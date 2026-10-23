import { Minus, Plus } from 'lucide-react';
import { QUESTION_TYPE_LABELS, type QuestionCounts, type QuestionType } from '@/types/api';

const TYPES: QuestionType[] = ['MCQ', 'MSQ', 'SHORT_ANSWER', 'LONG_ANSWER'];

/** How many questions of each type, with optional marks per question beside each. */
export default function CountSteppers({
  counts,
  onChange,
  marksPerQuestion,
  max = 50,
}: {
  counts: QuestionCounts;
  onChange: (c: QuestionCounts) => void;
  marksPerQuestion?: Partial<Record<QuestionType, number>>;
  max?: number;
}) {
  const set = (t: QuestionType, n: number) => onChange({ ...counts, [t]: Math.max(0, Math.min(max, n)) });
  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
      {TYPES.map((t) => {
        const n = counts[t] ?? 0;
        return (
          <div key={t} className="flex items-center justify-between rounded-lg border border-gray-200 px-3 py-2">
            <div>
              <p className="text-sm font-medium text-gray-800">{QUESTION_TYPE_LABELS[t]}</p>
              {marksPerQuestion?.[t] !== undefined && (
                <p className="text-xs text-gray-500">about {marksPerQuestion[t]} mark{marksPerQuestion[t] === 1 ? '' : 's'} each</p>
              )}
            </div>
            <div className="flex items-center gap-1">
              <button
                type="button"
                aria-label={`Fewer ${QUESTION_TYPE_LABELS[t]}`}
                onClick={() => set(t, n - 1)}
                disabled={n === 0}
                className="rounded p-1 text-gray-500 hover:bg-gray-100 disabled:opacity-30"
              >
                <Minus size={14} />
              </button>
              <input
                type="number"
                min={0}
                max={max}
                value={n}
                aria-label={`${QUESTION_TYPE_LABELS[t]} count`}
                onChange={(e) => set(t, Number(e.target.value) || 0)}
                className="h-8 w-12 rounded border border-gray-300 text-center text-sm"
              />
              <button
                type="button"
                aria-label={`More ${QUESTION_TYPE_LABELS[t]}`}
                onClick={() => set(t, n + 1)}
                className="rounded p-1 text-gray-500 hover:bg-gray-100"
              >
                <Plus size={14} />
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}
