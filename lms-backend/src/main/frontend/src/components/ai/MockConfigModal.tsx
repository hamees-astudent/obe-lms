import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import api from '@/lib/api';
import { useTopics } from '@/lib/ai';
import { parseApiError } from '@/lib/apiError';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import TopicPicker from '@/components/ai/TopicPicker';
import CountSteppers from '@/components/ai/CountSteppers';
import {
  DIFFICULTIES,
  type Difficulty,
  type MockAttemptResponse,
  type MockRequestBody,
  type QuestionCounts,
  type UUID,
} from '@/types/api';

/** A student's request for a practice quiz or paper from chosen topics. */
export default function MockConfigModal({
  pscId,
  courseLabel,
  onClose,
}: {
  pscId: UUID;
  courseLabel: string;
  onClose: () => void;
}) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const topicsQ = useTopics(pscId);
  const [kind, setKind] = useState<'QUIZ' | 'PAPER'>('QUIZ');
  const [topicIds, setTopicIds] = useState<UUID[]>([]);
  const [counts, setCounts] = useState<QuestionCounts>({ MCQ: 8, MSQ: 2, SHORT_ANSWER: 0, LONG_ANSWER: 0 });
  const [difficulty, setDifficulty] = useState<Difficulty>('MEDIUM');
  const [duration, setDuration] = useState(60);

  const create = useMutation({
    mutationFn: (body: MockRequestBody) =>
      api.post<MockAttemptResponse>('/me/ai/mocks', body).then((r) => r.data),
    onSuccess: (m) => {
      qc.invalidateQueries({ queryKey: ['me', 'ai', 'mocks'] });
      navigate(`/study-assistant/mocks/${m.id}`);
    },
  });

  function switchKind(k: 'QUIZ' | 'PAPER') {
    setKind(k);
    setCounts(k === 'QUIZ'
      ? { MCQ: 8, MSQ: 2, SHORT_ANSWER: 0, LONG_ANSWER: 0 }
      : { MCQ: 10, MSQ: 0, SHORT_ANSWER: 3, LONG_ANSWER: 1 });
  }

  const total = Object.values(counts).reduce((a, b) => a + (b ?? 0), 0);

  return (
    <Modal open onClose={onClose} title={`Practice ${kind === 'QUIZ' ? 'quiz' : 'paper'} — ${courseLabel}`} maxWidth="max-w-2xl">
      <div className="space-y-5">
        <div className="flex gap-2">
          {(['QUIZ', 'PAPER'] as const).map((k) => (
            <button
              key={k}
              type="button"
              onClick={() => switchKind(k)}
              className={`flex-1 rounded-lg border px-3 py-2 text-sm font-medium ${
                kind === k ? 'border-primary-500 bg-primary-50 text-primary-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50'
              }`}
            >
              {k === 'QUIZ' ? 'Quick quiz' : 'Mock paper'}
              <span className="block text-xs font-normal text-gray-500">
                {k === 'QUIZ' ? 'Choice questions, marked instantly' : 'Mixed questions, timed like an exam'}
              </span>
            </button>
          ))}
        </div>

        <div>
          <p className="mb-1 text-sm font-medium text-gray-700">Topics</p>
          {topicsQ.isLoading ? (
            <Spinner size="sm" />
          ) : (topicsQ.data ?? []).length === 0 ? (
            <p className="text-sm text-gray-500">
              No topics yet — your teacher's uploads are still being indexed, or none have been posted.
            </p>
          ) : (
            <TopicPicker topics={topicsQ.data ?? []} selected={topicIds} onChange={setTopicIds} />
          )}
          <p className="mt-1 text-xs text-gray-500">Leave empty to practise on everything.</p>
        </div>

        <div>
          <p className="mb-1 text-sm font-medium text-gray-700">Questions</p>
          <CountSteppers counts={counts} onChange={setCounts} />
        </div>

        <div className="flex flex-wrap items-end gap-4">
          <label className="text-sm">
            <span className="mb-1 block font-medium text-gray-700">Difficulty</span>
            <select
              value={difficulty}
              onChange={(e) => setDifficulty(e.target.value as Difficulty)}
              className="h-9 rounded-lg border border-gray-300 px-2 text-sm"
            >
              {DIFFICULTIES.map((d) => (
                <option key={d} value={d}>{d.charAt(0) + d.slice(1).toLowerCase()}</option>
              ))}
            </select>
          </label>
          {kind === 'PAPER' && (
            <label className="text-sm">
              <span className="mb-1 block font-medium text-gray-700">Time (minutes)</span>
              <input
                type="number"
                min={10}
                max={300}
                value={duration}
                onChange={(e) => setDuration(Number(e.target.value))}
                className="h-9 w-24 rounded-lg border border-gray-300 px-2 text-sm"
              />
            </label>
          )}
        </div>

        {create.error != null && <p className="text-sm text-red-600">{parseApiError(create.error)}</p>}

        <div className="flex items-center justify-between border-t border-gray-100 pt-4">
          <p className="text-xs text-gray-500">Practice only — never counted toward your grade.</p>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={onClose}>Cancel</Button>
            <Button
              disabled={total === 0}
              loading={create.isPending}
              onClick={() =>
                create.mutate({
                  pscId,
                  kind,
                  topicIds,
                  counts,
                  difficulty,
                  durationMinutes: kind === 'PAPER' ? duration : undefined,
                })
              }
            >
              Generate ({total})
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}
