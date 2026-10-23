import { useQuery } from '@tanstack/react-query';
import api from '@/lib/api';
import { parseEvent } from '@/lib/notificationStream';
import { useAuthStore } from '@/store/authStore';
import type {
  AiStatusResponse,
  CitationResponse,
  CloResponse,
  IngestionStatusResponse,
  PaperSettingsResponse,
  TopicResponse,
  UUID,
} from '@/types/api';

/** Whether the local model is up ("LLM") or the features run on classical NLP ("BASIC"). */
export function useAiStatus() {
  return useQuery({
    queryKey: ['ai', 'status'],
    queryFn: () => api.get<AiStatusResponse>('/ai/status').then((r) => r.data),
    staleTime: 30_000,
  });
}

/** Index state of an offering's documents; polls while any is still being read. */
export function useIngestion(pscId: UUID | undefined, enabled = true) {
  return useQuery({
    queryKey: ['offerings', pscId, 'ingestion'],
    queryFn: () =>
      api.get<IngestionStatusResponse[]>(`/offerings/${pscId}/ai/ingestion`).then((r) => r.data),
    enabled: !!pscId && enabled,
    refetchInterval: (q) =>
      (q.state.data ?? []).some((m) => m.status === 'PENDING' || m.status === 'PROCESSING')
        ? 4000
        : false,
  });
}

export function useTopics(pscId: UUID | undefined) {
  return useQuery({
    queryKey: ['offerings', pscId, 'topics'],
    queryFn: () => api.get<TopicResponse[]>(`/offerings/${pscId}/ai/topics`).then((r) => r.data),
    enabled: !!pscId,
  });
}

export function usePaperSettings(pscId: UUID | undefined) {
  return useQuery({
    queryKey: ['offerings', pscId, 'paper-settings'],
    queryFn: () =>
      api.get<PaperSettingsResponse>(`/offerings/${pscId}/paper-settings`).then((r) => r.data),
    enabled: !!pscId,
  });
}

/** A course's CLOs (shares its cache entry with the CLO mapping panels). */
export function useCourseClos(courseId: UUID | undefined) {
  return useQuery({
    queryKey: ['courses', courseId, 'clos'],
    queryFn: () => api.get<CloResponse[]>(`/courses/${courseId}/clos`).then((r) => r.data),
    enabled: !!courseId,
    staleTime: 5 * 60 * 1000,
  });
}

/** Downloads a file the API returns as an attachment (PDF, ZIP). */
export async function downloadBlob(path: string, filename: string): Promise<void> {
  const res = await api.get<Blob>(path, { responseType: 'blob', timeout: 120_000 });
  const url = URL.createObjectURL(res.data);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

export interface ChatDone {
  messageId: UUID;
  content: string;
  refused: boolean;
  mode: 'LLM' | 'EXTRACTIVE' | '';
  unverified: boolean;
}

export interface ChatStreamHandlers {
  onDelta: (text: string) => void;
  /** The model failed part-way; discard what was shown, a fallback answer follows. */
  onReset: () => void;
  onCitations: (citations: CitationResponse[]) => void;
  onDone: (done: ChatDone) => void;
  onError: (message: string) => void;
}

/**
 * Asks the study assistant and streams its answer.
 *
 * `fetch` rather than `EventSource`, which can neither POST nor send the
 * Authorization header. The URL is built from the axios base so the API prefix
 * is written in one place. A 401 is retried once after any api call has
 * refreshed the session through the axios interceptor.
 */
export async function streamChat(
  sessionId: UUID,
  content: string,
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
  retried = false,
): Promise<void> {
  const token = useAuthStore.getState().accessToken;
  const res = await fetch(`${api.defaults.baseURL}/me/ai/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
      // A raw fetch, not axios: nothing else would label the JSON body.
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ content }),
    signal,
  });
  if (res.status === 401 && !retried) {
    await api.get('/ai/status').catch(() => undefined);
    return streamChat(sessionId, content, handlers, signal, true);
  }
  if (!res.ok || !res.body) {
    let message = `The study assistant could not answer (HTTP ${res.status}).`;
    try {
      const problem = await res.json();
      message = problem.detail ?? problem.message ?? message;
    } catch {
      // not JSON
    }
    handlers.onError(message);
    return;
  }

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
      if (!parsed) continue;
      try {
        const data = JSON.parse(parsed.data);
        if (parsed.event === 'delta') handlers.onDelta(data.text ?? '');
        else if (parsed.event === 'reset') handlers.onReset();
        else if (parsed.event === 'citations') handlers.onCitations(data as CitationResponse[]);
        else if (parsed.event === 'done') handlers.onDone(data as ChatDone);
        else if (parsed.event === 'error') handlers.onError(data.message ?? 'Something went wrong.');
      } catch {
        console.warn('Ignoring malformed chat event', parsed);
      }
    }
  }
}
