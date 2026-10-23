import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, ChevronLeft, Clock, Info, XCircle } from 'lucide-react';
import api from '@/lib/api';
import { parseApiError } from '@/lib/apiError';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import QueryError from '@/components/ui/QueryError';
import BloomBadge from '@/components/ai/BloomBadge';
import CitationChip from '@/components/ai/CitationChip';
import { fmtMarks } from '@/components/ai/MarksMeter';
import { isWrittenType, type MockAttemptResponse, type MockQuestionResponse, type MockResult } from '@/types/api';

/** A practice quiz or paper: answering it, then its marks and feedback. */
export default function MockAttemptPage() {
  const { mockId } = useParams<{ mockId: string }>();
  const qc = useQueryClient();
  const mockQ = useQuery({
    queryKey: ['me', 'ai', 'mock', mockId],
    queryFn: () => api.get<MockAttemptResponse>(`/me/ai/mocks/${mockId}`).then((r) => r.data),
    refetchInterval: (q) => (q.state.data?.status === 'GENERATING' ? 2500 : false),
  });
  const mock = mockQ.data;

  if (mockQ.isLoading) return <div className="flex justify-center py-16"><Spinner size="lg" /></div>;
  if (mockQ.error || !mock) return <QueryError error={mockQ.error} onRetry={() => mockQ.refetch()} />;

  const back = (
    <Link to={`/study-assistant?course=${mock.pscId}`} className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-800">
      <ChevronLeft size={16} /> Study Assistant
    </Link>
  );

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      {back}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">
          {mock.kind === 'QUIZ' ? 'Practice quiz' : 'Mock paper'}
        </h1>
        <p className="mt-1 flex items-center gap-1.5 text-sm text-gray-500">
          <Info size={14} /> Practice only — this is never counted toward your grade.
        </p>
      </div>

      {mock.status === 'GENERATING' && (
        <div className="flex flex-col items-center rounded-xl border border-gray-200 bg-white py-16 text-center">
          <Spinner size="lg" />
          <p className="mt-4 text-sm text-gray-600">Writing questions from your course material…</p>
          <p className="mt-1 text-xs text-gray-400">This can take a minute.</p>
        </div>
      )}
      {mock.status === 'FAILED' && (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {mock.error ?? 'The practice paper could not be generated.'}
        </div>
      )}
      {mock.config.notes && mock.config.notes.length > 0 && (
        <ul className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-800">
          {mock.config.notes.map((n) => <li key={n}>{n}</li>)}
        </ul>
      )}
      {mock.status === 'READY' && (
        <Attempt mock={mock} onGraded={(m) => qc.setQueryData(['me', 'ai', 'mock', mockId], m)} />
      )}
      {mock.status === 'GRADED' && <Results mock={mock} />}
    </div>
  );
}

function Attempt({ mock, onGraded }: { mock: MockAttemptResponse; onGraded: (m: MockAttemptResponse) => void }) {
  const qc = useQueryClient();
  const [answers, setAnswers] = useState<Record<string, string[]>>(mock.answers ?? {});
  const questions = mock.questions ?? [];
  const duration = mock.kind === 'PAPER' ? mock.config.durationMinutes : undefined;
  const [deadline] = useState(() => (duration ? Date.now() + duration * 60_000 : null));
  const [left, setLeft] = useState<number | null>(duration ? duration * 60 : null);

  const save = useMutation({
    mutationFn: (a: Record<string, string[]>) => api.put(`/me/ai/mocks/${mock.id}/answers`, { answers: a }),
  });
  const submit = useMutation({
    mutationFn: (a: Record<string, string[]>) =>
      api.post<MockAttemptResponse>(`/me/ai/mocks/${mock.id}/submit`, { answers: a }).then((r) => r.data),
    onSuccess: (m) => {
      onGraded(m);
      qc.invalidateQueries({ queryKey: ['me', 'ai', 'mocks'] });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    },
  });

  // Save a few seconds after the last change, so a reload keeps the answers.
  const firstRender = useRef(true);
  useEffect(() => {
    if (firstRender.current) { firstRender.current = false; return; }
    const t = setTimeout(() => save.mutate(answers), 1500);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [answers]);

  const answersRef = useRef(answers);
  answersRef.current = answers;
  useEffect(() => {
    if (!deadline) return;
    const id = setInterval(() => {
      const s = Math.max(0, Math.round((deadline - Date.now()) / 1000));
      setLeft(s);
      if (s === 0) {
        clearInterval(id);
        submit.mutate(answersRef.current);
      }
    }, 1000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deadline]);

  const answered = questions.filter((q) => (answers[q.id] ?? []).some((a) => a.trim())).length;

  return (
    <div className="space-y-4">
      <div className="sticky top-0 z-10 flex items-center justify-between rounded-lg border border-gray-200 bg-white/95 px-4 py-2 backdrop-blur">
        <span className="text-sm text-gray-600">
          {answered}/{questions.length} answered · {fmtMarks(mock.totalMarks)} marks
        </span>
        {left !== null && (
          <span className={`flex items-center gap-1 text-sm font-semibold ${left < 120 ? 'text-red-600' : 'text-amber-700'}`}>
            <Clock size={14} /> {Math.floor(left / 60)}:{String(left % 60).padStart(2, '0')}
          </span>
        )}
      </div>

      {questions.map((q, i) => (
        <div key={q.id} className="rounded-xl border border-gray-200 bg-white p-4">
          <QuestionHeader q={q} n={i + 1} />
          {isWrittenType(q.type) ? (
            <textarea
              value={(answers[q.id] ?? [''])[0]}
              onChange={(e) => setAnswers((a) => ({ ...a, [q.id]: [e.target.value] }))}
              rows={q.type === 'LONG_ANSWER' ? 8 : 3}
              placeholder="Write your answer…"
              aria-label={`Answer to question ${i + 1}`}
              className="mt-3 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
            />
          ) : (
            <div className="mt-3 space-y-2">
              {q.options.map((o, oi) => {
                const sel = (answers[q.id] ?? []).includes(o.id);
                return (
                  <button
                    key={o.id}
                    type="button"
                    onClick={() =>
                      setAnswers((a) => {
                        const cur = a[q.id] ?? [];
                        if (q.type === 'MCQ') return { ...a, [q.id]: [o.id] };
                        return { ...a, [q.id]: sel ? cur.filter((x) => x !== o.id) : [...cur, o.id] };
                      })
                    }
                    className={`flex w-full items-center gap-3 rounded-lg border-2 px-3 py-2 text-left text-sm ${
                      sel ? 'border-primary-500 bg-primary-50' : 'border-gray-200 hover:border-primary-300'
                    }`}
                  >
                    <span className={`flex h-5 w-5 items-center justify-center rounded-full border-2 text-xs font-bold ${
                      sel ? 'border-primary-500 bg-primary-500 text-white' : 'border-gray-300 text-gray-400'
                    }`}>
                      {String.fromCharCode(65 + oi)}
                    </span>
                    {o.text}
                  </button>
                );
              })}
              {q.type === 'MSQ' && <p className="text-xs text-gray-400">Select all that apply</p>}
            </div>
          )}
        </div>
      ))}

      {submit.error != null && <p className="text-sm text-red-600">{parseApiError(submit.error)}</p>}
      <div className="flex justify-end">
        <Button
          loading={submit.isPending}
          onClick={() => confirm(`Submit for marking? You've answered ${answered}/${questions.length}.`) && submit.mutate(answers)}
        >
          Submit for marking
        </Button>
      </div>
    </div>
  );
}

function Results({ mock }: { mock: MockAttemptResponse }) {
  const questions = mock.questions ?? [];
  const pct = mock.totalMarks > 0 ? Math.round(((mock.score ?? 0) / mock.totalMarks) * 100) : 0;
  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-gray-200 bg-white p-5 text-center">
        <p className="text-sm text-gray-500">Your score</p>
        <p className="text-3xl font-bold text-gray-900">
          {fmtMarks(mock.score ?? 0)} <span className="text-lg font-normal text-gray-400">/ {fmtMarks(mock.totalMarks)}</span>
        </p>
        <p className="text-sm text-gray-500">{pct}%</p>
      </div>
      {questions.map((q, i) => (
        <ResultCard key={q.id} q={q} n={i + 1} answer={mock.answers?.[q.id] ?? []} result={mock.results?.[q.id]} />
      ))}
    </div>
  );
}

function ResultCard({ q, n, answer, result }: { q: MockQuestionResponse; n: number; answer: string[]; result?: MockResult }) {
  const full = result && result.score >= result.maxMarks;
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4">
      <QuestionHeader q={q} n={n} score={result ? `${fmtMarks(result.score)}/${fmtMarks(result.maxMarks)}` : undefined} good={full} />
      {isWrittenType(q.type) ? (
        <div className="mt-3 space-y-2 text-sm">
          <div className="rounded-lg bg-gray-50 p-3">
            <p className="mb-1 text-xs font-semibold text-gray-500">Your answer</p>
            <p className="whitespace-pre-wrap text-gray-800">{answer[0] || <em className="text-gray-400">No answer</em>}</p>
          </div>
          {q.modelAnswer && (
            <div className="rounded-lg bg-green-50 p-3">
              <p className="mb-1 text-xs font-semibold text-green-700">Model answer</p>
              <p className="text-gray-800">{q.modelAnswer}</p>
            </div>
          )}
        </div>
      ) : (
        <div className="mt-3 space-y-1.5">
          {q.options.map((o, oi) => {
            const correct = q.correctAnswer?.includes(o.id);
            const chosen = answer.includes(o.id);
            return (
              <div
                key={o.id}
                className={`flex items-center gap-2 rounded-lg border px-3 py-1.5 text-sm ${
                  correct ? 'border-green-300 bg-green-50' : chosen ? 'border-red-300 bg-red-50' : 'border-gray-100'
                }`}
              >
                <span className="w-4 text-xs font-bold text-gray-500">{String.fromCharCode(65 + oi)}</span>
                <span className="flex-1">{o.text}</span>
                {correct && <CheckCircle2 size={14} className="text-green-600" />}
                {chosen && !correct && <XCircle size={14} className="text-red-500" />}
              </div>
            );
          })}
        </div>
      )}
      {result?.feedback && <p className="mt-2 text-sm text-gray-700">{result.feedback}</p>}
      {q.explanation && !isWrittenType(q.type) && <p className="mt-1 text-xs text-gray-500">{q.explanation}</p>}
      {q.sources.length > 0 && (
        <div className="mt-2 flex flex-wrap items-center gap-1.5">
          <span className="text-xs text-gray-400">Revise:</span>
          {q.sources.map((s) => <CitationChip key={s.chunkId} citation={s} />)}
        </div>
      )}
    </div>
  );
}

function QuestionHeader({ q, n, score, good }: { q: MockQuestionResponse; n: number; score?: string; good?: boolean }) {
  return (
    <div className="flex items-start gap-2">
      <span className="mt-0.5 flex-shrink-0 rounded-full bg-primary-100 px-2 py-0.5 text-xs font-bold text-primary-700">Q{n}</span>
      <p className="flex-1 text-sm font-medium text-gray-900">{q.questionText}</p>
      <div className="flex flex-shrink-0 items-center gap-2">
        <BloomBadge level={q.bloomLevel} />
        {score ? (
          <span className={`text-xs font-semibold ${good ? 'text-green-700' : 'text-gray-700'}`}>{score}</span>
        ) : (
          <span className="text-xs text-gray-400">{fmtMarks(q.marks)} mark{q.marks === 1 ? '' : 's'}</span>
        )}
      </div>
    </div>
  );
}
