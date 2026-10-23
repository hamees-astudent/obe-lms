import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle,
  BookOpenCheck,
  ClipboardList,
  Info,
  MessageSquarePlus,
  Send,
  Sparkles,
  Square,
  Trash2,
} from 'lucide-react';
import api from '@/lib/api';
import { streamChat, useTopics } from '@/lib/ai';
import { formatDateTime } from '@/lib/datetime';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import QueryError from '@/components/ui/QueryError';
import AiModeBanner from '@/components/ai/AiModeBanner';
import AnswerText from '@/components/ai/AnswerText';
import CitationChip from '@/components/ai/CitationChip';
import MockConfigModal from '@/components/ai/MockConfigModal';
import type {
  ChatMessageResponse,
  ChatSessionResponse,
  MockAttemptResponse,
  StudyCourseResponse,
  UUID,
} from '@/types/api';

/** A message on screen: saved ones, plus the one being streamed. */
type ShownMessage = ChatMessageResponse & { streaming?: boolean };

export default function StudyAssistantPage() {
  const qc = useQueryClient();
  const [params, setParams] = useSearchParams();
  const pscId = params.get('course') ?? undefined;
  const sessionId = params.get('session') ?? undefined;
  const [showMock, setShowMock] = useState(false);

  const coursesQ = useQuery({
    queryKey: ['me', 'ai', 'courses'],
    queryFn: () => api.get<StudyCourseResponse[]>('/me/ai/courses').then((r) => r.data),
  });
  const courses = coursesQ.data ?? [];
  const course = courses.find((c) => c.pscId === pscId);

  // Open the first course by default.
  useEffect(() => {
    if (!pscId && courses.length > 0) setParams({ course: courses[0].pscId }, { replace: true });
  }, [pscId, courses, setParams]);

  const sessionsQ = useQuery({
    queryKey: ['me', 'ai', 'sessions', pscId],
    queryFn: () =>
      api.get<ChatSessionResponse[]>('/me/ai/chat/sessions', { params: { pscId } }).then((r) => r.data),
    enabled: !!pscId,
  });

  const mocksQ = useQuery({
    queryKey: ['me', 'ai', 'mocks', pscId],
    queryFn: () => api.get<MockAttemptResponse[]>('/me/ai/mocks', { params: { pscId } }).then((r) => r.data),
    enabled: !!pscId,
  });

  const newSession = useMutation({
    mutationFn: () =>
      api.post<ChatSessionResponse>('/me/ai/chat/sessions', { pscId }).then((r) => r.data),
    onSuccess: (s) => {
      qc.invalidateQueries({ queryKey: ['me', 'ai', 'sessions', pscId] });
      setParams({ course: pscId!, session: s.id });
    },
  });

  const deleteSession = useMutation({
    mutationFn: (id: UUID) => api.delete(`/me/ai/chat/sessions/${id}`),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ['me', 'ai', 'sessions', pscId] });
      if (id === sessionId) setParams({ course: pscId! });
    },
  });

  if (coursesQ.isLoading) {
    return <div className="flex justify-center py-16"><Spinner size="lg" /></div>;
  }
  if (coursesQ.error) return <QueryError error={coursesQ.error} onRetry={() => coursesQ.refetch()} />;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold text-gray-900">
            <Sparkles className="text-primary-600" size={22} />
            Study Assistant
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            Ask about your courses. Answers come only from the material your teachers uploaded, with
            the slides and pages they came from.
          </p>
        </div>
        {courses.length > 0 && (
          <select
            value={pscId ?? ''}
            onChange={(e) => setParams({ course: e.target.value })}
            aria-label="Course"
            className="h-9 rounded-lg border border-gray-300 px-3 text-sm"
          >
            {courses.map((c) => (
              <option key={c.pscId} value={c.pscId}>
                {c.courseCode} — {c.courseName}
              </option>
            ))}
          </select>
        )}
      </div>

      <AiModeBanner what="the assistant" />

      {courses.length === 0 ? (
        <div className="rounded-xl border border-dashed border-gray-200 py-16 text-center text-sm text-gray-500">
          You are not enrolled in any course yet.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-[17rem_1fr]">
          {/* Sidebar: conversations and practice */}
          <aside className="space-y-4">
            <div className="rounded-xl border border-gray-200 bg-white p-3">
              <div className="mb-2 flex items-center justify-between">
                <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Conversations</p>
                <Button size="sm" variant="ghost" loading={newSession.isPending} onClick={() => newSession.mutate()}>
                  <MessageSquarePlus size={14} /> New
                </Button>
              </div>
              {(sessionsQ.data ?? []).length === 0 ? (
                <p className="px-1 py-2 text-xs text-gray-400">No conversations yet.</p>
              ) : (
                <ul className="space-y-1">
                  {(sessionsQ.data ?? []).map((s) => (
                    <li key={s.id} className="group flex items-center">
                      <button
                        onClick={() => setParams({ course: pscId!, session: s.id })}
                        className={`min-w-0 flex-1 truncate rounded-lg px-2 py-1.5 text-left text-sm ${
                          s.id === sessionId ? 'bg-primary-50 text-primary-700' : 'text-gray-700 hover:bg-gray-50'
                        }`}
                      >
                        {s.title}
                      </button>
                      <button
                        aria-label="Delete conversation"
                        onClick={() => confirm('Delete this conversation?') && deleteSession.mutate(s.id)}
                        className="p-1 text-gray-300 opacity-0 hover:text-red-500 group-hover:opacity-100"
                      >
                        <Trash2 size={13} />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="rounded-xl border border-gray-200 bg-white p-3">
              <div className="mb-2 flex items-center justify-between">
                <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Practice</p>
                <Button size="sm" variant="ghost" onClick={() => setShowMock(true)}>
                  <ClipboardList size={14} /> New
                </Button>
              </div>
              {(mocksQ.data ?? []).length === 0 ? (
                <p className="px-1 py-2 text-xs text-gray-400">Generate a practice quiz or mock paper.</p>
              ) : (
                <ul className="space-y-1">
                  {(mocksQ.data ?? []).slice(0, 8).map((m) => (
                    <li key={m.id}>
                      <Link
                        to={`/study-assistant/mocks/${m.id}`}
                        className="flex items-center justify-between rounded-lg px-2 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
                      >
                        <span>
                          {m.kind === 'QUIZ' ? 'Quiz' : 'Paper'}
                          <span className="ml-1 text-xs text-gray-400">{formatDateTime(m.createdAt)}</span>
                        </span>
                        {m.status === 'GRADED' ? (
                          <span className="text-xs font-semibold text-green-700">
                            {m.score}/{m.totalMarks}
                          </span>
                        ) : (
                          <Badge variant={m.status === 'FAILED' ? 'danger' : 'info'}>
                            {m.status === 'GENERATING' ? 'Preparing' : m.status === 'READY' ? 'Open' : 'Failed'}
                          </Badge>
                        )}
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {course && (
              <p className="px-1 text-xs text-gray-400">
                {course.readyMaterials} indexed material{course.readyMaterials === 1 ? '' : 's'} in {course.courseCode}.
              </p>
            )}
          </aside>

          {/* Conversation */}
          <section className="flex min-h-[32rem] flex-col rounded-xl border border-gray-200 bg-white">
            {sessionId && pscId ? (
              <Conversation key={sessionId} sessionId={sessionId} pscId={pscId} />
            ) : (
              <EmptyConversation
                course={course}
                starting={newSession.isPending}
                onStart={() => newSession.mutate()}
              />
            )}
          </section>
        </div>
      )}

      {showMock && pscId && course && (
        <MockConfigModal
          pscId={pscId}
          courseLabel={course.courseCode}
          onClose={() => setShowMock(false)}
        />
      )}
    </div>
  );
}

function EmptyConversation({
  course,
  starting,
  onStart,
}: {
  course?: StudyCourseResponse;
  starting: boolean;
  onStart: () => void;
}) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center p-8 text-center">
      <BookOpenCheck size={40} className="mb-3 text-gray-300" />
      <p className="text-sm text-gray-600">
        {course && course.readyMaterials === 0
          ? `No material has been indexed for ${course.courseCode} yet, so there is nothing to answer from.`
          : 'Start a conversation to ask about any topic in the course material.'}
      </p>
      <Button className="mt-4" loading={starting} onClick={onStart}>
        <MessageSquarePlus size={15} /> New conversation
      </Button>
    </div>
  );
}

function Conversation({ sessionId, pscId }: { sessionId: UUID; pscId: UUID }) {
  const qc = useQueryClient();
  const sessionQ = useQuery({
    queryKey: ['me', 'ai', 'session', sessionId],
    queryFn: () => api.get<ChatSessionResponse>(`/me/ai/chat/sessions/${sessionId}`).then((r) => r.data),
  });
  const topicsQ = useTopics(pscId);
  const [messages, setMessages] = useState<ShownMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (sessionQ.data?.messages) setMessages(sessionQ.data.messages);
  }, [sessionQ.data]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages]);

  useEffect(() => () => abortRef.current?.abort(), []);

  const suggestions = useMemo(
    () => (topicsQ.data ?? []).slice(0, 6).map((t) => `Explain ${t.name}`),
    [topicsQ.data],
  );

  async function send(text: string) {
    const content = text.trim();
    if (!content || busy) return;
    setDraft('');
    setError(null);
    setBusy(true);
    const now = new Date().toISOString();
    const pendingId = `pending-${Date.now()}`;
    setMessages((m) => [
      ...m,
      { id: `user-${Date.now()}`, role: 'USER', content, citations: [], refused: false, unverified: false, createdAt: now },
      { id: pendingId, role: 'ASSISTANT', content: '', citations: [], refused: false, unverified: false, createdAt: now, streaming: true },
    ]);
    const update = (fn: (m: ShownMessage) => ShownMessage) =>
      setMessages((all) => all.map((m) => (m.id === pendingId ? fn(m) : m)));

    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await streamChat(sessionId, content, {
        onDelta: (t) => update((m) => ({ ...m, content: m.content + t })),
        onReset: () => update((m) => ({ ...m, content: '' })),
        onCitations: (c) => update((m) => ({ ...m, citations: c })),
        onDone: (d) =>
          update((m) => ({
            ...m,
            id: d.messageId,
            content: d.content,
            refused: d.refused,
            unverified: d.unverified,
            mode: d.mode || undefined,
            streaming: false,
          })),
        onError: (msg) => {
          setError(msg);
          setMessages((all) => all.filter((m) => m.id !== pendingId));
        },
      }, controller.signal);
    } catch {
      if (!controller.signal.aborted) setError('The connection to the assistant dropped. Please try again.');
    } finally {
      setBusy(false);
      update((m) => ({ ...m, streaming: false }));
      qc.invalidateQueries({ queryKey: ['me', 'ai', 'sessions', pscId] });
    }
  }

  if (sessionQ.isLoading) return <div className="flex flex-1 items-center justify-center"><Spinner /></div>;
  if (sessionQ.error) return <div className="p-4"><QueryError error={sessionQ.error} /></div>;

  return (
    <>
      <div className="flex-1 space-y-4 overflow-y-auto p-4" aria-live="polite">
        {messages.length === 0 && (
          <div className="py-8 text-center">
            <p className="text-sm text-gray-500">Ask a question about this course, for example:</p>
            <div className="mt-3 flex flex-wrap justify-center gap-2">
              {suggestions.map((s) => (
                <button
                  key={s}
                  onClick={() => send(s)}
                  className="rounded-full border border-gray-200 px-3 py-1 text-xs text-gray-700 hover:border-primary-300 hover:bg-primary-50"
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
        {messages.map((m) => (
          <MessageBubble key={m.id} message={m} />
        ))}
        <div ref={endRef} />
      </div>

      {error && (
        <p className="mx-4 mb-2 flex items-center gap-1.5 rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700">
          <AlertTriangle size={13} /> {error}
        </p>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault();
          send(draft);
        }}
        className="flex items-end gap-2 border-t border-gray-100 p-3"
      >
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              send(draft);
            }
          }}
          rows={2}
          maxLength={2000}
          placeholder="Ask about a topic, a definition, or how two ideas compare…"
          aria-label="Your question"
          className="min-h-[2.5rem] flex-1 resize-none rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
        />
        {busy ? (
          <Button type="button" variant="secondary" onClick={() => abortRef.current?.abort()}>
            <Square size={14} /> Stop
          </Button>
        ) : (
          <Button type="submit" disabled={!draft.trim()}>
            <Send size={14} /> Ask
          </Button>
        )}
      </form>
    </>
  );
}

function MessageBubble({ message }: { message: ShownMessage }) {
  if (message.role === 'USER') {
    return (
      <div className="flex justify-end">
        <div className="max-w-[80%] whitespace-pre-wrap rounded-2xl rounded-br-sm bg-primary-600 px-4 py-2 text-sm text-white">
          {message.content}
        </div>
      </div>
    );
  }
  return (
    <div className="flex justify-start">
      <div
        className={`max-w-[85%] rounded-2xl rounded-bl-sm px-4 py-3 ${
          message.refused ? 'border border-gray-200 bg-gray-50' : 'bg-gray-100'
        }`}
      >
        {message.refused && (
          <p className="mb-1 flex items-center gap-1 text-xs font-medium text-gray-500">
            <Info size={12} /> Not covered by the course material
          </p>
        )}
        {message.content ? (
          <AnswerText text={message.content} citations={message.citations} />
        ) : (
          <span className="inline-flex items-center gap-2 text-sm text-gray-500">
            <Spinner size="sm" /> Searching the course material…
          </span>
        )}
        {message.citations.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1.5 border-t border-gray-200 pt-2">
            {message.citations.map((c, i) => (
              <CitationChip key={c.chunkId + i} citation={c} index={i + 1} />
            ))}
          </div>
        )}
        {!message.streaming && message.unverified && (
          <p className="mt-2 flex items-center gap-1 text-xs text-amber-700">
            <AlertTriangle size={12} /> Part of this answer could not be matched to the material — check
            the sources.
          </p>
        )}
        {!message.streaming && message.mode === 'EXTRACTIVE' && (
          <p className="mt-1 text-[11px] text-gray-400">Quoted directly from the material (basic mode).</p>
        )}
      </div>
    </div>
  );
}
