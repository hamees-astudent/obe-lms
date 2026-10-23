import { useState } from 'react';
import { X } from 'lucide-react';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import { isWrittenType, type QuestionDraftResponse, type RubricItem, type UpdateDraftBody } from '@/types/api';

/** Full edit of a generated question's wording, options, model answer and rubric. */
export default function DraftEditModal({
  draft,
  saving,
  error,
  onSave,
  onClose,
}: {
  draft: QuestionDraftResponse;
  saving: boolean;
  error?: string;
  onSave: (body: UpdateDraftBody) => void;
  onClose: () => void;
}) {
  const [text, setText] = useState(draft.questionText);
  const [options, setOptions] = useState(draft.options);
  const [correct, setCorrect] = useState<string[]>(draft.correctAnswer);
  const [model, setModel] = useState(draft.modelAnswer ?? '');
  const [rubric, setRubric] = useState<RubricItem[]>(draft.rubric ?? []);
  const [explanation, setExplanation] = useState(draft.explanation ?? '');
  const written = isWrittenType(draft.type);
  const rubricTotal = rubric.reduce((s, r) => s + (Number(r.marks) || 0), 0);

  return (
    <Modal open onClose={onClose} title="Edit generated question" maxWidth="max-w-2xl">
      <div className="space-y-4">
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={3}
          aria-label="Question"
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
        />
        {!written ? (
          <div className="space-y-2">
            {options.map((o, i) => (
              <div key={o.id} className="flex items-center gap-2">
                <span className="w-5 text-xs font-semibold text-gray-500">{String.fromCharCode(65 + i)}</span>
                <input
                  value={o.text}
                  onChange={(e) => setOptions(options.map((x) => (x.id === o.id ? { ...x, text: e.target.value } : x)))}
                  className="flex-1 rounded-lg border border-gray-300 px-2.5 py-1.5 text-sm"
                />
                <label className="flex items-center gap-1 text-xs">
                  <input
                    type={draft.type === 'MCQ' ? 'radio' : 'checkbox'}
                    name="draft-correct"
                    checked={correct.includes(o.id)}
                    onChange={() =>
                      setCorrect(draft.type === 'MCQ' ? [o.id] : correct.includes(o.id) ? correct.filter((c) => c !== o.id) : [...correct, o.id])
                    }
                  />
                  Correct
                </label>
              </div>
            ))}
          </div>
        ) : (
          <>
            <div>
              <p className="mb-1 text-sm font-medium text-gray-700">Model answer</p>
              <textarea value={model} onChange={(e) => setModel(e.target.value)} rows={5} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" />
            </div>
            <div>
              <div className="mb-1 flex items-center justify-between">
                <p className="text-sm font-medium text-gray-700">
                  Rubric <span className={`text-xs font-normal ${rubricTotal > draft.marks ? 'text-red-600' : 'text-gray-500'}`}>{rubricTotal} of {draft.marks} marks</span>
                </p>
                <button type="button" className="text-xs text-primary-600" onClick={() => setRubric([...rubric, { criterion: '', marks: 1, keywords: [] }])}>
                  + Add criterion
                </button>
              </div>
              {rubric.map((r, i) => (
                <div key={i} className="mb-2 grid grid-cols-[1fr_4.5rem_1fr_auto] gap-2">
                  <input value={r.criterion} onChange={(e) => setRubric(rubric.map((x, j) => (j === i ? { ...x, criterion: e.target.value } : x)))} className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm" />
                  <input type="number" step="0.5" value={r.marks} onChange={(e) => setRubric(rubric.map((x, j) => (j === i ? { ...x, marks: Number(e.target.value) } : x)))} className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm" />
                  <input
                    value={(r.keywords ?? []).join(', ')}
                    onChange={(e) => setRubric(rubric.map((x, j) => (j === i ? { ...x, keywords: e.target.value.split(',').map((k) => k.trim()).filter(Boolean) } : x)))}
                    placeholder="keywords"
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                  />
                  <button type="button" aria-label="Remove criterion" onClick={() => setRubric(rubric.filter((_, j) => j !== i))} className="p-1 text-gray-400 hover:text-red-500">
                    <X size={14} />
                  </button>
                </div>
              ))}
            </div>
          </>
        )}
        <input
          value={explanation}
          onChange={(e) => setExplanation(e.target.value)}
          placeholder="Explanation / note (optional)"
          className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
        />
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={saving}
            onClick={() =>
              onSave({
                questionText: text,
                options: written ? [] : options,
                correctAnswer: written ? [] : correct,
                modelAnswer: written ? model : undefined,
                rubric: written ? rubric : [],
                explanation: explanation || undefined,
                marks: draft.marks,
                bloomLevel: draft.bloomLevel,
                difficulty: draft.difficulty,
                cloIds: draft.suggestedCloIds,
                status: draft.status,
              })
            }
          >
            Save
          </Button>
        </div>
      </div>
    </Modal>
  );
}
