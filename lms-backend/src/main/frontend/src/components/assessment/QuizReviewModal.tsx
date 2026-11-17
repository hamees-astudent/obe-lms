import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Sparkles } from 'lucide-react';
import api from '@/lib/api';
import { parseApiError } from '@/lib/apiError';
import { toast } from '@/components/ui/Toast';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import { fmtMarks } from '@/components/ai/MarksMeter';
import {
  isWrittenType,
  type GradeQuizSubmissionBody,
  type GradeSuggestionResponse,
  type QuizQuestionResponse,
  type QuizResponse,
  type QuizSubmissionResponse,
} from '@/types/api';
import RichText from '@/components/ai/RichText';

/**
 * Marks the written answers of one attempt. Each answer comes with a suggested
 * mark — keyword coverage against the rubric, and the local model's reading
 * when it is running — which the teacher can take or override. Nothing counts
 * until the teacher saves.
 */
export default function QuizReviewModal({
  quiz,
  submission,
  onClose,
}: {
  quiz: QuizResponse;
  submission: QuizSubmissionResponse;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const questionsQ = useQuery({
    queryKey: ['quizzes', quiz.id, 'questions'],
    queryFn: () => api.get<QuizQuestionResponse[]>(`/quizzes/${quiz.id}/questions`).then((r) => r.data),
  });
  const suggestionsQ = useQuery({
    queryKey: ['quiz-submissions', submission.id, 'grade-suggestions'],
    queryFn: () =>
      api
        .post<GradeSuggestionResponse[]>(`/ai/quiz-submissions/${submission.id}/grade-suggestions`, undefined, { timeout: 180_000 })
        .then((r) => r.data),
    staleTime: Infinity,
    retry: false,
  });
  const questions = (questionsQ.data ?? []).slice().sort((a, b) => a.orderIndex - b.orderIndex);
  const written = questions.filter((q) => isWrittenType(q.type));
  const suggestion = (id: string) => suggestionsQ.data?.find((s) => s.questionId === id);

  const [grades, setGrades] = useState<Record<string, { score: string; feedback: string }>>({});
  useEffect(() => {
    // Start from saved marks when re-grading.
    const initial: Record<string, { score: string; feedback: string }> = {};
    for (const q of written) {
      const saved = submission.questionScores?.[q.id];
      if (saved?.gradedBy === 'TEACHER') {
        initial[q.id] = { score: String(saved.score), feedback: saved.feedback ?? '' };
      }
    }
    setGrades((g) => ({ ...initial, ...g }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [questionsQ.data]);

  const save = useMutation({
    mutationFn: (body: GradeQuizSubmissionBody) =>
      api.put<QuizSubmissionResponse>(`/quiz-submissions/${submission.id}/grades`, body).then((r) => r.data),
    onSuccess: () => {
      toast.success('Marks saved.');
      qc.invalidateQueries({ queryKey: ['quizzes', quiz.id, 'submissions'] });
      onClose();
    },
  });

  const objective = questions
    .filter((q) => !isWrittenType(q.type))
    .reduce((s, q) => s + Number(submission.questionScores?.[q.id]?.score ?? 0), 0);
  const writtenTotal = written.reduce((s, q) => s + (Number(grades[q.id]?.score) || 0), 0);
  const complete = written.every((q) => grades[q.id]?.score !== undefined && grades[q.id].score !== '');

  function useAll() {
    const next = { ...grades };
    for (const q of written) {
      const s = suggestion(q.id);
      if (s && !next[q.id]?.score) next[q.id] = { score: String(s.score), feedback: s.feedback };
    }
    setGrades(next);
  }

  return (
    <Modal open onClose={onClose} title={`Mark answers — ${submission.studentName ?? 'student'}`} maxWidth="max-w-3xl">
      {questionsQ.isLoading ? (
        <div className="flex justify-center py-8"><Spinner /></div>
      ) : (
        <div className="space-y-4">
          <div className="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2 text-sm">
            <span>
              Choice questions (auto-marked): <strong>{fmtMarks(objective)}</strong> · Written:{' '}
              <strong>{fmtMarks(writtenTotal)}</strong> · Total{' '}
              <strong>{fmtMarks(objective + writtenTotal)}</strong> / {fmtMarks(quiz.totalMarks)}
            </span>
            <Button size="sm" variant="secondary" onClick={useAll} disabled={!suggestionsQ.data}>
              <Sparkles size={13} /> Use all suggestions
            </Button>
          </div>
          {suggestionsQ.isLoading && (
            <p className="flex items-center gap-2 text-xs text-gray-500"><Spinner size="sm" /> Preparing suggested marks…</p>
          )}

          <div className="max-h-[58vh] space-y-4 overflow-y-auto pr-1">
            {written.map((q) => {
              const s = suggestion(q.id);
              const answer = submission.answers?.[q.id]?.[0] ?? '';
              const g = grades[q.id] ?? { score: '', feedback: '' };
              return (
                <div key={q.id} className="rounded-lg border border-gray-200 p-3">
                  <div className="flex items-start gap-1 text-sm font-medium text-gray-900">
                    <span>Q{q.orderIndex + 1}.</span>
                    <RichText text={q.questionText} compact className="flex-1" />
                    <span className="text-xs text-gray-400">({fmtMarks(q.marks)} marks)</span>
                  </div>
                  <div className="mt-2 grid gap-2 md:grid-cols-2">
                    <div className="rounded bg-gray-50 p-2 text-sm">
                      <p className="mb-1 text-xs font-semibold text-gray-500">Student's answer</p>
                      <p className="whitespace-pre-wrap">{answer || <em className="text-gray-400">No answer</em>}</p>
                    </div>
                    <div className="rounded bg-green-50 p-2 text-sm">
                      <p className="mb-1 text-xs font-semibold text-green-700">Model answer</p>
                      {q.modelAnswer && <RichText text={q.modelAnswer} compact />}
                      {(q.rubric ?? []).length > 0 && (
                        <ul className="mt-1 list-disc pl-4 text-xs text-gray-600">
                          {q.rubric!.map((r, i) => (
                            <li key={i}>{r.criterion} — {fmtMarks(r.marks)}{r.keywords?.length ? ` (${r.keywords.join(', ')})` : ''}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                  </div>
                  {s && (
                    <div className="mt-2 rounded border border-primary-100 bg-primary-50/50 p-2 text-xs text-gray-700">
                      <p>
                        <Sparkles size={11} className="mr-1 inline text-primary-600" />
                        Suggested <strong>{fmtMarks(s.score)}</strong>/{fmtMarks(s.maxMarks)}
                        {s.method === 'LLM' && s.classicalScore !== undefined && (
                          <span className="text-gray-500"> (keyword check: {fmtMarks(s.classicalScore)})</span>
                        )}
                        {' '}— {s.feedback}
                      </p>
                      {s.disagreement && (
                        <p className="mt-1 flex items-center gap-1 text-amber-700">
                          <AlertTriangle size={11} /> The two checks disagree a lot — read this answer carefully.
                        </p>
                      )}
                    </div>
                  )}
                  <div className="mt-2 flex flex-wrap items-center gap-2">
                    <label className="text-xs text-gray-600">
                      Marks{' '}
                      <input
                        type="number"
                        min={0}
                        max={q.marks}
                        step={0.5}
                        value={g.score}
                        onChange={(e) => setGrades((all) => ({ ...all, [q.id]: { ...g, score: e.target.value } }))}
                        className="ml-1 h-8 w-20 rounded border border-gray-300 px-2 text-sm"
                      />
                    </label>
                    <input
                      value={g.feedback}
                      onChange={(e) => setGrades((all) => ({ ...all, [q.id]: { ...g, feedback: e.target.value } }))}
                      placeholder="Feedback for the student (optional)"
                      className="h-8 min-w-[12rem] flex-1 rounded border border-gray-300 px-2 text-sm"
                    />
                    {s && (
                      <Button size="sm" variant="ghost" onClick={() => setGrades((all) => ({ ...all, [q.id]: { score: String(s.score), feedback: s.feedback } }))}>
                        Use suggestion
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {save.error != null && <p className="text-sm text-red-600">{parseApiError(save.error)}</p>}
          <div className="flex justify-end gap-2 border-t border-gray-100 pt-3">
            <Button variant="secondary" onClick={onClose}>Cancel</Button>
            <Button
              disabled={!complete}
              loading={save.isPending}
              onClick={() =>
                save.mutate({
                  grades: Object.fromEntries(
                    written.map((q) => [q.id, { score: Number(grades[q.id].score), feedback: grades[q.id].feedback || undefined }]),
                  ),
                })
              }
            >
              Save marks
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}
