import { useState, useEffect, useRef } from 'react';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, useFieldArray, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  ChevronLeft,
  Plus,
  Pencil,
  Trash2,
  ClipboardCheck,
  HelpCircle,
  ChevronDown,
  ChevronUp,
  Clock,
  Calendar,
  FileText,
  Upload,
  X,
} from 'lucide-react';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import Modal from '@/components/ui/Modal';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Input from '@/components/ui/Input';
import Spinner from '@/components/ui/Spinner';
import type {
  UUID,
  OfferingSummaryResponse,
  AssignmentResponse,
  AssignmentSubmissionResponse,
  QuizResponse,
  QuizQuestionResponse,
  QuizSubmissionResponse,
  SubmissionType,
  UploadedFileResponse,
} from '@/types/api';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function fmt(dt: string) {
  return new Date(dt).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function toLocalDatetimeValue(iso?: string): string {
  if (!iso) return '';
  return iso.slice(0, 16);
}

function isPastDue(dueDate: string): boolean {
  return new Date(dueDate) < new Date();
}

const SUBMISSION_TYPE_LABELS: Record<SubmissionType, string> = {
  FILE: 'File',
  TEXT: 'Text',
  BOTH: 'File + Text',
};

// ============================================================================
// ASSIGNMENT SECTION
// ============================================================================

// ---------------------------------------------------------------------------
// Create/Edit Assignment modal
// ---------------------------------------------------------------------------
const assignmentSchema = z.object({
  title: z.string().min(1, 'Required').max(255),
  description: z.string().optional(),
  submissionType: z.enum(['FILE', 'TEXT', 'BOTH']),
  totalMarks: z.coerce.number().min(0.01, 'Must be > 0'),
  dueDate: z.string().min(1, 'Required'),
  allowLateSubmission: z.boolean().optional(),
  latePenaltyPercent: z.coerce.number().min(0).max(100).optional().or(z.literal('')),
});
type AssignmentFormData = z.infer<typeof assignmentSchema>;

interface AssignmentFormModalProps {
  pscId: UUID;
  editing?: AssignmentResponse | null;
  onClose: () => void;
}

function AssignmentFormModal({ pscId, editing, onClose }: AssignmentFormModalProps) {
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AssignmentFormData>({
    resolver: zodResolver(assignmentSchema),
    defaultValues: editing
      ? {
          title: editing.title,
          description: editing.description ?? '',
          submissionType: editing.submissionType,
          totalMarks: editing.totalMarks,
          dueDate: toLocalDatetimeValue(editing.dueDate),
          allowLateSubmission: editing.allowLateSubmission,
          latePenaltyPercent: editing.latePenaltyPercent ?? '',
        }
      : {
          submissionType: 'FILE',
          allowLateSubmission: false,
          latePenaltyPercent: '',
        },
  });

  const mutation = useMutation({
    mutationFn: (data: AssignmentFormData) => {
      const body = {
        ...data,
        dueDate: new Date(data.dueDate).toISOString(),
        latePenaltyPercent: data.latePenaltyPercent === '' ? undefined : data.latePenaltyPercent,
      };
      if (editing) {
        return api.put<AssignmentResponse>(`/assignments/${editing.id}`, body).then((r) => r.data);
      }
      return api
        .post<AssignmentResponse>(`/offerings/${pscId}/assignments`, { ...body, pscId })
        .then((r) => r.data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'assignments'] });
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      title={editing ? 'Edit Assignment' : 'Create Assignment'}
      maxWidth="max-w-xl"
    >
      <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-4">
        <Input label="Title" error={errors.title?.message} {...register('title')} />
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
          <textarea
            {...register('description')}
            rows={3}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            placeholder="Optional instructions…"
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Submission Type</label>
            <select
              {...register('submissionType')}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
            >
              <option value="FILE">File upload</option>
              <option value="TEXT">Text entry</option>
              <option value="BOTH">File + Text</option>
            </select>
          </div>
          <Input
            label="Total Marks"
            type="number"
            step="0.5"
            error={errors.totalMarks?.message}
            {...register('totalMarks')}
          />
        </div>
        <Input
          label="Due Date & Time"
          type="datetime-local"
          error={errors.dueDate?.message}
          {...register('dueDate')}
        />
        <div className="flex items-center gap-3">
          <input type="checkbox" id="late" {...register('allowLateSubmission')} />
          <label htmlFor="late" className="text-sm text-gray-700">
            Allow late submission
          </label>
          <Input
            label="Late penalty (%)"
            type="number"
            step="1"
            className="ml-auto w-32"
            error={errors.latePenaltyPercent?.message}
            {...register('latePenaltyPercent')}
          />
        </div>
        {mutation.error && (
          <p className="text-xs text-red-600">Failed to save. Please try again.</p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            {editing ? 'Save Changes' : 'Create Assignment'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Submit Assignment modal (student)
// ---------------------------------------------------------------------------
interface SubmitAssignmentModalProps {
  assignment: AssignmentResponse;
  onClose: () => void;
}

function SubmitAssignmentModal({ assignment, onClose }: SubmitAssignmentModalProps) {
  const queryClient = useQueryClient();
  const [text, setText] = useState('');
  const [uploadedFile, setUploadedFile] = useState<{
    key: string;
    name: string;
    size: number;
  } | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const needsText = assignment.submissionType === 'TEXT' || assignment.submissionType === 'BOTH';
  const needsFile = assignment.submissionType === 'FILE' || assignment.submissionType === 'BOTH';

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadError('');
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await api.post<UploadedFileResponse>('/files', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setUploadedFile({
        key: res.data.objectKey,
        name: res.data.originalName,
        size: res.data.fileSize,
      });
    } catch {
      setUploadError('Upload failed. Please try again.');
    } finally {
      setUploading(false);
    }
  }

  const mutation = useMutation({
    mutationFn: () =>
      api
        .post<AssignmentSubmissionResponse>(`/assignments/${assignment.id}/submit`, {
          textContent: needsText ? text : undefined,
          fileKey: uploadedFile?.key,
          fileName: uploadedFile?.name,
          fileSize: uploadedFile?.size,
        })
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['me', 'assignments', assignment.id, 'submission'],
      });
      onClose();
    },
  });

  const canSubmit =
    (!needsText || text.trim().length > 0) && (!needsFile || !!uploadedFile);

  return (
    <Modal open onClose={onClose} title={`Submit: ${assignment.title}`} maxWidth="max-w-xl">
      <div className="space-y-4">
        <div className="rounded-lg bg-gray-50 p-3 text-sm text-gray-600">
          <p>
            <span className="font-medium">Due:</span> {fmt(assignment.dueDate)}
          </p>
          <p>
            <span className="font-medium">Marks:</span> {assignment.totalMarks}
          </p>
          <p>
            <span className="font-medium">Submission type:</span>{' '}
            {SUBMISSION_TYPE_LABELS[assignment.submissionType]}
          </p>
        </div>

        {needsText && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Your Answer
            </label>
            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              rows={6}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              placeholder="Write your answer here…"
            />
          </div>
        )}

        {needsFile && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Attach File
            </label>
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              onChange={handleFileChange}
            />
            {uploadedFile ? (
              <div className="flex items-center gap-2 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm">
                <FileText size={16} className="text-green-600" />
                <span className="flex-1 truncate text-green-700">{uploadedFile.name}</span>
                <button
                  onClick={() => setUploadedFile(null)}
                  className="text-gray-400 hover:text-red-500"
                >
                  <X size={14} />
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading}
                className="flex w-full items-center justify-center gap-2 rounded-lg border-2 border-dashed border-gray-300 p-4 text-sm text-gray-500 hover:border-primary-400 hover:text-primary-600 disabled:opacity-50"
              >
                {uploading ? (
                  <Spinner size="sm" />
                ) : (
                  <>
                    <Upload size={16} />
                    Click to upload a file
                  </>
                )}
              </button>
            )}
            {uploadError && <p className="mt-1 text-xs text-red-600">{uploadError}</p>}
          </div>
        )}

        {mutation.error && (
          <p className="text-xs text-red-600">Submission failed. Please try again.</p>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            onClick={() => mutation.mutate()}
            loading={mutation.isPending}
            disabled={!canSubmit}
          >
            Submit Assignment
          </Button>
        </div>
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Grade Submission modal (teacher)
// ---------------------------------------------------------------------------
interface GradeModalProps {
  submission: AssignmentSubmissionResponse;
  totalMarks: number;
  onClose: () => void;
}

function GradeModal({ submission, totalMarks, onClose }: GradeModalProps) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, formState: { errors } } = useForm<{
    marksObtained: number;
    feedback: string;
  }>({
    defaultValues: {
      marksObtained: submission.marksObtained ?? 0,
      feedback: submission.feedback ?? '',
    },
  });

  const mutation = useMutation({
    mutationFn: (data: { marksObtained: number; feedback: string }) =>
      api
        .post<AssignmentSubmissionResponse>(
          `/submissions/assignments/${submission.id}/grade`,
          data,
        )
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['assignments', submission.assignmentId, 'submissions'],
      });
      onClose();
    },
  });

  return (
    <Modal open onClose={onClose} title="Grade Submission">
      <form
        onSubmit={handleSubmit((d) =>
          mutation.mutate({ marksObtained: Number(d.marksObtained), feedback: d.feedback }),
        )}
        className="space-y-4"
      >
        {submission.textContent && (
          <div>
            <p className="mb-1 text-xs font-medium uppercase tracking-wide text-gray-400">
              Student's Answer
            </p>
            <div className="max-h-40 overflow-y-auto rounded-lg bg-gray-50 p-3 text-sm text-gray-700">
              {submission.textContent}
            </div>
          </div>
        )}
        {submission.fileName && (
          <p className="text-sm text-gray-600">
            <span className="font-medium">File:</span> {submission.fileName}
          </p>
        )}
        <Input
          label={`Marks obtained (out of ${totalMarks})`}
          type="number"
          step="0.5"
          min="0"
          max={totalMarks}
          error={errors.marksObtained?.message}
          {...register('marksObtained', { required: 'Required', valueAsNumber: true })}
        />
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Feedback</label>
          <textarea
            {...register('feedback')}
            rows={3}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
            placeholder="Optional feedback…"
          />
        </div>
        {mutation.error && (
          <p className="text-xs text-red-600">Grading failed. Please try again.</p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            Save Grade
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Submissions list (teacher view — expanded per assignment)
// ---------------------------------------------------------------------------
function SubmissionsList({
  assignment,
  onClose,
}: {
  assignment: AssignmentResponse;
  onClose: () => void;
}) {
  const [gradingSubmission, setGradingSubmission] =
    useState<AssignmentSubmissionResponse | null>(null);

  const submissionsQ = useQuery({
    queryKey: ['assignments', assignment.id, 'submissions'],
    queryFn: () =>
      api
        .get<AssignmentSubmissionResponse[]>(`/assignments/${assignment.id}/submissions`)
        .then((r) => r.data),
  });
  const submissions = submissionsQ.data ?? [];

  return (
    <Modal
      open
      onClose={onClose}
      title={`Submissions: ${assignment.title}`}
      maxWidth="max-w-2xl"
    >
      {submissionsQ.isLoading ? (
        <div className="flex justify-center py-8">
          <Spinner />
        </div>
      ) : submissions.length === 0 ? (
        <p className="py-6 text-center text-sm text-gray-400">No submissions yet.</p>
      ) : (
        <div className="divide-y divide-gray-100">
          {submissions.map((s) => (
            <div key={s.id} className="flex items-start justify-between gap-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="text-xs font-mono text-gray-500">{s.studentId.slice(0, 12)}…</p>
                {s.submittedAt && (
                  <p className="mt-0.5 text-xs text-gray-400">
                    Submitted {fmt(s.submittedAt)}
                  </p>
                )}
                {s.fileName && (
                  <p className="mt-0.5 text-xs text-gray-400">File: {s.fileName}</p>
                )}
              </div>
              <div className="flex flex-shrink-0 items-center gap-2">
                {s.status === 'GRADED' ? (
                  <span className="text-sm font-semibold text-green-700">
                    {s.marksObtained} / {assignment.totalMarks}
                  </span>
                ) : (
                  <Badge variant="warning">Pending</Badge>
                )}
                <Button size="sm" variant="secondary" onClick={() => setGradingSubmission(s)}>
                  {s.status === 'GRADED' ? 'Re-grade' : 'Grade'}
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
      {gradingSubmission && (
        <GradeModal
          submission={gradingSubmission}
          totalMarks={assignment.totalMarks}
          onClose={() => setGradingSubmission(null)}
        />
      )}
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Assignment card
// ---------------------------------------------------------------------------
interface AssignmentCardProps {
  assignment: AssignmentResponse;
  isTeacher: boolean;
  pscId: UUID;
  highlighted?: boolean;
  onEdit: (a: AssignmentResponse) => void;
}

function AssignmentCard({
  assignment,
  isTeacher,
  pscId,
  highlighted,
  onEdit,
}: AssignmentCardProps) {
  const queryClient = useQueryClient();
  const [showSubmissions, setShowSubmissions] = useState(false);
  const [showSubmitModal, setShowSubmitModal] = useState(false);

  // Student: fetch own submission status
  const mySubmissionQ = useQuery({
    queryKey: ['me', 'assignments', assignment.id, 'submission'],
    queryFn: () =>
      api
        .get<AssignmentSubmissionResponse>(`/me/assignments/${assignment.id}/submission`)
        .then((r) => r.data),
    enabled: !isTeacher,
    retry: false,
  });

  const deleteMutation = useMutation({
    mutationFn: () => api.delete(`/assignments/${assignment.id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'assignments'] });
    },
  });

  const overdue = isPastDue(assignment.dueDate);
  const mySubmission = mySubmissionQ.data;

  return (
    <div
      className={`rounded-xl border bg-white p-4 shadow-sm transition-all ${
        highlighted ? 'border-primary-400 ring-2 ring-primary-100' : 'border-gray-200'
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 min-w-0 flex-1">
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-green-50 text-green-600">
            <ClipboardCheck size={18} />
          </div>
          <div className="min-w-0">
            <p className="font-semibold text-gray-900">{assignment.title}</p>
            {assignment.description && (
              <p className="mt-0.5 text-sm text-gray-500 line-clamp-2">
                {assignment.description}
              </p>
            )}
            <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-500">
              <span className="flex items-center gap-1">
                <Calendar size={12} />
                Due: {fmt(assignment.dueDate)}
              </span>
              <span className="text-gray-300">·</span>
              <span>{assignment.totalMarks} marks</span>
              <Badge variant="default">{SUBMISSION_TYPE_LABELS[assignment.submissionType]}</Badge>
              {overdue && <Badge variant="danger">Overdue</Badge>}
            </div>
          </div>
        </div>

        <div className="flex flex-shrink-0 items-center gap-1">
          {isTeacher ? (
            <>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setShowSubmissions(true)}
              >
                Submissions
              </Button>
              <button
                onClick={() => onEdit(assignment)}
                className="rounded p-1.5 text-gray-400 hover:text-gray-700"
              >
                <Pencil size={15} />
              </button>
              <button
                onClick={() => {
                  if (confirm('Delete this assignment?')) deleteMutation.mutate();
                }}
                className="rounded p-1.5 text-gray-400 hover:text-red-500"
              >
                <Trash2 size={15} />
              </button>
            </>
          ) : (
            <>
              {mySubmissionQ.isLoading ? (
                <Spinner size="sm" />
              ) : mySubmission ? (
                <div className="text-right">
                  {mySubmission.status === 'GRADED' ? (
                    <span className="text-sm font-bold text-green-700">
                      {mySubmission.marksObtained}/{assignment.totalMarks}
                    </span>
                  ) : (
                    <Badge variant="info">Submitted</Badge>
                  )}
                </div>
              ) : !overdue || assignment.allowLateSubmission ? (
                <Button size="sm" onClick={() => setShowSubmitModal(true)}>
                  Submit
                </Button>
              ) : (
                <Badge variant="danger">Closed</Badge>
              )}
            </>
          )}
        </div>
      </div>

      {showSubmissions && (
        <SubmissionsList assignment={assignment} onClose={() => setShowSubmissions(false)} />
      )}
      {showSubmitModal && (
        <SubmitAssignmentModal
          assignment={assignment}
          onClose={() => setShowSubmitModal(false)}
        />
      )}
    </div>
  );
}

// ============================================================================
// QUIZ SECTION
// ============================================================================

// ---------------------------------------------------------------------------
// Create/Edit Quiz modal
// ---------------------------------------------------------------------------
const quizSchema = z.object({
  title: z.string().min(1, 'Required').max(255),
  description: z.string().optional(),
  durationMinutes: z.coerce.number().int().positive().optional().or(z.literal('')),
  availableFrom: z.string().optional(),
  availableUntil: z.string().optional(),
  shuffleQuestions: z.boolean().optional(),
  shuffleOptions: z.boolean().optional(),
});
type QuizFormData = z.infer<typeof quizSchema>;

interface QuizFormModalProps {
  pscId: UUID;
  editing?: QuizResponse | null;
  onClose: () => void;
}

function QuizFormModal({ pscId, editing, onClose }: QuizFormModalProps) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, formState: { errors } } = useForm<QuizFormData>({
    resolver: zodResolver(quizSchema),
    defaultValues: editing
      ? {
          title: editing.title,
          description: editing.description ?? '',
          durationMinutes: editing.durationMinutes ?? '',
          availableFrom: toLocalDatetimeValue(editing.availableFrom),
          availableUntil: toLocalDatetimeValue(editing.availableUntil),
          shuffleQuestions: editing.shuffleQuestions,
          shuffleOptions: editing.shuffleOptions,
        }
      : { shuffleQuestions: false, shuffleOptions: false },
  });

  const mutation = useMutation({
    mutationFn: (data: QuizFormData) => {
      const body = {
        ...data,
        durationMinutes: data.durationMinutes === '' ? undefined : data.durationMinutes,
        availableFrom: data.availableFrom
          ? new Date(data.availableFrom).toISOString()
          : undefined,
        availableUntil: data.availableUntil
          ? new Date(data.availableUntil).toISOString()
          : undefined,
      };
      if (editing) {
        return api.put<QuizResponse>(`/quizzes/${editing.id}`, body).then((r) => r.data);
      }
      return api
        .post<QuizResponse>(`/offerings/${pscId}/quizzes`, { ...body, pscId })
        .then((r) => r.data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'quizzes'] });
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      title={editing ? 'Edit Quiz' : 'Create Quiz'}
      maxWidth="max-w-xl"
    >
      <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-4">
        <Input label="Title" error={errors.title?.message} {...register('title')} />
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
          <textarea
            {...register('description')}
            rows={2}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
            placeholder="Optional description…"
          />
        </div>
        <Input
          label="Duration (minutes, leave empty for unlimited)"
          type="number"
          min="1"
          error={errors.durationMinutes?.message}
          {...register('durationMinutes')}
        />
        <div className="grid grid-cols-2 gap-3">
          <Input
            label="Available From"
            type="datetime-local"
            {...register('availableFrom')}
          />
          <Input
            label="Available Until"
            type="datetime-local"
            {...register('availableUntil')}
          />
        </div>
        <div className="flex gap-4 text-sm text-gray-700">
          <label className="flex items-center gap-2">
            <input type="checkbox" {...register('shuffleQuestions')} />
            Shuffle questions
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" {...register('shuffleOptions')} />
            Shuffle options
          </label>
        </div>
        {mutation.error && <p className="text-xs text-red-600">Failed to save.</p>}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            {editing ? 'Save Changes' : 'Create Quiz'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Question builder modal (teacher)
// ---------------------------------------------------------------------------
const questionSchema = z.object({
  questionText: z.string().min(1, 'Required'),
  type: z.enum(['MCQ', 'MSQ']),
  options: z
    .array(z.object({ id: z.string().min(1), text: z.string().min(1, 'Required') }))
    .min(2, 'At least 2 options required'),
  correctAnswer: z.array(z.string()).min(1, 'Select at least one correct answer'),
  marks: z.coerce.number().min(0.01, 'Must be > 0'),
  orderIndex: z.coerce.number().int().min(0).optional().or(z.literal('')),
  explanation: z.string().optional(),
});
type QuestionFormData = z.infer<typeof questionSchema>;

interface QuestionFormModalProps {
  quizId: UUID;
  editing?: QuizQuestionResponse | null;
  nextOrderIndex: number;
  onClose: () => void;
}

function QuestionFormModal({ quizId, editing, nextOrderIndex, onClose }: QuestionFormModalProps) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, watch, control, setValue, formState: { errors } } =
    useForm<QuestionFormData>({
      resolver: zodResolver(questionSchema),
      defaultValues: editing
        ? {
            questionText: editing.questionText,
            type: editing.type,
            options: editing.options,
            correctAnswer: editing.correctAnswer ?? [],
            marks: editing.marks,
            orderIndex: editing.orderIndex,
            explanation: editing.explanation ?? '',
          }
        : {
            type: 'MCQ',
            options: [
              { id: 'a', text: '' },
              { id: 'b', text: '' },
            ],
            correctAnswer: [],
            orderIndex: nextOrderIndex,
          },
    });

  const { fields, append, remove } = useFieldArray({ control, name: 'options' });
  const qType = watch('type');
  const correctAnswer = watch('correctAnswer');

  function toggleCorrect(optId: string) {
    if (qType === 'MCQ') {
      setValue('correctAnswer', [optId]);
    } else {
      const cur = correctAnswer ?? [];
      setValue(
        'correctAnswer',
        cur.includes(optId) ? cur.filter((x) => x !== optId) : [...cur, optId],
      );
    }
  }

  const mutation = useMutation({
    mutationFn: (data: QuestionFormData) => {
      const body = {
        ...data,
        orderIndex:
          data.orderIndex === '' ? nextOrderIndex : Number(data.orderIndex),
      };
      if (editing) {
        return api
          .put<QuizQuestionResponse>(`/quiz-questions/${editing.id}`, body)
          .then((r) => r.data);
      }
      return api
        .post<QuizQuestionResponse>(`/quizzes/${quizId}/questions`, body)
        .then((r) => r.data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['quizzes', quizId, 'questions'] });
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      title={editing ? 'Edit Question' : 'Add Question'}
      maxWidth="max-w-xl"
    >
      <form onSubmit={handleSubmit((d) => mutation.mutate(d))} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Question</label>
          <textarea
            {...register('questionText')}
            rows={3}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
          />
          {errors.questionText && (
            <p className="mt-1 text-xs text-red-600">{errors.questionText.message}</p>
          )}
        </div>

        <div className="flex items-center gap-4 text-sm">
          <label className="font-medium text-gray-700">Type:</label>
          <label className="flex items-center gap-1.5">
            <input type="radio" value="MCQ" {...register('type')} />
            Multiple Choice (1 answer)
          </label>
          <label className="flex items-center gap-1.5">
            <input type="radio" value="MSQ" {...register('type')} />
            Multi-Select (many answers)
          </label>
        </div>

        <div>
          <div className="mb-2 flex items-center justify-between">
            <label className="text-sm font-medium text-gray-700">
              Options{' '}
              <span className="text-xs font-normal text-gray-400">
                (click option ID to mark as correct)
              </span>
            </label>
            <button
              type="button"
              onClick={() => append({ id: String.fromCharCode(97 + fields.length), text: '' })}
              className="text-xs font-medium text-primary-600 hover:text-primary-800"
            >
              + Add option
            </button>
          </div>
          {errors.options && (
            <p className="mb-1 text-xs text-red-600">{errors.options.message as string}</p>
          )}
          {fields.map((field, idx) => {
            const isCorrect = (correctAnswer ?? []).includes(field.id);
            return (
              <div key={field.id} className="mb-2 flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => toggleCorrect(field.id)}
                  className={`h-7 w-7 flex-shrink-0 rounded-full border-2 text-xs font-bold transition-colors ${
                    isCorrect
                      ? 'border-green-500 bg-green-500 text-white'
                      : 'border-gray-300 text-gray-400 hover:border-green-400'
                  }`}
                >
                  {field.id.toUpperCase()}
                </button>
                <Controller
                  name={`options.${idx}.text`}
                  control={control}
                  render={({ field: f }) => (
                    <input
                      {...f}
                      placeholder={`Option ${field.id.toUpperCase()}`}
                      className="flex-1 rounded-lg border border-gray-300 px-2.5 py-1.5 text-sm focus:border-primary-500 focus:outline-none"
                    />
                  )}
                />
                {fields.length > 2 && (
                  <button
                    type="button"
                    onClick={() => remove(idx)}
                    className="text-gray-400 hover:text-red-500"
                  >
                    <X size={14} />
                  </button>
                )}
              </div>
            );
          })}
          {errors.correctAnswer && (
            <p className="mt-1 text-xs text-red-600">{errors.correctAnswer.message as string}</p>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Input
            label="Marks"
            type="number"
            step="0.5"
            error={errors.marks?.message}
            {...register('marks')}
          />
          <Input
            label="Order index"
            type="number"
            min="0"
            {...register('orderIndex')}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Explanation (shown after submit)
          </label>
          <input
            {...register('explanation')}
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none"
            placeholder="Optional…"
          />
        </div>

        {mutation.error && <p className="text-xs text-red-600">Failed to save question.</p>}
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
            {editing ? 'Save Changes' : 'Add Question'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Questions panel (teacher — inline expandable)
// ---------------------------------------------------------------------------
function QuestionsPanel({ quiz }: { quiz: QuizResponse }) {
  const queryClient = useQueryClient();
  const [editingQuestion, setEditingQuestion] = useState<QuizQuestionResponse | null>(null);
  const [showAddQuestion, setShowAddQuestion] = useState(false);

  const questionsQ = useQuery({
    queryKey: ['quizzes', quiz.id, 'questions'],
    queryFn: () =>
      api
        .get<QuizQuestionResponse[]>(`/quizzes/${quiz.id}/questions`)
        .then((r) => r.data),
  });
  const questions = (questionsQ.data ?? []).slice().sort((a, b) => a.orderIndex - b.orderIndex);

  const deleteMutation = useMutation({
    mutationFn: (id: UUID) => api.delete(`/quiz-questions/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['quizzes', quiz.id, 'questions'] });
    },
  });

  const totalMarks = questions.reduce((sum, q) => sum + q.marks, 0);

  return (
    <div className="mt-3 rounded-lg border border-gray-200 bg-gray-50 p-4">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700">
          {questions.length} question{questions.length !== 1 ? 's' : ''} · {totalMarks} total marks
        </span>
        <Button size="sm" onClick={() => setShowAddQuestion(true)}>
          <Plus size={14} className="mr-1" />
          Add Question
        </Button>
      </div>

      {questionsQ.isLoading ? (
        <div className="flex justify-center py-4">
          <Spinner />
        </div>
      ) : questions.length === 0 ? (
        <p className="py-3 text-center text-sm text-gray-400">
          No questions yet. Add the first one.
        </p>
      ) : (
        <div className="space-y-2">
          {questions.map((q, idx) => (
            <div
              key={q.id}
              className="flex items-start justify-between rounded-lg border border-gray-200 bg-white p-3 text-sm"
            >
              <div className="flex-1 min-w-0">
                <span className="font-medium text-gray-500 mr-2">{idx + 1}.</span>
                <span className="text-gray-900">{q.questionText}</span>
                <div className="mt-1 flex flex-wrap gap-1">
                  <Badge variant={q.type === 'MCQ' ? 'info' : 'warning'}>{q.type}</Badge>
                  <span className="text-xs text-gray-400">{q.marks} marks</span>
                  <span className="text-xs text-gray-300">·</span>
                  <span className="text-xs text-green-600">
                    Correct: {q.correctAnswer?.join(', ')}
                  </span>
                </div>
              </div>
              <div className="ml-2 flex flex-shrink-0 items-center gap-1">
                <button
                  onClick={() => setEditingQuestion(q)}
                  className="rounded p-1 text-gray-400 hover:text-gray-700"
                >
                  <Pencil size={13} />
                </button>
                <button
                  onClick={() => {
                    if (confirm('Delete this question?')) deleteMutation.mutate(q.id);
                  }}
                  className="rounded p-1 text-gray-400 hover:text-red-500"
                >
                  <Trash2 size={13} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {(showAddQuestion || editingQuestion) && (
        <QuestionFormModal
          quizId={quiz.id}
          editing={editingQuestion}
          nextOrderIndex={questions.length}
          onClose={() => {
            setShowAddQuestion(false);
            setEditingQuestion(null);
          }}
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Quiz Player (student — modal)
// ---------------------------------------------------------------------------
interface QuizPlayerProps {
  quiz: QuizResponse;
  submission: QuizSubmissionResponse;
  onClose: () => void;
}

function QuizPlayer({ quiz, submission, onClose }: QuizPlayerProps) {
  const queryClient = useQueryClient();
  const [answers, setAnswers] = useState<Record<string, string[]>>(submission.answers ?? {});
  const [timeLeft, setTimeLeft] = useState<number | null>(() => {
    if (!quiz.durationMinutes) return null;
    const elapsed = (Date.now() - new Date(submission.startedAt).getTime()) / 1000;
    return Math.max(0, quiz.durationMinutes * 60 - elapsed);
  });

  const questionsQ = useQuery({
    queryKey: ['quizzes', quiz.id, 'questions'],
    queryFn: () =>
      api.get<QuizQuestionResponse[]>(`/quizzes/${quiz.id}/questions`).then((r) => r.data),
  });
  const questions = (questionsQ.data ?? []).slice().sort((a, b) => a.orderIndex - b.orderIndex);

  // Timer countdown
  useEffect(() => {
    if (timeLeft === null) return;
    if (timeLeft <= 0) {
      submitMutation.mutate();
      return;
    }
    const id = setInterval(() => setTimeLeft((t) => (t !== null ? t - 1 : null)), 1000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [timeLeft]);

  const saveMutation = useMutation({
    mutationFn: () =>
      api
        .put<QuizSubmissionResponse>(`/quiz-submissions/${submission.id}/answers`, { answers })
        .then((r) => r.data),
  });

  const submitMutation = useMutation({
    mutationFn: () =>
      api
        .post<QuizSubmissionResponse>(`/quiz-submissions/${submission.id}/submit`)
        .then((r) => r.data),
    onSuccess: (data) => {
      queryClient.setQueryData(['me', 'quizzes', quiz.id, 'submission'], data);
      onClose();
    },
  });

  function toggleAnswer(questionId: UUID, optId: string, type: 'MCQ' | 'MSQ') {
    setAnswers((prev) => {
      const cur = prev[questionId] ?? [];
      if (type === 'MCQ') return { ...prev, [questionId]: [optId] };
      return {
        ...prev,
        [questionId]: cur.includes(optId)
          ? cur.filter((x) => x !== optId)
          : [...cur, optId],
      };
    });
  }

  const answered = questions.filter((q) => (answers[q.id] ?? []).length > 0).length;

  return (
    <Modal open onClose={() => {}} title={quiz.title} maxWidth="max-w-2xl">
      {/* Header */}
      <div className="mb-4 flex items-center justify-between">
        <span className="text-sm text-gray-500">
          {answered}/{questions.length} answered
        </span>
        {timeLeft !== null && (
          <span
            className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold ${
              timeLeft < 120
                ? 'bg-red-50 text-red-700'
                : 'bg-amber-50 text-amber-700'
            }`}
          >
            <Clock size={14} />
            {Math.floor(timeLeft / 60)}:{String(Math.floor(timeLeft % 60)).padStart(2, '0')}
          </span>
        )}
      </div>

      {questionsQ.isLoading ? (
        <div className="flex justify-center py-8">
          <Spinner />
        </div>
      ) : (
        <div className="max-h-[55vh] space-y-5 overflow-y-auto pr-2">
          {questions.map((q, idx) => {
            const selected = answers[q.id] ?? [];
            return (
              <div key={q.id} className="rounded-lg border border-gray-200 p-4">
                <div className="mb-3 flex items-start gap-2">
                  <span className="mt-0.5 flex-shrink-0 rounded-full bg-primary-100 px-2 py-0.5 text-xs font-bold text-primary-700">
                    Q{idx + 1}
                  </span>
                  <p className="text-sm font-medium text-gray-900">{q.questionText}</p>
                  <span className="ml-auto flex-shrink-0 text-xs text-gray-400">
                    {q.marks} mark{q.marks !== 1 ? 's' : ''}
                  </span>
                </div>
                <div className="space-y-2">
                  {q.options.map((opt) => {
                    const isSelected = selected.includes(opt.id);
                    return (
                      <button
                        key={opt.id}
                        type="button"
                        onClick={() => toggleAnswer(q.id, opt.id, q.type)}
                        className={`flex w-full items-center gap-3 rounded-lg border-2 px-3 py-2.5 text-left text-sm transition-colors ${
                          isSelected
                            ? 'border-primary-500 bg-primary-50 text-primary-800'
                            : 'border-gray-200 text-gray-700 hover:border-primary-300 hover:bg-gray-50'
                        }`}
                      >
                        <span
                          className={`flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full border-2 text-xs font-bold ${
                            isSelected
                              ? 'border-primary-500 bg-primary-500 text-white'
                              : 'border-gray-300 text-gray-400'
                          }`}
                        >
                          {opt.id.toUpperCase()}
                        </span>
                        {opt.text}
                      </button>
                    );
                  })}
                </div>
                {q.type === 'MSQ' && (
                  <p className="mt-2 text-xs text-gray-400">Select all that apply</p>
                )}
              </div>
            );
          })}
        </div>
      )}

      <div className="mt-4 flex items-center justify-between border-t border-gray-100 pt-4">
        <Button
          variant="secondary"
          size="sm"
          onClick={() => saveMutation.mutate()}
          loading={saveMutation.isPending}
        >
          Save Progress
        </Button>
        <div className="flex gap-2">
          <Button
            variant="secondary"
            onClick={() => {
              saveMutation.mutate();
              onClose();
            }}
          >
            Save & Exit
          </Button>
          <Button
            variant="primary"
            onClick={() => {
              if (
                confirm(
                  `Submit quiz? You've answered ${answered}/${questions.length} questions.`,
                )
              ) {
                submitMutation.mutate();
              }
            }}
            loading={submitMutation.isPending}
          >
            Submit Quiz
          </Button>
        </div>
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Quiz card
// ---------------------------------------------------------------------------
interface QuizCardProps {
  quiz: QuizResponse;
  isTeacher: boolean;
  pscId: UUID;
  highlighted?: boolean;
  onEdit: (q: QuizResponse) => void;
}

function QuizCard({ quiz, isTeacher, pscId, highlighted, onEdit }: QuizCardProps) {
  const queryClient = useQueryClient();
  const [showQuestions, setShowQuestions] = useState(false);
  const [showSubmissions, setShowSubmissions] = useState(false);
  const [showPlayer, setShowPlayer] = useState(false);

  const mySubmissionQ = useQuery({
    queryKey: ['me', 'quizzes', quiz.id, 'submission'],
    queryFn: () =>
      api
        .get<QuizSubmissionResponse>(`/me/quizzes/${quiz.id}/submission`)
        .then((r) => r.data),
    enabled: !isTeacher,
    retry: false,
  });

  const teacherSubmissionsQ = useQuery({
    queryKey: ['quizzes', quiz.id, 'submissions'],
    queryFn: () =>
      api
        .get<QuizSubmissionResponse[]>(`/quizzes/${quiz.id}/submissions`)
        .then((r) => r.data),
    enabled: isTeacher && showSubmissions,
  });

  const deleteMutation = useMutation({
    mutationFn: () => api.delete(`/quizzes/${quiz.id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'quizzes'] });
    },
  });

  const startMutation = useMutation({
    mutationFn: () =>
      api.post<QuizSubmissionResponse>(`/quizzes/${quiz.id}/start`).then((r) => r.data),
    onSuccess: (data) => {
      queryClient.setQueryData(['me', 'quizzes', quiz.id, 'submission'], data);
      setShowPlayer(true);
    },
  });

  const now = new Date();
  const isWithinWindow =
    (!quiz.availableFrom || new Date(quiz.availableFrom) <= now) &&
    (!quiz.availableUntil || new Date(quiz.availableUntil) >= now);

  const mySubmission = mySubmissionQ.data;
  const isSubmitted = !!mySubmission?.submittedAt;
  const inProgress = mySubmission && !isSubmitted;

  return (
    <div
      className={`rounded-xl border bg-white p-4 shadow-sm transition-all ${
        highlighted ? 'border-primary-400 ring-2 ring-primary-100' : 'border-gray-200'
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 min-w-0 flex-1">
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-purple-50 text-purple-600">
            <HelpCircle size={18} />
          </div>
          <div className="min-w-0">
            <p className="font-semibold text-gray-900">{quiz.title}</p>
            {quiz.description && (
              <p className="mt-0.5 text-sm text-gray-500 line-clamp-2">{quiz.description}</p>
            )}
            <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-500">
              {quiz.durationMinutes && (
                <span className="flex items-center gap-1">
                  <Clock size={12} />
                  {quiz.durationMinutes} min
                </span>
              )}
              {quiz.availableFrom && (
                <span className="flex items-center gap-1">
                  <Calendar size={12} />
                  {fmt(quiz.availableFrom)} – {quiz.availableUntil ? fmt(quiz.availableUntil) : 'open'}
                </span>
              )}
              {!isTeacher && !isWithinWindow && (
                <Badge variant="danger">Closed</Badge>
              )}
            </div>
          </div>
        </div>

        <div className="flex flex-shrink-0 items-center gap-1">
          {isTeacher ? (
            <>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setShowQuestions((v) => !v)}
              >
                Questions
                {showQuestions ? <ChevronUp size={13} className="ml-1" /> : <ChevronDown size={13} className="ml-1" />}
              </Button>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setShowSubmissions((v) => !v)}
              >
                Submissions
              </Button>
              <button
                onClick={() => onEdit(quiz)}
                className="rounded p-1.5 text-gray-400 hover:text-gray-700"
              >
                <Pencil size={15} />
              </button>
              <button
                onClick={() => {
                  if (confirm('Delete this quiz?')) deleteMutation.mutate();
                }}
                className="rounded p-1.5 text-gray-400 hover:text-red-500"
              >
                <Trash2 size={15} />
              </button>
            </>
          ) : (
            <>
              {mySubmissionQ.isLoading ? (
                <Spinner size="sm" />
              ) : isSubmitted ? (
                <div className="text-right">
                  {mySubmission?.score !== undefined ? (
                    <span className="text-sm font-bold text-green-700">
                      Score: {mySubmission.score}
                    </span>
                  ) : (
                    <Badge variant="info">Submitted</Badge>
                  )}
                </div>
              ) : inProgress ? (
                <Button size="sm" onClick={() => setShowPlayer(true)}>
                  Continue
                </Button>
              ) : isWithinWindow ? (
                <Button
                  size="sm"
                  loading={startMutation.isPending}
                  onClick={() => startMutation.mutate()}
                >
                  Start Quiz
                </Button>
              ) : (
                <Badge variant="default">Not available</Badge>
              )}
            </>
          )}
        </div>
      </div>

      {/* Questions panel (teacher) */}
      {isTeacher && showQuestions && <QuestionsPanel quiz={quiz} />}

      {/* Submissions panel (teacher) */}
      {isTeacher && showSubmissions && (
        <div className="mt-3 rounded-lg border border-gray-200 bg-gray-50 p-4">
          {teacherSubmissionsQ.isLoading ? (
            <div className="flex justify-center py-4">
              <Spinner />
            </div>
          ) : (teacherSubmissionsQ.data ?? []).length === 0 ? (
            <p className="text-center text-sm text-gray-400">No quiz submissions yet.</p>
          ) : (
            <div className="divide-y divide-gray-100">
              {(teacherSubmissionsQ.data ?? []).map((s) => (
                <div key={s.id} className="flex items-center justify-between py-2 text-sm">
                  <span className="font-mono text-xs text-gray-500">
                    {s.studentId.slice(0, 12)}…
                  </span>
                  <div className="flex items-center gap-3">
                    {s.submittedAt ? (
                      <span className="text-xs text-gray-400">{fmt(s.submittedAt)}</span>
                    ) : (
                      <Badge variant="warning">In progress</Badge>
                    )}
                    {s.score !== undefined ? (
                      <span className="font-semibold text-green-700">{s.score} pts</span>
                    ) : s.submittedAt ? (
                      <Badge variant="info">Awaiting grade</Badge>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Quiz player (student) */}
      {showPlayer && mySubmission && (
        <QuizPlayer
          quiz={quiz}
          submission={mySubmission}
          onClose={() => setShowPlayer(false)}
        />
      )}
    </div>
  );
}

// ============================================================================
// COURSE ASSESSMENT PAGE
// ============================================================================
export default function CourseAssessmentPage() {
  const { pscId } = useParams<{ pscId: string }>();
  const [searchParams] = useSearchParams();
  const user = useAuthStore((s) => s.user);

  const highlightAssignment = searchParams.get('assignment');
  const highlightQuiz = searchParams.get('quiz');

  const isTeacher =
    user?.role === 'TEACHER' || user?.role === 'ASSISTANT' || user?.role === 'ADMIN';
  const [activeTab, setActiveTab] = useState<'assignments' | 'quizzes'>(
    highlightQuiz ? 'quizzes' : 'assignments',
  );
  const [editingAssignment, setEditingAssignment] = useState<AssignmentResponse | null>(null);
  const [editingQuiz, setEditingQuiz] = useState<QuizResponse | null>(null);
  const [showCreateAssignment, setShowCreateAssignment] = useState(false);
  const [showCreateQuiz, setShowCreateQuiz] = useState(false);

  const offeringQ = useQuery({
    queryKey: ['offerings', pscId],
    queryFn: () =>
      api.get<OfferingSummaryResponse>(`/offerings/${pscId}`).then((r) => r.data),
    enabled: !!pscId,
  });

  const assignmentsQ = useQuery({
    queryKey: ['offerings', pscId, 'assignments'],
    queryFn: () =>
      api.get<AssignmentResponse[]>(`/offerings/${pscId}/assignments`).then((r) => r.data),
    enabled: !!pscId,
  });

  const quizzesQ = useQuery({
    queryKey: ['offerings', pscId, 'quizzes'],
    queryFn: () =>
      api.get<QuizResponse[]>(`/offerings/${pscId}/quizzes`).then((r) => r.data),
    enabled: !!pscId,
  });

  // Scroll to highlighted item
  const highlightRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (highlightRef.current) {
      setTimeout(
        () => highlightRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }),
        400,
      );
    }
  }, [assignmentsQ.data, quizzesQ.data]);

  if (!pscId) {
    return (
      <div className="py-8 text-center text-sm text-gray-400">No course specified.</div>
    );
  }

  const offering = offeringQ.data;
  const title = offering
    ? `${offering.courseCode} — ${offering.courseName}`
    : '…';
  const assignments = assignmentsQ.data ?? [];
  const quizzes = quizzesQ.data ?? [];

  return (
    <div className="space-y-6">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2">
        <Link
          to="/assessment"
          className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-800"
        >
          <ChevronLeft size={16} />
          Assessments
        </Link>
        <span className="text-gray-300">/</span>
        <span className="truncate text-sm text-gray-600">{title}</span>
      </div>

      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">
          {offeringQ.isLoading ? (
            <span className="inline-block h-7 w-64 animate-pulse rounded bg-gray-200" />
          ) : (
            title
          )}
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          {isTeacher ? 'Manage assignments and quizzes' : 'Assignments and quizzes for this course'}
        </p>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200">
        <nav className="flex gap-6" aria-label="Tabs">
          {(['assignments', 'quizzes'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`border-b-2 pb-3 text-sm font-medium capitalize transition-colors ${
                activeTab === tab
                  ? 'border-primary-600 text-primary-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {tab === 'assignments' ? (
                <span className="flex items-center gap-1.5">
                  <ClipboardCheck size={14} />
                  Assignments ({assignments.length})
                </span>
              ) : (
                <span className="flex items-center gap-1.5">
                  <HelpCircle size={14} />
                  Quizzes ({quizzes.length})
                </span>
              )}
            </button>
          ))}
        </nav>
      </div>

      {/* Assignments tab */}
      {activeTab === 'assignments' && (
        <div className="space-y-4">
          {isTeacher && (
            <div className="flex justify-end">
              <Button onClick={() => setShowCreateAssignment(true)}>
                <Plus size={15} className="mr-1" />
                New Assignment
              </Button>
            </div>
          )}
          {assignmentsQ.isLoading ? (
            <div className="flex justify-center py-12">
              <Spinner />
            </div>
          ) : assignments.length === 0 ? (
            <div className="flex flex-col items-center rounded-xl border border-dashed border-gray-200 py-14 text-center">
              <ClipboardCheck size={36} className="mb-3 text-gray-300" />
              <p className="text-sm text-gray-400">No assignments yet.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {assignments.map((a) => {
                const isHighlighted = a.id === highlightAssignment;
                return (
                  <div key={a.id} ref={isHighlighted ? highlightRef : undefined}>
                    <AssignmentCard
                      assignment={a}
                      isTeacher={isTeacher}
                      pscId={pscId}
                      highlighted={isHighlighted}
                      onEdit={setEditingAssignment}
                    />
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Quizzes tab */}
      {activeTab === 'quizzes' && (
        <div className="space-y-4">
          {isTeacher && (
            <div className="flex justify-end">
              <Button onClick={() => setShowCreateQuiz(true)}>
                <Plus size={15} className="mr-1" />
                New Quiz
              </Button>
            </div>
          )}
          {quizzesQ.isLoading ? (
            <div className="flex justify-center py-12">
              <Spinner />
            </div>
          ) : quizzes.length === 0 ? (
            <div className="flex flex-col items-center rounded-xl border border-dashed border-gray-200 py-14 text-center">
              <HelpCircle size={36} className="mb-3 text-gray-300" />
              <p className="text-sm text-gray-400">No quizzes yet.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {quizzes.map((q) => {
                const isHighlighted = q.id === highlightQuiz;
                return (
                  <div key={q.id} ref={isHighlighted ? highlightRef : undefined}>
                    <QuizCard
                      quiz={q}
                      isTeacher={isTeacher}
                      pscId={pscId}
                      highlighted={isHighlighted}
                      onEdit={setEditingQuiz}
                    />
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Create/Edit modals */}
      {(showCreateAssignment || editingAssignment) && (
        <AssignmentFormModal
          pscId={pscId}
          editing={editingAssignment}
          onClose={() => {
            setShowCreateAssignment(false);
            setEditingAssignment(null);
          }}
        />
      )}
      {(showCreateQuiz || editingQuiz) && (
        <QuizFormModal
          pscId={pscId}
          editing={editingQuiz}
          onClose={() => {
            setShowCreateQuiz(false);
            setEditingQuiz(null);
          }}
        />
      )}
    </div>
  );
}
