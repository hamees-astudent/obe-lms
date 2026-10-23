import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, useFieldArray, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { AlertTriangle, Plus, Sparkles, X } from 'lucide-react';
import api from '@/lib/api';
import { useCourseClos } from '@/lib/ai';
import { parseApiError } from '@/lib/apiError';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Input from '@/components/ui/Input';
import BloomBadge from '@/components/ai/BloomBadge';
import {
  BLOOM_LABELS,
  BLOOM_LEVELS,
  DIFFICULTIES,
  QUESTION_TYPE_LABELS,
  isWrittenType,
  type BloomLevel,
  type Difficulty,
  type QuestionAssistResponse,
  type QuestionType,
  type QuizQuestionResponse,
  type UUID,
} from '@/types/api';

/** Position label shown to the teacher: A, B, C… by row, whatever the stored id. */
export const optionLabel = (idx: number) => String.fromCharCode(65 + idx);

/** The first unused id from a, b, c… so ids stay unique after rows are removed. */
export function nextOptionId(used: string[]): string {
  for (let i = 0; i < 26; i++) {
    const id = String.fromCharCode(97 + i);
    if (!used.includes(id)) return id;
  }
  return `o${used.length + 1}`;
}

/** Correct answers that actually name one of the options. */
export function validCorrectIds(q: Pick<QuizQuestionResponse, 'options' | 'correctAnswer'>): string[] {
  const ids = new Set(q.options.map((o) => o.id));
  return (q.correctAnswer ?? []).filter((id) => ids.has(id));
}

const TYPES: QuestionType[] = ['MCQ', 'MSQ', 'SHORT_ANSWER', 'LONG_ANSWER'];

const questionSchema = z
  .object({
    questionText: z.string().min(1, 'Required'),
    type: z.enum(['MCQ', 'MSQ', 'SHORT_ANSWER', 'LONG_ANSWER']),
    options: z.array(z.object({ id: z.string().min(1), text: z.string() })),
    correctAnswer: z.array(z.string()),
    marks: z.coerce.number().min(0.01, 'Must be > 0'),
    orderIndex: z.coerce.number().int().min(0).optional().or(z.literal('')),
    explanation: z.string().optional(),
    bloomLevel: z.string().optional(),
    difficulty: z.string().optional(),
    modelAnswer: z.string().optional(),
    rubric: z.array(z.object({
      criterion: z.string().min(1, 'Required'),
      marks: z.coerce.number().min(0, '≥ 0'),
      keywords: z.string().optional(),
    })),
    cloIds: z.array(z.string()),
  })
  .superRefine((d, ctx) => {
    if (isWrittenType(d.type)) {
      if (!d.modelAnswer?.trim()) {
        ctx.addIssue({ code: 'custom', path: ['modelAnswer'], message: 'A model answer is needed to mark against' });
      }
      const total = d.rubric.reduce((s, r) => s + (Number(r.marks) || 0), 0);
      if (total > d.marks) {
        ctx.addIssue({ code: 'custom', path: ['rubric'], message: `The rubric adds up to ${total}, more than the question's ${d.marks} marks` });
      }
      return;
    }
    if (d.options.length < 2) ctx.addIssue({ code: 'custom', path: ['options'], message: 'At least 2 options required' });
    d.options.forEach((o, i) => {
      if (!o.text.trim()) ctx.addIssue({ code: 'custom', path: ['options', i, 'text'], message: 'Required' });
    });
    if (d.correctAnswer.length === 0) {
      ctx.addIssue({ code: 'custom', path: ['correctAnswer'], message: 'Select at least one correct answer' });
    }
  });
type QuestionFormData = z.infer<typeof questionSchema>;

interface QuestionEditorProps {
  quizId: UUID;
  pscId: UUID;
  courseId?: UUID;
  editing?: QuizQuestionResponse | null;
  nextOrderIndex: number;
  onClose: () => void;
}

/**
 * Adds or edits one quiz question: single/multiple choice or a written answer
 * with its model answer and rubric, plus its Bloom level, difficulty and CLOs.
 * "Suggest" asks the server for a Bloom level, marks and CLO — classical NLP,
 * refined by the local model when it is running.
 */
export default function QuestionEditor({ quizId, pscId, courseId, editing, nextOrderIndex, onClose }: QuestionEditorProps) {
  const queryClient = useQueryClient();
  const closQ = useCourseClos(courseId);
  const clos = closQ.data ?? [];
  // Questions saved by the old builder stored row keys, not option ids, as the
  // correct answer. Drop those on load and say so, rather than show nothing ticked.
  const savedCorrect = editing ? validCorrectIds(editing) : [];
  const hadBrokenAnswer = !!editing && !isWrittenType(editing.type) && savedCorrect.length === 0;

  const { register, handleSubmit, watch, control, setValue, getValues, formState: { errors } } =
    useForm<QuestionFormData>({
      resolver: zodResolver(questionSchema),
      defaultValues: editing
        ? {
            questionText: editing.questionText,
            type: editing.type,
            options: isWrittenType(editing.type) ? [{ id: 'a', text: '' }, { id: 'b', text: '' }] : editing.options,
            correctAnswer: savedCorrect,
            marks: editing.marks,
            orderIndex: editing.orderIndex,
            explanation: editing.explanation ?? '',
            bloomLevel: editing.bloomLevel ?? '',
            difficulty: editing.difficulty ?? '',
            modelAnswer: editing.modelAnswer ?? '',
            rubric: (editing.rubric ?? []).map((r) => ({ criterion: r.criterion, marks: r.marks, keywords: (r.keywords ?? []).join(', ') })),
            cloIds: editing.cloIds ?? [],
          }
        : {
            type: 'MCQ',
            options: [{ id: 'a', text: '' }, { id: 'b', text: '' }],
            correctAnswer: [],
            marks: 1,
            orderIndex: nextOrderIndex,
            bloomLevel: '',
            difficulty: '',
            modelAnswer: '',
            rubric: [],
            cloIds: [],
          },
    });

  // `fields[i].id` is react-hook-form's own row key, not the option's id —
  // useFieldArray overwrites `id`. Option ids are read from the form values instead.
  const { fields, append, remove } = useFieldArray({ control, name: 'options' });
  const rubricRows = useFieldArray({ control, name: 'rubric' });
  const options = watch('options') ?? [];
  const qType = watch('type');
  const written = isWrittenType(qType);
  const correctAnswer = watch('correctAnswer') ?? [];
  const cloIds = watch('cloIds') ?? [];
  const marks = Number(watch('marks')) || 0;
  const rubricTotal = (watch('rubric') ?? []).reduce((s, r) => s + (Number(r.marks) || 0), 0);
  const [suggestion, setSuggestion] = useState<QuestionAssistResponse | null>(null);

  // Switching to single-answer keeps only the first answer ticked.
  useEffect(() => {
    if (qType === 'MCQ' && correctAnswer.length > 1) {
      setValue('correctAnswer', [correctAnswer[0]], { shouldValidate: true });
    }
  }, [qType, correctAnswer, setValue]);

  function toggleCorrect(optId: string) {
    const next =
      qType === 'MCQ'
        ? [optId]
        : correctAnswer.includes(optId)
          ? correctAnswer.filter((x) => x !== optId)
          : [...correctAnswer, optId];
    setValue('correctAnswer', next, { shouldValidate: true });
  }

  function removeOption(idx: number) {
    const id = options[idx]?.id;
    remove(idx);
    if (id && correctAnswer.includes(id)) {
      setValue('correctAnswer', correctAnswer.filter((x) => x !== id), { shouldValidate: true });
    }
  }

  function toggleClo(id: UUID) {
    setValue('cloIds', cloIds.includes(id) ? cloIds.filter((x) => x !== id) : [...cloIds, id]);
  }

  const assist = useMutation({
    mutationFn: () => {
      const v = getValues();
      return api
        .post<QuestionAssistResponse>('/ai/question-assist', {
          pscId,
          questionText: v.questionText,
          type: v.type,
          options: isWrittenType(v.type) ? undefined : v.options,
          correctAnswer: isWrittenType(v.type) ? undefined : v.correctAnswer,
          modelAnswer: isWrittenType(v.type) ? v.modelAnswer : undefined,
          difficulty: v.difficulty || undefined,
        })
        .then((r) => r.data);
    },
    onSuccess: setSuggestion,
  });

  function applySuggestion(s: QuestionAssistResponse) {
    setValue('bloomLevel', s.bloomLevel);
    setValue('marks', s.marks, { shouldValidate: true });
    setValue('cloIds', s.suggestedCloIds);
    setSuggestion(null);
  }

  const mutation = useMutation({
    mutationFn: (data: QuestionFormData) => {
      const isWritten = isWrittenType(data.type);
      const body = {
        questionText: data.questionText,
        type: data.type,
        options: isWritten ? [] : data.options,
        correctAnswer: isWritten ? [] : data.correctAnswer,
        marks: data.marks,
        orderIndex: data.orderIndex === '' || data.orderIndex === undefined ? nextOrderIndex : Number(data.orderIndex),
        explanation: data.explanation || undefined,
        bloomLevel: (data.bloomLevel || undefined) as BloomLevel | undefined,
        difficulty: (data.difficulty || undefined) as Difficulty | undefined,
        modelAnswer: isWritten ? data.modelAnswer : undefined,
        rubric: isWritten
          ? data.rubric.map((r) => ({
              criterion: r.criterion,
              marks: Number(r.marks),
              keywords: (r.keywords ?? '').split(',').map((k) => k.trim()).filter(Boolean),
            }))
          : [],
        cloIds: data.cloIds,
      };
      if (editing) {
        return api.put<QuizQuestionResponse>(`/quiz-questions/${editing.id}`, body).then((r) => r.data);
      }
      return api.post<QuizQuestionResponse>(`/quizzes/${quizId}/questions`, body).then((r) => r.data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['quizzes', quizId, 'questions'] });
      queryClient.invalidateQueries({ queryKey: ['quizzes', quizId, 'clo-mappings'] });
      onClose();
    },
  });

  return (
    <Modal open onClose={onClose} title={editing ? 'Edit Question' : 'Add Question'} maxWidth="max-w-2xl">
      <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-4">
        {hadBrokenAnswer && (
          <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
            <AlertTriangle size={16} className="mt-0.5 flex-shrink-0" />
            <span>
              This question&apos;s saved correct answer didn&apos;t match any option, so no student
              could score on it. Mark the correct answer again and save.
            </span>
          </div>
        )}

        <div>
          <label className="mb-1 block text-sm font-medium text-gray-700">Question</label>
          <textarea
            {...register('questionText')}
            rows={3}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
          />
          {errors.questionText && <p className="mt-1 text-xs text-red-600">{errors.questionText.message}</p>}
        </div>

        <fieldset className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
          <legend className="sr-only">Question type</legend>
          <span className="font-medium text-gray-700">Type:</span>
          {TYPES.map((t) => (
            <label key={t} className="flex items-center gap-1.5">
              <input type="radio" value={t} {...register('type')} />
              {QUESTION_TYPE_LABELS[t]}
            </label>
          ))}
        </fieldset>

        {!written ? (
          <div>
            <div className="mb-2 flex items-center justify-between">
              <span className="text-sm font-medium text-gray-700">
                Options{' '}
                <span className="text-xs font-normal text-gray-500">
                  {qType === 'MCQ' ? '— select the one correct answer' : '— tick every correct answer'}
                </span>
              </span>
              <button
                type="button"
                onClick={() => append({ id: nextOptionId(options.map((o) => o.id)), text: '' })}
                disabled={fields.length >= 26}
                className="text-xs font-medium text-primary-600 hover:text-primary-800 disabled:text-gray-300"
              >
                + Add option
              </button>
            </div>
            {errors.options?.message && <p className="mb-1 text-xs text-red-600">{errors.options.message}</p>}
            {fields.map((field, idx) => {
              const optId = options[idx]?.id ?? '';
              const isCorrect = correctAnswer.includes(optId);
              const textError = errors.options?.[idx]?.text?.message;
              return (
                <div key={field.id} className="mb-2">
                  <div className={`flex items-center gap-2 rounded-lg border px-2 py-1.5 ${isCorrect ? 'border-green-300 bg-green-50' : 'border-transparent'}`}>
                    <span className="w-5 flex-shrink-0 text-center text-xs font-semibold text-gray-500">{optionLabel(idx)}</span>
                    <Controller
                      name={`options.${idx}.text`}
                      control={control}
                      render={({ field: f }) => (
                        <input
                          {...f}
                          aria-label={`Option ${optionLabel(idx)}`}
                          aria-invalid={!!textError}
                          placeholder={`Option ${optionLabel(idx)}`}
                          className={`min-w-0 flex-1 rounded-lg border px-2.5 py-1.5 text-sm focus:border-primary-500 focus:outline-none ${textError ? 'border-red-400' : 'border-gray-300'}`}
                        />
                      )}
                    />
                    <label className="flex flex-shrink-0 cursor-pointer items-center gap-1 text-xs font-medium text-gray-600">
                      <input
                        type={qType === 'MCQ' ? 'radio' : 'checkbox'}
                        name="correct-answer"
                        checked={isCorrect}
                        onChange={() => toggleCorrect(optId)}
                        className="h-4 w-4 accent-green-600"
                      />
                      Correct
                    </label>
                    {fields.length > 2 && (
                      <button type="button" onClick={() => removeOption(idx)} aria-label={`Remove option ${optionLabel(idx)}`} className="flex-shrink-0 p-1 text-gray-400 hover:text-red-500">
                        <X size={14} />
                      </button>
                    )}
                  </div>
                  {textError && <p className="ml-9 mt-0.5 text-xs text-red-600">{textError}</p>}
                </div>
              );
            })}
            {errors.correctAnswer && <p className="mt-1 text-xs text-red-600">{errors.correctAnswer.message as string}</p>}
          </div>
        ) : (
          <div className="space-y-3">
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                Model answer <span className="text-xs font-normal text-gray-500">— what a full-marks answer says; never shown to students before marking</span>
              </label>
              <textarea
                {...register('modelAnswer')}
                rows={qType === 'LONG_ANSWER' ? 6 : 3}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
              />
              {errors.modelAnswer && <p className="mt-1 text-xs text-red-600">{errors.modelAnswer.message}</p>}
            </div>
            <div>
              <div className="mb-1 flex items-center justify-between">
                <span className="text-sm font-medium text-gray-700">
                  Rubric{' '}
                  <span className={`text-xs font-normal ${rubricTotal > marks ? 'text-red-600' : 'text-gray-500'}`}>
                    {rubricTotal} of {marks} marks allotted
                  </span>
                </span>
                <button
                  type="button"
                  onClick={() => rubricRows.append({ criterion: '', marks: 1, keywords: '' })}
                  className="text-xs font-medium text-primary-600 hover:text-primary-800"
                >
                  + Add criterion
                </button>
              </div>
              {rubricRows.fields.map((f, i) => (
                <div key={f.id} className="mb-2 grid grid-cols-[1fr_4.5rem_1fr_auto] gap-2">
                  <input {...register(`rubric.${i}.criterion`)} placeholder="Criterion, e.g. Defines 3NF" className="rounded-lg border border-gray-300 px-2.5 py-1.5 text-sm" />
                  <input {...register(`rubric.${i}.marks`)} type="number" step="0.5" min="0" aria-label="Marks" className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm" />
                  <input {...register(`rubric.${i}.keywords`)} placeholder="Keywords, comma separated" className="rounded-lg border border-gray-300 px-2.5 py-1.5 text-sm" />
                  <button type="button" onClick={() => rubricRows.remove(i)} aria-label="Remove criterion" className="p-1 text-gray-400 hover:text-red-500">
                    <X size={14} />
                  </button>
                </div>
              ))}
              {errors.rubric?.message && <p className="text-xs text-red-600">{errors.rubric.message}</p>}
              {errors.rubric?.root?.message && <p className="text-xs text-red-600">{errors.rubric.root.message}</p>}
            </div>
          </div>
        )}

        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Input label="Marks" type="number" step="0.5" error={errors.marks?.message} {...register('marks')} />
          <Input label="Order index" type="number" min="0" {...register('orderIndex')} />
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-gray-700">Bloom level</span>
            <select {...register('bloomLevel')} className="h-9 rounded-lg border border-gray-300 px-2 text-sm">
              <option value="">—</option>
              {BLOOM_LEVELS.map((b) => <option key={b} value={b}>{BLOOM_LABELS[b]}</option>)}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium text-gray-700">Difficulty</span>
            <select {...register('difficulty')} className="h-9 rounded-lg border border-gray-300 px-2 text-sm">
              <option value="">—</option>
              {DIFFICULTIES.map((d) => <option key={d} value={d}>{d.charAt(0) + d.slice(1).toLowerCase()}</option>)}
            </select>
          </label>
        </div>

        {clos.length > 0 && (
          <div>
            <p className="mb-1 text-sm font-medium text-gray-700">CLOs assessed</p>
            <div className="flex flex-wrap gap-1.5">
              {clos.map((c) => {
                const on = cloIds.includes(c.id);
                return (
                  <button
                    key={c.id}
                    type="button"
                    title={c.title}
                    onClick={() => toggleClo(c.id)}
                    className={`rounded-full border px-2.5 py-1 text-xs ${on ? 'border-primary-500 bg-primary-50 text-primary-700' : 'border-gray-200 text-gray-600 hover:border-primary-300'}`}
                  >
                    {c.code}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        <div className="rounded-lg border border-dashed border-primary-200 bg-primary-50/40 p-3">
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs text-gray-600">Suggest a Bloom level, marks and CLO from the question text.</p>
            <Button type="button" size="sm" variant="secondary" loading={assist.isPending} onClick={() => assist.mutate()}>
              <Sparkles size={13} /> Suggest
            </Button>
          </div>
          {assist.error != null && <p className="mt-2 text-xs text-red-600">{parseApiError(assist.error)}</p>}
          {suggestion && (
            <div className="mt-3 space-y-1.5 text-xs text-gray-700">
              <p className="flex items-center gap-2">
                <BloomBadge level={suggestion.bloomLevel} /> <span className="text-gray-500">{suggestion.bloomSource}</span>
              </p>
              <p><span className="font-semibold">{suggestion.marks} marks</span> <span className="text-gray-500">({suggestion.marksBreakdown})</span></p>
              <p>
                <span className="font-semibold">
                  {clos.filter((c) => suggestion.suggestedCloIds.includes(c.id)).map((c) => c.code).join(', ') || 'No CLO'}
                </span>{' '}
                <span className="text-gray-500">— {suggestion.cloRationale}</span>
              </p>
              <p className="text-gray-400">
                Scores: {suggestion.cloScores.map((s) => `${s.code} ${s.score.toFixed(2)}`).join(' · ')}
                {suggestion.modelUsed ? ' · checked by the local model' : ''}
              </p>
              <div className="flex gap-2 pt-1">
                <Button type="button" size="sm" onClick={() => applySuggestion(suggestion)}>Apply</Button>
                <Button type="button" size="sm" variant="ghost" onClick={() => setSuggestion(null)}>Dismiss</Button>
              </div>
            </div>
          )}
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-gray-700">
            {written ? 'Note shown after marking' : 'Explanation (shown after submit)'}
          </label>
          <input
            {...register('explanation')}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
            placeholder="Optional…"
          />
        </div>

        {mutation.error != null && <p className="text-xs text-red-600">{parseApiError(mutation.error)}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={mutation.isPending}>
            <Plus size={14} className={editing ? 'hidden' : ''} />
            {editing ? 'Save Changes' : 'Add Question'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
