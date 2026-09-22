import { useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ChevronLeft,
  Camera,
  AlertTriangle,
  CheckCircle2,
  Trash2,
  Keyboard,
  RefreshCw,
  User,
  Loader2,
} from 'lucide-react';
import api from '@/lib/api';
import { toast } from '@/components/ui/Toast';
import { parseApiError } from '@/lib/apiError';
import { formatDate, formatDateTime } from '@/lib/datetime';
import { uploadFile, validateUpload, presignedUrlByKey } from '@/lib/files';
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
  ExamResultResponse,
  ExamRosterEntryResponse,
  ScanReviewResponse,
  ScanSummaryResponse,
  QuestionMarkEntryBody,
} from '@/types/api';

/** Images only, and small enough that a phone photo goes through unresized. */
const ACCEPTED_IMAGES = ['.jpg', '.jpeg', '.png', '.webp'];

// ---------------------------------------------------------------------------
// The captured page, shown beside the marks read from it
// ---------------------------------------------------------------------------
function ScanImage({ imageKey }: { imageKey: string }) {
  const urlQ = useQuery({
    queryKey: ['files', 'url-by-key', imageKey],
    queryFn: () => presignedUrlByKey(imageKey),
    // Pre-signed URLs expire; refetching on mount is cheaper than a broken image.
    staleTime: 5 * 60 * 1000,
  });

  if (urlQ.isLoading) {
    return (
      <div className="flex h-64 items-center justify-center rounded-lg bg-gray-50">
        <Spinner />
      </div>
    );
  }
  if (urlQ.isError || !urlQ.data) {
    return (
      <div className="flex h-64 items-center justify-center rounded-lg bg-gray-50 text-sm text-gray-400">
        Could not load the captured page.
      </div>
    );
  }
  return (
    <a href={urlQ.data} target="_blank" rel="noopener noreferrer">
      <img
        src={urlQ.data}
        alt="The captured exam page"
        className="max-h-[70vh] w-full rounded-lg border border-gray-200 object-contain"
      />
    </a>
  );
}

// ---------------------------------------------------------------------------
// Review and confirm a scanned copy
// ---------------------------------------------------------------------------
interface ReviewModalProps {
  exam: ExamResponse;
  scan: ScanReviewResponse;
  roster: ExamRosterEntryResponse[];
  onClose: () => void;
}

function ReviewModal({ exam, scan, roster, onClose }: ReviewModalProps) {
  const queryClient = useQueryClient();

  const [studentId, setStudentId] = useState<string>(scan.matchedStudentId ?? '');
  // Marks are held as strings so an empty box stays empty ("not attempted")
  // rather than collapsing to 0 the moment it is touched.
  const [marks, setMarks] = useState<Record<UUID, string>>(() =>
    Object.fromEntries(
      scan.proposedMarks.map((m) => [
        m.questionId,
        m.marksObtained === null ? '' : String(m.marksObtained),
      ]),
    ),
  );
  const [remarks, setRemarks] = useState('');

  const total = scan.proposedMarks.reduce((sum, m) => {
    const value = marks[m.questionId];
    return sum + (value === '' || value === undefined ? 0 : Number(value) || 0);
  }, 0);

  const overMax = scan.proposedMarks.filter((m) => {
    const value = marks[m.questionId];
    return value !== '' && value !== undefined && Number(value) > m.maxMarks;
  });

  const blanks = scan.proposedMarks.filter(
    (m) => marks[m.questionId] === '' || marks[m.questionId] === undefined,
  );

  const confirmMutation = useMutation({
    mutationFn: () => {
      const body = {
        studentId,
        remarks: remarks.trim() || undefined,
        marks: scan.proposedMarks.map<QuestionMarkEntryBody>((m) => ({
          questionId: m.questionId,
          marksObtained:
            marks[m.questionId] === '' || marks[m.questionId] === undefined
              ? null
              : Number(marks[m.questionId]),
        })),
      };
      return api
        .post<ExamResultResponse>(`/exam-scans/${scan.id}/confirm`, body)
        .then((r) => r.data);
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['exams', exam.id] });
      toast.success(`Marks recorded for ${result.studentName ?? 'the student'}`);
      onClose();
    },
    onError: (error) => toast.error(parseApiError(error)),
  });

  return (
    <Modal open onClose={onClose} title="Review scanned copy" maxWidth="max-w-5xl">
      <div className="grid gap-5 lg:grid-cols-2">
        {/* ── The page itself ─────────────────────────────────────────── */}
        <div>
          <ScanImage imageKey={scan.imageKey} />
          <p className="mt-2 text-xs text-gray-400">
            Check every value against the page. Nothing is recorded until you confirm.
          </p>
        </div>

        {/* ── What was read ───────────────────────────────────────────── */}
        <div className="space-y-4">
          {scan.warnings.length > 0 && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3">
              <p className="flex items-center gap-1.5 text-sm font-semibold text-amber-800">
                <AlertTriangle size={14} />
                Worth checking
              </p>
              <ul className="mt-1.5 space-y-1 text-xs text-amber-700">
                {scan.warnings.map((w, i) => (
                  <li key={i}>· {w}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="rounded-lg bg-gray-50 p-3 text-xs text-gray-600">
            <p className="mb-1.5 font-semibold text-gray-700">Read from the page</p>
            <dl className="grid grid-cols-2 gap-x-3 gap-y-1">
              <dt className="text-gray-500">Roll number</dt>
              <dd>{scan.readRollNumber ?? '—'}</dd>
              <dt className="text-gray-500">Name</dt>
              <dd>{scan.readStudentName ?? '—'}</dd>
              <dt className="text-gray-500">Course</dt>
              <dd>{scan.readCourseCode ?? '—'}</dd>
              <dt className="text-gray-500">Date</dt>
              <dd>{scan.readExamDate ? formatDate(scan.readExamDate) : '—'}</dd>
              {scan.readWrittenTotal != null && (
                <>
                  <dt className="text-gray-500">Total on page</dt>
                  <dd>{scan.readWrittenTotal}</dd>
                </>
              )}
            </dl>
          </div>

          {/* Student */}
          <div className="flex flex-col gap-1">
            <label htmlFor="student" className="text-sm font-medium text-gray-700">
              Student
            </label>
            <select
              id="student"
              value={studentId}
              onChange={(e) => setStudentId(e.target.value)}
              className="h-9 w-full rounded-lg border border-gray-300 px-3 text-sm shadow-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="">Select the student…</option>
              {roster.map((s) => (
                <option key={s.studentId} value={s.studentId}>
                  {s.studentNumber ? `${s.studentNumber} — ` : ''}
                  {s.name}
                  {s.hasResult ? ' (already marked)' : ''}
                </option>
              ))}
            </select>
            {scan.matchedStudentId && (
              <p className="text-xs text-gray-500">
                Matched from roll number {scan.readRollNumber}.
              </p>
            )}
          </div>

          {/* Marks */}
          <div>
            <p className="mb-2 text-sm font-medium text-gray-700">Marks</p>
            <div className="space-y-1.5">
              {scan.proposedMarks.map((m) => {
                const value = marks[m.questionId] ?? '';
                const isOver = value !== '' && Number(value) > m.maxMarks;
                return (
                  <div key={m.questionId} className="flex items-center gap-2">
                    <span className="w-16 shrink-0 text-sm text-gray-600">Q{m.questionNo}</span>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      max={m.maxMarks}
                      value={value}
                      aria-label={`Marks for question ${m.questionNo}`}
                      onChange={(e) =>
                        setMarks((prev) => ({ ...prev, [m.questionId]: e.target.value }))
                      }
                      className={[
                        'h-8 w-24 rounded-lg border px-2 text-sm shadow-sm focus:outline-none focus:ring-2',
                        isOver
                          ? 'border-red-400 focus:border-red-400 focus:ring-red-400'
                          : m.unread
                            ? 'border-amber-300 bg-amber-50 focus:border-primary-500 focus:ring-primary-500'
                            : 'border-gray-300 focus:border-primary-500 focus:ring-primary-500',
                      ].join(' ')}
                    />
                    <span className="text-xs text-gray-400">/ {m.maxMarks}</span>
                    {m.unread && (
                      <span className="text-xs text-amber-600">not read — enter it yourself</span>
                    )}
                  </div>
                );
              })}
            </div>

            <div className="mt-3 flex items-center justify-between border-t border-gray-100 pt-2 text-sm">
              <span className="text-gray-500">Total</span>
              <span className="font-semibold text-gray-900">
                {total.toFixed(2)} / {exam.totalMarks}
              </span>
            </div>
            {scan.readWrittenTotal != null &&
              Math.abs(scan.readWrittenTotal - total) > 0.001 && (
                <p className="mt-1 text-xs text-amber-600">
                  The page has {scan.readWrittenTotal} written as the total.
                </p>
              )}
            {blanks.length > 0 && (
              <p className="mt-1 text-xs text-gray-500">
                {blanks.length} question{blanks.length !== 1 ? 's' : ''} left blank will be
                recorded as not attempted.
              </p>
            )}
          </div>

          <Input
            label="Remarks (optional)"
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            placeholder="Anything worth noting about this copy"
          />

          <div className="flex justify-end gap-2 pt-1">
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button
              loading={confirmMutation.isPending}
              disabled={!studentId || overMax.length > 0}
              onClick={() => confirmMutation.mutate()}
            >
              <CheckCircle2 size={15} />
              Confirm and record
            </Button>
          </div>
          {overMax.length > 0 && (
            <p className="text-right text-xs text-red-600">
              Question {overMax.map((m) => m.questionNo).join(', ')} is above its maximum.
            </p>
          )}
          {!studentId && (
            <p className="text-right text-xs text-gray-500">Select a student to continue.</p>
          )}
        </div>
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Manual mark entry, for copies that are not scanned
// ---------------------------------------------------------------------------
function ManualEntryModal({
  exam,
  roster,
  onClose,
}: {
  exam: ExamResponse;
  roster: ExamRosterEntryResponse[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [studentId, setStudentId] = useState('');
  const [marks, setMarks] = useState<Record<UUID, string>>({});
  const [remarks, setRemarks] = useState('');

  const total = exam.questions.reduce((sum, q) => {
    const value = marks[q.id];
    return sum + (value === '' || value === undefined ? 0 : Number(value) || 0);
  }, 0);

  const overMax = exam.questions.filter((q) => {
    const value = marks[q.id];
    return value !== '' && value !== undefined && Number(value) > q.maxMarks;
  });

  const mutation = useMutation({
    mutationFn: () =>
      api
        .post<ExamResultResponse>(`/exams/${exam.id}/results`, {
          studentId,
          remarks: remarks.trim() || undefined,
          marks: exam.questions.map<QuestionMarkEntryBody>((q) => ({
            questionId: q.id,
            marksObtained:
              marks[q.id] === '' || marks[q.id] === undefined ? null : Number(marks[q.id]),
          })),
        })
        .then((r) => r.data),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['exams', exam.id] });
      toast.success(`Marks recorded for ${result.studentName ?? 'the student'}`);
      onClose();
    },
    onError: (error) => toast.error(parseApiError(error)),
  });

  return (
    <Modal open onClose={onClose} title="Enter marks manually" maxWidth="max-w-xl">
      <div className="space-y-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="manual-student" className="text-sm font-medium text-gray-700">
            Student
          </label>
          <select
            id="manual-student"
            value={studentId}
            onChange={(e) => setStudentId(e.target.value)}
            className="h-9 w-full rounded-lg border border-gray-300 px-3 text-sm shadow-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
            <option value="">Select the student…</option>
            {roster.map((s) => (
              <option key={s.studentId} value={s.studentId}>
                {s.studentNumber ? `${s.studentNumber} — ` : ''}
                {s.name}
                {s.hasResult ? ' (already marked)' : ''}
              </option>
            ))}
          </select>
        </div>

        <div>
          <p className="mb-2 text-sm font-medium text-gray-700">Marks</p>
          <div className="space-y-1.5">
            {exam.questions.map((q) => (
              <div key={q.id} className="flex items-center gap-2">
                <span className="w-16 shrink-0 text-sm text-gray-600">Q{q.questionNo}</span>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  max={q.maxMarks}
                  value={marks[q.id] ?? ''}
                  aria-label={`Marks for question ${q.questionNo}`}
                  onChange={(e) => setMarks((prev) => ({ ...prev, [q.id]: e.target.value }))}
                  className="h-8 w-24 rounded-lg border border-gray-300 px-2 text-sm shadow-sm focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
                />
                <span className="text-xs text-gray-400">/ {q.maxMarks}</span>
              </div>
            ))}
          </div>
          <div className="mt-3 flex items-center justify-between border-t border-gray-100 pt-2 text-sm">
            <span className="text-gray-500">Total</span>
            <span className="font-semibold text-gray-900">
              {total.toFixed(2)} / {exam.totalMarks}
            </span>
          </div>
        </div>

        <Input
          label="Remarks (optional)"
          value={remarks}
          onChange={(e) => setRemarks(e.target.value)}
        />

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            loading={mutation.isPending}
            disabled={!studentId || overMax.length > 0}
            onClick={() => mutation.mutate()}
          >
            Record marks
          </Button>
        </div>
        {overMax.length > 0 && (
          <p className="text-right text-xs text-red-600">
            Question {overMax.map((q) => q.questionNo).join(', ')} is above its maximum.
          </p>
        )}
      </div>
    </Modal>
  );
}

// ---------------------------------------------------------------------------
// Capture panel
// ---------------------------------------------------------------------------
function CapturePanel({
  exam,
  onScanned,
}: {
  exam: ExamResponse;
  onScanned: (scan: ScanReviewResponse) => void;
}) {
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [stage, setStage] = useState<'idle' | 'uploading' | 'reading'>('idle');

  const disabled = exam.status !== 'OPEN';

  async function handleFile(file: File) {
    const problem = validateUpload(file, ACCEPTED_IMAGES);
    if (problem) {
      toast.error(problem);
      return;
    }
    try {
      setStage('uploading');
      // Stored first, so a failed or slow read never loses the photograph.
      const uploaded = await uploadFile(file, {
        context: 'exam-scans',
        contextId: exam.id,
      });

      setStage('reading');
      const { data: scan } = await api.post<ScanReviewResponse>('/exam-scans', {
        imageKey: uploaded.objectKey,
        imageName: uploaded.originalName,
        imageSize: uploaded.fileSize,
        examId: exam.id,
      });

      queryClient.invalidateQueries({ queryKey: ['exams', exam.id, 'scans'] });
      if (scan.status === 'FAILED') {
        toast.error(scan.errorMessage ?? 'That page could not be read.');
      } else {
        onScanned(scan);
      }
    } catch (error) {
      toast.error(parseApiError(error));
    } finally {
      setStage('idle');
      if (inputRef.current) inputRef.current.value = '';
    }
  }

  return (
    <div className="rounded-xl border border-dashed border-gray-300 bg-white p-6 text-center">
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        // Opens the rear camera on a phone and the file picker on a desktop.
        capture="environment"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void handleFile(file);
        }}
      />

      <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-primary-50 text-primary-600">
        {stage === 'idle' ? <Camera size={22} /> : <Loader2 size={22} className="animate-spin" />}
      </div>

      {stage === 'idle' ? (
        <>
          <p className="text-sm font-medium text-gray-900">Capture a marked copy</p>
          <p className="mt-1 text-xs text-gray-500">
            Photograph the first page — the marks table, roll number, name, course code and
            date. You review everything before it is recorded.
          </p>
          <Button
            className="mt-4"
            disabled={disabled}
            onClick={() => inputRef.current?.click()}
          >
            <Camera size={15} />
            Capture page
          </Button>
          {disabled && (
            <p className="mt-2 text-xs text-amber-600">
              {exam.status === 'DRAFT'
                ? 'Open the exam before scanning copies.'
                : 'This exam is locked; unlock it to record more marks.'}
            </p>
          )}
        </>
      ) : (
        <p className="text-sm text-gray-600">
          {stage === 'uploading' ? 'Uploading the page…' : 'Reading the marks table…'}
        </p>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Page
// ---------------------------------------------------------------------------
export default function ExamMarksPage() {
  const { pscId, examId } = useParams<{ pscId: UUID; examId: UUID }>();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const [reviewing, setReviewing] = useState<ScanReviewResponse | null>(null);
  const [manualOpen, setManualOpen] = useState(false);

  const canManage =
    user?.role === 'ADMIN' || user?.role === 'TEACHER' || user?.role === 'ASSISTANT';

  const examQ = useQuery({
    queryKey: ['exams', examId],
    queryFn: () => api.get<ExamResponse>(`/exams/${examId}`).then((r) => r.data),
    enabled: !!examId,
  });

  const rosterQ = useQuery({
    queryKey: ['exams', examId, 'roster'],
    queryFn: () =>
      api.get<ExamRosterEntryResponse[]>(`/exams/${examId}/roster`).then((r) => r.data),
    enabled: !!examId && canManage,
  });

  const resultsQ = useQuery({
    queryKey: ['exams', examId, 'results'],
    queryFn: () =>
      api.get<ExamResultResponse[]>(`/exams/${examId}/results`).then((r) => r.data),
    enabled: !!examId && canManage,
  });

  const scansQ = useQuery({
    queryKey: ['exams', examId, 'scans'],
    queryFn: () => api.get<ScanSummaryResponse[]>(`/exams/${examId}/scans`).then((r) => r.data),
    enabled: !!examId && canManage,
  });

  const openScanMutation = useMutation({
    mutationFn: (scanId: UUID) =>
      api.get<ScanReviewResponse>(`/exam-scans/${scanId}`).then((r) => r.data),
    onSuccess: (scan) => setReviewing(scan),
    onError: (error) => toast.error(parseApiError(error)),
  });

  const discardMutation = useMutation({
    mutationFn: (scanId: UUID) => api.delete(`/exam-scans/${scanId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['exams', examId, 'scans'] });
      toast.success('Scan discarded');
    },
    onError: (error) => toast.error(parseApiError(error)),
  });

  const deleteResultMutation = useMutation({
    mutationFn: (studentId: UUID) => api.delete(`/exams/${examId}/results/${studentId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['exams', examId] });
      toast.success('Marks removed');
    },
    onError: (error) => toast.error(parseApiError(error)),
  });

  if (examQ.isError) {
    return <QueryError error={examQ.error} onRetry={() => examQ.refetch()} />;
  }
  if (examQ.isLoading || !examQ.data) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    );
  }

  const exam = examQ.data;
  const roster = rosterQ.data ?? [];
  const results = resultsQ.data ?? [];
  const pendingScans = (scansQ.data ?? []).filter(
    (s) => s.status === 'EXTRACTED' || s.status === 'FAILED',
  );

  return (
    <div className="space-y-6">
      {/* ── Header ──────────────────────────────────────────────────────── */}
      <div>
        <Link
          to={`/exams/${pscId}`}
          className="mb-2 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
        >
          <ChevronLeft size={15} />
          {exam.courseCode ?? 'Exams'}
        </Link>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-bold text-gray-900">{exam.title}</h1>
              <Badge variant="info">{EXAM_TYPE_LABELS[exam.examType]}</Badge>
              <Badge variant={exam.status === 'OPEN' ? 'success' : 'default'}>{exam.status}</Badge>
            </div>
            <p className="mt-1 text-sm text-gray-500">
              {formatDate(exam.examDate)} · {exam.totalMarks} marks ·{' '}
              {exam.resultsRecorded}/{exam.rosterSize} marked
            </p>
          </div>
          {canManage && (
            <Button variant="secondary" onClick={() => setManualOpen(true)} disabled={exam.status !== 'OPEN'}>
              <Keyboard size={15} />
              Enter manually
            </Button>
          )}
        </div>
      </div>

      {!canManage ? (
        <StudentResultView examId={exam.id} studentId={user!.id} />
      ) : (
        <>
          <CapturePanel exam={exam} onScanned={setReviewing} />

          {/* ── Scans awaiting review ─────────────────────────────────── */}
          {pendingScans.length > 0 && (
            <div>
              <h2 className="mb-2 text-sm font-semibold text-gray-900">
                Awaiting review ({pendingScans.length})
              </h2>
              <div className="space-y-2">
                {pendingScans.map((scan) => (
                  <div
                    key={scan.id}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-gray-200 bg-white p-3"
                  >
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-gray-900">
                        {scan.matchedStudentName ??
                          scan.readStudentName ??
                          scan.readRollNumber ??
                          'Unidentified copy'}
                      </p>
                      <p className="text-xs text-gray-500">
                        {scan.readRollNumber ?? 'no roll number read'} ·{' '}
                        {formatDateTime(scan.createdAt)}
                      </p>
                      {scan.status === 'FAILED' && (
                        <p className="mt-0.5 flex items-center gap-1 text-xs text-red-600">
                          <AlertTriangle size={12} />
                          {scan.errorMessage ?? 'Could not be read'}
                        </p>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      {scan.warningCount > 0 && scan.status !== 'FAILED' && (
                        <Badge variant="warning">
                          {scan.warningCount} to check
                        </Badge>
                      )}
                      {scan.status === 'EXTRACTED' && (
                        <Button
                          size="sm"
                          loading={openScanMutation.isPending}
                          onClick={() => openScanMutation.mutate(scan.id)}
                        >
                          Review
                        </Button>
                      )}
                      <Button
                        size="sm"
                        variant="ghost"
                        aria-label="Discard scan"
                        onClick={() => discardMutation.mutate(scan.id)}
                      >
                        <Trash2 size={14} className="text-red-500" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Recorded marks ────────────────────────────────────────── */}
          <div>
            <div className="mb-2 flex items-center justify-between">
              <h2 className="text-sm font-semibold text-gray-900">
                Recorded marks ({results.length})
              </h2>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => resultsQ.refetch()}
                aria-label="Refresh recorded marks"
              >
                <RefreshCw size={13} />
              </Button>
            </div>

            {resultsQ.isLoading ? (
              <div className="flex justify-center py-10">
                <Spinner />
              </div>
            ) : results.length === 0 ? (
              <div className="rounded-lg border border-gray-200 bg-white py-10 text-center">
                <User size={32} className="mx-auto mb-2 text-gray-300" />
                <p className="text-sm text-gray-400">No marks recorded yet.</p>
              </div>
            ) : (
              <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
                <table className="w-full text-sm">
                  <thead className="border-b border-gray-200 bg-gray-50 text-left text-xs uppercase text-gray-500">
                    <tr>
                      <th className="px-4 py-2 font-medium">Roll number</th>
                      <th className="px-4 py-2 font-medium">Student</th>
                      {exam.questions.map((q) => (
                        <th key={q.id} className="px-2 py-2 text-center font-medium">
                          Q{q.questionNo}
                        </th>
                      ))}
                      <th className="px-4 py-2 text-right font-medium">Total</th>
                      <th className="px-4 py-2 font-medium">Source</th>
                      <th className="px-4 py-2" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {results.map((result) => {
                      const byQuestion = new Map(
                        result.marks.map((m) => [m.questionId, m.marksObtained]),
                      );
                      return (
                        <tr key={result.id} className="hover:bg-gray-50">
                          <td className="px-4 py-2 text-gray-600">
                            {result.studentNumber ?? '—'}
                          </td>
                          <td className="px-4 py-2 font-medium text-gray-900">
                            {result.studentName ?? '—'}
                          </td>
                          {exam.questions.map((q) => {
                            const mark = byQuestion.get(q.id);
                            return (
                              <td key={q.id} className="px-2 py-2 text-center text-gray-600">
                                {mark === null || mark === undefined ? (
                                  <span className="text-gray-300" title="Not attempted">
                                    —
                                  </span>
                                ) : (
                                  mark
                                )}
                              </td>
                            );
                          })}
                          <td className="px-4 py-2 text-right font-semibold text-gray-900">
                            {result.totalObtained}
                            {result.percentage != null && (
                              <span className="ml-1 text-xs font-normal text-gray-400">
                                ({result.percentage}%)
                              </span>
                            )}
                          </td>
                          <td className="px-4 py-2">
                            <Badge variant={result.source === 'SCAN' ? 'purple' : 'default'}>
                              {result.source === 'SCAN' ? 'Scanned' : 'Manual'}
                            </Badge>
                          </td>
                          <td className="px-4 py-2 text-right">
                            {exam.status === 'OPEN' && (
                              <Button
                                size="sm"
                                variant="ghost"
                                aria-label={`Remove marks for ${result.studentName}`}
                                onClick={() => {
                                  if (
                                    window.confirm(
                                      `Remove the recorded marks for ${result.studentName}?`,
                                    )
                                  ) {
                                    deleteResultMutation.mutate(result.studentId);
                                  }
                                }}
                              >
                                <Trash2 size={13} className="text-red-500" />
                              </Button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}

      {reviewing && (
        <ReviewModal
          exam={exam}
          scan={reviewing}
          roster={roster}
          onClose={() => setReviewing(null)}
        />
      )}
      {manualOpen && (
        <ManualEntryModal exam={exam} roster={roster} onClose={() => setManualOpen(false)} />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Student view — their own marks only
// ---------------------------------------------------------------------------
function StudentResultView({ examId, studentId }: { examId: UUID; studentId: UUID }) {
  const resultQ = useQuery({
    queryKey: ['exams', examId, 'results', studentId],
    queryFn: () =>
      api
        .get<ExamResultResponse>(`/exams/${examId}/results/${studentId}`)
        .then((r) => r.data),
    retry: false,
  });

  if (resultQ.isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    );
  }
  if (resultQ.isError || !resultQ.data) {
    return (
      <div className="rounded-lg border border-gray-200 bg-white py-12 text-center">
        <p className="text-sm text-gray-400">Your marks for this exam are not published yet.</p>
      </div>
    );
  }

  const result = resultQ.data;
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-6">
      <div className="mb-4 flex items-end justify-between">
        <div>
          <p className="text-sm text-gray-500">Your total</p>
          <p className="text-3xl font-bold text-gray-900">
            {result.totalObtained}
            <span className="text-lg font-normal text-gray-400"> / {result.totalMarks}</span>
          </p>
        </div>
        {result.percentage != null && (
          <Badge variant={result.percentage >= 50 ? 'success' : 'danger'}>
            {result.percentage}%
          </Badge>
        )}
      </div>
      <table className="w-full text-sm">
        <thead className="border-b border-gray-200 text-left text-xs uppercase text-gray-500">
          <tr>
            <th className="py-2 font-medium">Question</th>
            <th className="py-2 text-right font-medium">Marks</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {result.marks.map((m) => (
            <tr key={m.questionId}>
              <td className="py-2 text-gray-600">Q{m.questionNo}</td>
              <td className="py-2 text-right text-gray-900">
                {m.marksObtained === null ? (
                  <span className="text-gray-400">Not attempted</span>
                ) : (
                  `${m.marksObtained} / ${m.maxMarks}`
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {result.remarks && (
        <p className="mt-4 rounded-lg bg-gray-50 p-3 text-sm text-gray-600">{result.remarks}</p>
      )}
    </div>
  );
}
