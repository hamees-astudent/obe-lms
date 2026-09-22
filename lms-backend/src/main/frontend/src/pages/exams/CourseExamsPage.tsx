import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, useFieldArray } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  ChevronLeft,
  Plus,
  Pencil,
  Trash2,
  ScanLine,
  Lock,
  Unlock,
  AlertTriangle,
  X,
  Calendar,
} from 'lucide-react';
import api from '@/lib/api';
import { toast } from '@/components/ui/Toast';
import { parseApiError } from '@/lib/apiError';
import { formatDate, toInputDate } from '@/lib/datetime';
import { useAuthStore } from '@/store/authStore';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Input from '@/components/ui/Input';
import Spinner from '@/components/ui/Spinner';
import QueryError from '@/components/ui/QueryError';
import { EXAM_TYPE_LABELS } from '@/types/api';
import type {
  UUID,
  ExamResponse,
  ExamStatus,
  ExamCloMappingResponse,
  OfferingSummaryResponse,
} from '@/types/api';

const STATUS_VARIANT: Record<ExamStatus, 'default' | 'success' | 'warning'> = {
  DRAFT: 'warning',
  OPEN: 'success',
  LOCKED: 'default',
};

const STATUS_HINT: Record<ExamStatus, string> = {
  DRAFT: 'Still being set up — scanning is disabled until it is opened.',
  OPEN: 'Accepting scanned copies and manual marks.',
  LOCKED: 'Marks are final and can no longer be changed.',
};

// ---------------------------------------------------------------------------
// Create / edit exam
// ---------------------------------------------------------------------------
const examSchema = z.object({
  title: z.string().min(1, 'Required').max(255),
  examType: z.enum(['MIDTERM', 'FINAL', 'SESSIONAL', 'MAKEUP']),
  examDate: z.string().min(1, 'Required'),
  totalMarks: z.coerce.number().min(0.01, 'Must be greater than 0'),
  questions: z
    .array(
      z.object({
        questionNo: z.string().min(1, 'Required').max(10),
        maxMarks: z.coerce.number().min(0.01, 'Must be greater than 0'),
        cloIds: z.array(z.string()).optional(),
      }),
    )
    .min(1, 'Add at least one question'),
});
type ExamFormData = z.infer<typeof examSchema>;

interface ExamFormModalProps {
  pscId: UUID;
  editing?: ExamResponse | null;
  onClose: () => void;
}

function ExamFormModal({ pscId, editing, onClose }: ExamFormModalProps) {
  const queryClient = useQueryClient();

  // CLOs are only fetchable once an exam exists, so a new exam maps its
  // questions to CLOs on the second pass (edit). Shown as a hint below.
  const closQ = useQuery({
    queryKey: ['exams', editing?.id, 'clos'],
    queryFn: () =>
      api.get<ExamCloMappingResponse[]>(`/exams/${editing!.id}/clos`).then((r) => r.data),
    enabled: !!editing,
  });
  const clos = closQ.data ?? [];

  const {
    register,
    control,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ExamFormData>({
    resolver: zodResolver(examSchema),
    defaultValues: editing
      ? {
          title: editing.title,
          examType: editing.examType,
          examDate: toInputDate(editing.examDate),
          totalMarks: editing.totalMarks,
          questions: editing.questions.map((q) => ({
            questionNo: q.questionNo,
            maxMarks: q.maxMarks,
            cloIds: q.cloMappings.map((m) => m.cloId),
          })),
        }
      : {
          examType: 'MIDTERM',
          questions: [{ questionNo: '1', maxMarks: 10, cloIds: [] }],
        },
  });

  const { fields, append, remove } = useFieldArray({ control, name: 'questions' });

  const watched = watch('questions');
  const questionSum = (watched ?? []).reduce(
    (sum, q) => sum + (Number(q?.maxMarks) || 0),
    0,
  );
  const declaredTotal = Number(watch('totalMarks')) || 0;
  const sumMismatch = declaredTotal > 0 && Math.abs(questionSum - declaredTotal) > 0.001;

  const mutation = useMutation({
    mutationFn: (data: ExamFormData) => {
      const body = {
        title: data.title,
        examType: data.examType,
        examDate: data.examDate,
        totalMarks: data.totalMarks,
        questions: data.questions.map((q) => ({
          questionNo: q.questionNo,
          maxMarks: q.maxMarks,
          cloMappings: (q.cloIds ?? []).map((cloId) => ({ cloId })),
        })),
      };
      if (editing) {
        return api.put<ExamResponse>(`/exams/${editing.id}`, body).then((r) => r.data);
      }
      return api
        .post<ExamResponse>(`/offerings/${pscId}/exams`, { ...body, pscId })
        .then((r) => r.data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'exams'] });
      toast.success(editing ? 'Exam updated' : 'Exam created');
      onClose();
    },
    onError: (error) => toast.error(parseApiError(error)),
  });

  return (
    <Modal
      open
      onClose={onClose}
      title={editing ? 'Edit exam' : 'New exam'}
      maxWidth="max-w-3xl"
    >
      <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-4">
        <Input label="Title" placeholder="Midterm Examination" {...register('title')} error={errors.title?.message} />

        <div className="grid gap-4 sm:grid-cols-3">
          <div className="flex flex-col gap-1">
            <label htmlFor="examType" className="text-sm font-medium text-gray-700">
              Type
            </label>
            <select
              id="examType"
              {...register('examType')}
              className="h-9 w-full rounded-lg border border-gray-300 px-3 text-sm shadow-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              {Object.entries(EXAM_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>

          <Input
            label="Exam date"
            type="date"
            {...register('examDate')}
            error={errors.examDate?.message}
            hint="Cross-checked against the date on each copy"
          />

          <Input
            label="Total marks"
            type="number"
            step="0.01"
            {...register('totalMarks')}
            error={errors.totalMarks?.message}
          />
        </div>

        {/* ── Questions ─────────────────────────────────────────────────── */}
        <div className="rounded-lg border border-gray-200 p-4">
          <div className="mb-3 flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-gray-900">Marks table</h3>
              <p className="text-xs text-gray-500">
                The rows a scanned copy is matched against. Use the labels as they appear
                on the paper.
              </p>
            </div>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={() =>
                append({ questionNo: String(fields.length + 1), maxMarks: 10, cloIds: [] })
              }
            >
              <Plus size={14} />
              Add question
            </Button>
          </div>

          <div className="space-y-2">
            {fields.map((field, index) => (
              <div key={field.id} className="flex items-start gap-2">
                <div className="w-24">
                  <Input
                    placeholder="1"
                    aria-label={`Question ${index + 1} label`}
                    {...register(`questions.${index}.questionNo`)}
                    error={errors.questions?.[index]?.questionNo?.message}
                  />
                </div>
                <div className="w-28">
                  <Input
                    type="number"
                    step="0.01"
                    placeholder="10"
                    aria-label={`Question ${index + 1} maximum marks`}
                    {...register(`questions.${index}.maxMarks`)}
                    error={errors.questions?.[index]?.maxMarks?.message}
                  />
                </div>
                <div className="flex-1">
                  {editing && clos.length > 0 ? (
                    <select
                      multiple
                      aria-label={`Question ${index + 1} CLOs`}
                      {...register(`questions.${index}.cloIds`)}
                      className="min-h-[36px] w-full rounded-lg border border-gray-300 px-2 py-1 text-xs shadow-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
                    >
                      {clos.map((clo) => (
                        <option key={clo.cloId} value={clo.cloId}>
                          {clo.cloCode} — {clo.cloTitle}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <p className="pt-2 text-xs text-gray-400">
                      {editing
                        ? 'No CLOs defined on this course yet.'
                        : 'Save the exam, then reopen it to map questions to CLOs.'}
                    </p>
                  )}
                </div>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  aria-label={`Remove question ${index + 1}`}
                  onClick={() => remove(index)}
                  disabled={fields.length === 1}
                >
                  <X size={14} />
                </Button>
              </div>
            ))}
          </div>

          <div className="mt-3 flex items-center justify-between border-t border-gray-100 pt-3 text-sm">
            <span className="text-gray-500">Questions add up to</span>
            <span className={sumMismatch ? 'font-semibold text-amber-600' : 'font-semibold text-gray-900'}>
              {questionSum.toFixed(2)} / {declaredTotal.toFixed(2)}
            </span>
          </div>
          {sumMismatch && (
            <p className="mt-1 flex items-start gap-1 text-xs text-amber-600">
              <AlertTriangle size={13} className="mt-0.5 shrink-0" />
              The questions do not add up to the exam total. You can save now, but the exam
              cannot be opened for marking until they match.
            </p>
          )}
          {errors.questions?.message && (
            <p className="mt-1 text-xs text-red-600">{errors.questions.message}</p>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            {editing ? 'Save changes' : 'Create exam'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Exam row
// ---------------------------------------------------------------------------
function ExamRow({
  exam,
  canManage,
  onEdit,
  onDelete,
  onStatus,
  statusPending,
}: {
  exam: ExamResponse;
  canManage: boolean;
  onEdit: () => void;
  onDelete: () => void;
  onStatus: (status: ExamStatus) => void;
  statusPending: boolean;
}) {
  const marksMatch = Math.abs(exam.questionMarksTotal - exam.totalMarks) < 0.001;

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-base font-semibold text-gray-900">{exam.title}</h3>
            <Badge variant="info">{EXAM_TYPE_LABELS[exam.examType]}</Badge>
            <Badge variant={STATUS_VARIANT[exam.status]}>{exam.status}</Badge>
          </div>
          <p className="mt-1 flex items-center gap-1.5 text-sm text-gray-500">
            <Calendar size={13} />
            {formatDate(exam.examDate)}
            <span className="text-gray-300">·</span>
            {exam.totalMarks} marks
            <span className="text-gray-300">·</span>
            {exam.questions.length} question{exam.questions.length !== 1 ? 's' : ''}
          </p>
          <p className="mt-1 text-xs text-gray-400">{STATUS_HINT[exam.status]}</p>
          {!marksMatch && (
            <p className="mt-1 flex items-center gap-1 text-xs text-amber-600">
              <AlertTriangle size={12} />
              Questions total {exam.questionMarksTotal} but the exam is out of {exam.totalMarks}.
            </p>
          )}
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <div className="mr-2 text-right">
            <p className="text-lg font-semibold text-gray-900">
              {exam.resultsRecorded}
              <span className="text-sm font-normal text-gray-400">/{exam.rosterSize}</span>
            </p>
            <p className="text-xs text-gray-400">marked</p>
          </div>

          <Link to={`/exams/${exam.pscId}/${exam.id}`}>
            <Button size="sm">
              <ScanLine size={14} />
              Marks
            </Button>
          </Link>

          {canManage && (
            <>
              {exam.status === 'DRAFT' && (
                <Button size="sm" variant="secondary" loading={statusPending} onClick={() => onStatus('OPEN')}>
                  <Unlock size={14} />
                  Open
                </Button>
              )}
              {exam.status === 'OPEN' && (
                <Button size="sm" variant="secondary" loading={statusPending} onClick={() => onStatus('LOCKED')}>
                  <Lock size={14} />
                  Lock
                </Button>
              )}
              {exam.status === 'LOCKED' && (
                <Button size="sm" variant="ghost" loading={statusPending} onClick={() => onStatus('OPEN')}>
                  <Unlock size={14} />
                  Unlock
                </Button>
              )}
              <Button size="sm" variant="ghost" aria-label="Edit exam" onClick={onEdit}>
                <Pencil size={14} />
              </Button>
              <Button size="sm" variant="ghost" aria-label="Delete exam" onClick={onDelete}>
                <Trash2 size={14} className="text-red-500" />
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------
export default function CourseExamsPage() {
  const { pscId } = useParams<{ pscId: UUID }>();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<ExamResponse | null>(null);

  const canManage = user?.role === 'ADMIN' || user?.role === 'TEACHER' || user?.role === 'ASSISTANT';

  const offeringQ = useQuery({
    queryKey: ['offerings', pscId],
    queryFn: () => api.get<OfferingSummaryResponse>(`/offerings/${pscId}`).then((r) => r.data),
    enabled: !!pscId,
  });

  const examsQ = useQuery({
    queryKey: ['offerings', pscId, 'exams'],
    queryFn: () => api.get<ExamResponse[]>(`/offerings/${pscId}/exams`).then((r) => r.data),
    enabled: !!pscId,
  });

  const statusMutation = useMutation({
    mutationFn: ({ examId, status }: { examId: UUID; status: ExamStatus }) =>
      api.patch<ExamResponse>(`/exams/${examId}/status`, { status }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'exams'] });
      toast.success('Exam status updated');
    },
    onError: (error) => toast.error(parseApiError(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: (examId: UUID) => api.delete(`/exams/${examId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'exams'] });
      toast.success('Exam deleted');
    },
    onError: (error) => toast.error(parseApiError(error)),
  });

  if (examsQ.isError) {
    return <QueryError error={examsQ.error} onRetry={() => examsQ.refetch()} />;
  }

  const exams = examsQ.data ?? [];

  return (
    <div className="space-y-6">
      <div>
        <Link
          to="/exams"
          className="mb-2 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
        >
          <ChevronLeft size={15} />
          All courses
        </Link>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">
              {offeringQ.data?.courseCode ?? 'Exams'}
            </h1>
            <p className="mt-1 text-sm text-gray-500">{offeringQ.data?.courseName}</p>
          </div>
          {canManage && (
            <Button
              onClick={() => {
                setEditing(null);
                setShowForm(true);
              }}
            >
              <Plus size={15} />
              New exam
            </Button>
          )}
        </div>
      </div>

      {examsQ.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      ) : exams.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-center">
          <ScanLine size={40} className="mb-3 text-gray-300" />
          <p className="text-sm text-gray-400">No exams set up for this course yet.</p>
          {canManage && (
            <p className="mt-1 text-xs text-gray-400">
              Create an exam and list its questions, then scan the marked copies.
            </p>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {exams.map((exam) => (
            <ExamRow
              key={exam.id}
              exam={exam}
              canManage={canManage}
              statusPending={statusMutation.isPending}
              onEdit={() => {
                setEditing(exam);
                setShowForm(true);
              }}
              onDelete={() => {
                if (
                  window.confirm(
                    `Delete "${exam.title}"? This cannot be undone.`,
                  )
                ) {
                  deleteMutation.mutate(exam.id);
                }
              }}
              onStatus={(status) => statusMutation.mutate({ examId: exam.id, status })}
            />
          ))}
        </div>
      )}

      {showForm && pscId && (
        <ExamFormModal pscId={pscId} editing={editing} onClose={() => setShowForm(false)} />
      )}
    </div>
  );
}
