import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import { toast } from '@/components/ui/Toast';
import type { NotificationResponse } from '@/types/api';

const FIRST_RETRY_MS = 2_000;
const MAX_RETRY_MS = 30_000;

/** Where clicking a notification toast takes the user. */
function pathFor(n: NotificationResponse): string {
  if (n.eventType === 'ATTENDANCE_ALERT') return '/attendance';
  switch (n.referenceType) {
    case 'QUIZ':
    case 'ASSIGNMENT':
      return '/assessment';
    case 'MATERIAL':
    case 'ENROLLMENT':
      return '/courses';
    case 'SEMESTER':
      return '/transcripts';
    default:
      return '/notifications';
  }
}

/**
 * Marks what a notification changed as stale, so the page on screen shows it
 * without a reload — a student looking at their course sees the new quiz appear.
 */
function refreshFor(qc: QueryClient, n: NotificationResponse) {
  qc.invalidateQueries({ queryKey: ['notifications'] });
  const offeringList = (list: string) =>
    qc.invalidateQueries({ predicate: (q) => q.queryKey[0] === 'offerings' && q.queryKey[2] === list });
  switch (n.referenceType) {
    case 'QUIZ':
      offeringList('quizzes');
      qc.invalidateQueries({ queryKey: ['me', 'quizzes'] });
      break;
    case 'ASSIGNMENT':
      offeringList('assignments');
      qc.invalidateQueries({ queryKey: ['me', 'assignments'] });
      break;
    case 'MATERIAL':
      offeringList('materials');
      break;
    case 'ENROLLMENT':
      qc.invalidateQueries({ queryKey: ['me', 'enrollments'] });
      qc.invalidateQueries({ queryKey: ['me', 'timetable'] });
      break;
  }
  if (n.eventType === 'ATTENDANCE_ALERT') qc.invalidateQueries({ queryKey: ['me', 'attendance'] });
}

/** One server-sent event, from the lines between two blank lines. */
function parseEvent(block: string): { event: string; data: string } | null {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue; // comment, e.g. the keep-alive
    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '');
    if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
  }
  return data.length ? { event, data: data.join('\n') } : null;
}

/**
 * Keeps a live connection to `/api/notifications/stream` while signed in and
 * shows each notification as a toast the moment it is created.
 *
 * Uses `fetch` rather than `EventSource`, which cannot send the Authorization
 * header — the alternative, the token in the URL, would leave it in server and
 * proxy logs. Reconnects with backoff when the stream drops, and with the new
 * token after a refresh (the effect re-runs when the token changes).
 */
export function useNotificationStream() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const qc = useQueryClient();
  const navigate = useNavigate();
  // Held in a ref so navigating between pages never reconnects the stream.
  const navigateRef = useRef(navigate);
  navigateRef.current = navigate;

  useEffect(() => {
    if (!accessToken) return;
    const controller = new AbortController();
    let retryMs = FIRST_RETRY_MS;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const show = (n: NotificationResponse) => {
      refreshFor(qc, n);
      toast.notify({
        title: n.title,
        message: n.body ?? '',
        onClick: () => {
          api.put(`/notifications/${n.id}/read`)
            .then(() => qc.invalidateQueries({ queryKey: ['notifications'] }))
            .catch(() => undefined);
          navigateRef.current(pathFor(n));
        },
      });
    };

    const scheduleReconnect = () => {
      if (controller.signal.aborted) return;
      timer = setTimeout(connect, retryMs);
      retryMs = Math.min(retryMs * 2, MAX_RETRY_MS);
    };

    async function connect() {
      try {
        const res = await fetch('/api/notifications/stream', {
          headers: { Authorization: `Bearer ${accessToken}`, Accept: 'text/event-stream' },
          cache: 'no-store',
          signal: controller.signal,
        });
        if (res.status === 401) {
          // Expired token. Any api call refreshes the session through the axios
          // interceptor (or signs out if it cannot); the new token re-runs this effect.
          await api.get('/notifications/unread-count').catch(() => undefined);
          scheduleReconnect();
          return;
        }
        if (!res.ok || !res.body) throw new Error(`Notification stream: HTTP ${res.status}`);

        retryMs = FIRST_RETRY_MS;
        // Anything that arrived while disconnected is in the feed, not the stream.
        qc.invalidateQueries({ queryKey: ['notifications'] });

        const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
        let buffer = '';
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += value;
          let end: RegExpMatchArray | null;
          while ((end = buffer.match(/\r?\n\r?\n/)) && end.index !== undefined) {
            const parsed = parseEvent(buffer.slice(0, end.index));
            buffer = buffer.slice(end.index + end[0].length);
            if (parsed?.event === 'notification') {
              try {
                show(JSON.parse(parsed.data) as NotificationResponse);
              } catch {
                console.warn('Ignoring malformed notification event', parsed.data);
              }
            }
          }
        }
      } catch (error) {
        if (controller.signal.aborted) return;
        console.debug('Notification stream dropped; reconnecting.', error);
      }
      scheduleReconnect();
    }

    connect();
    return () => {
      controller.abort();
      if (timer) clearTimeout(timer);
    };
  }, [accessToken, qc]);
}
