import { useState, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient, useQueries } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  ChevronLeft,
  Plus,
  X,
  Lock,
  Unlock,
  CalendarCheck,
  Users,
  BarChart2,
} from 'lucide-react';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import Spinner from '@/components/ui/Spinner';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Input from '@/components/ui/Input';
import type {
  UUID,
  OfferingSummaryResponse,
  SessionResponse,
  AttendanceRecordResponse,
  AttendanceSummaryResponse,
  EnrollmentResponse,
  AttendanceStatus,
} from '@/types/api';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function formatDate(dateStr: string): string {
  // dateStr is "YYYY-MM-DD" from LocalDate
  const [year, month, day] = dateStr.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

function PercentBadge({ pct }: { pct: number }) {
  const variant = pct >= 85 ? 'success' : pct >= 75 ? 'warning' : 'danger';
  return <Badge variant={variant}>{pct.toFixed(1)}%</Badge>;
}

const STATUS_CONFIG: Record<
  AttendanceStatus,
  { label: string; activeClass: string; idleClass: string }
> = {
  PRESENT: {
    label: 'P',
    activeClass: 'bg-green-600 text-white border-green-600',
    idleClass: 'text-green-700 border-green-300 hover:bg-green-50',
  },
  ABSENT: {
    label: 'A',
    activeClass: 'bg-red-600 text-white border-red-600',
    idleClass: 'text-red-700 border-red-300 hover:bg-red-50',
  },
  LATE: {
    label: 'L',
    activeClass: 'bg-amber-500 text-white border-amber-500',
    idleClass: 'text-amber-700 border-amber-300 hover:bg-amber-50',
  },
  EXCUSED: {
    label: 'E',
    activeClass: 'bg-blue-600 text-white border-blue-600',
    idleClass: 'text-blue-700 border-blue-300 hover:bg-blue-50',
  },
};

const ALL_STATUSES: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'LATE', 'EXCUSED'];

// ---------------------------------------------------------------------------
// Session attendance sheet (teacher/admin)
// ---------------------------------------------------------------------------
interface SheetProps {
  session: SessionResponse;
  pscId: UUID;
  isAdmin: boolean;
}

function SessionSheet({ session, pscId }: SheetProps) {
  const queryClient = useQueryClient();

  const enrollmentsQ = useQuery({
    queryKey: ['offerings', pscId, 'enrollments'],
    queryFn: () =>
      api
        .get<EnrollmentResponse[]>(`/offerings/${pscId}/enrollments?status=ACTIVE`)
        .then((r) => r.data),
  });
  // Only STUDENT-role enrollments are tracked for attendance
  const enrollments = (enrollmentsQ.data ?? []).filter((e) => e.courseRole === 'STUDENT');

  const recordsQ = useQuery({
    queryKey: ['sessions', session.id, 'records'],
    queryFn: () =>
      api
        .get<AttendanceRecordResponse[]>(`/sessions/${session.id}/records`)
        .then((r) => r.data),
  });
  const records = recordsQ.data ?? [];

  const recordMap = useMemo(
    () => new Map<UUID, AttendanceRecordResponse>(records.map((r) => [r.studentId, r])),
    [records],
  );

  // Per-student marking loading state
  const [markingId, setMarkingId] = useState<UUID | null>(null);

  const markMutation = useMutation({
    mutationFn: ({ studentId, status }: { studentId: UUID; status: AttendanceStatus }) =>
      api
        .put<AttendanceRecordResponse>(`/sessions/${session.id}/records/${studentId}`, {
          status,
          remarks: '',
        })
        .then((r) => r.data),
    onSuccess: (data) => {
      queryClient.setQueryData(
        ['sessions', session.id, 'records'],
        (old: AttendanceRecordResponse[] = []) => {
          const idx = old.findIndex((r) => r.studentId === data.studentId);
          if (idx >= 0) return [...old.slice(0, idx), data, ...old.slice(idx + 1)];
          return [...old, data];
        },
      );
      setMarkingId(null);
    },
    onError: () => setMarkingId(null),
  });

  const closeMutation = useMutation({
    mutationFn: () =>
      api.post<SessionResponse>(`/sessions/${session.id}/close`).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'sessions'] });
    },
  });

  const markAllPresent = () => {
    const unrecorded = enrollments.filter(
      (e) => !recordMap.has(e.studentId) || recordMap.get(e.studentId)?.status !== 'PRESENT',
    );
    unrecorded.forEach((e) => {
      markMutation.mutate({ studentId: e.studentId, status: 'PRESENT' });
    });
  };

  if (enrollmentsQ.isLoading || recordsQ.isLoading) {
    return (
      <div className="flex justify-center py-8">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="mt-3 rounded-lg border border-gray-200 bg-gray-50 p-4">
      {/* Sheet header */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Users size={16} className="text-gray-500" />
          <span className="text-sm font-medium text-gray-700">
            {enrollments.length} student{enrollments.length !== 1 ? 's' : ''}
          </span>
          {records.length > 0 && (
            <span className="text-xs text-gray-400">· {records.length} marked</span>
          )}
        </div>
        <div className="flex gap-2">
          {session.open && enrollments.length > 0 && (
            <Button
              size="sm"
              variant="secondary"
              onClick={markAllPresent}
              loading={markMutation.isPending}
            >
              Mark all present
            </Button>
          )}
          {session.open && (
            <Button
              size="sm"
              variant="danger"
              loading={closeMutation.isPending}
              onClick={() => closeMutation.mutate()}
            >
              <Lock size={14} className="mr-1" />
              Close session
            </Button>
          )}
        </div>
      </div>

      {enrollments.length === 0 ? (
        <p className="py-4 text-center text-sm text-gray-400">No enrolled students found.</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50">
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">
                  Student
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">
                  Status
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">
                  Current
                </th>
              </tr>
            </thead>
            <tbody>
              {enrollments.map((e) => {
                const record = recordMap.get(e.studentId);
                const displayName = e.studentName ?? `${e.studentId.slice(0, 8)}…`;
                const isMarking = markMutation.isPending && markingId === e.studentId;

                return (
                  <tr key={e.studentId} className="border-b border-gray-50 last:border-0">
                    <td className="px-4 py-2.5">
                      <span
                        className="text-sm text-gray-700"
                      >
                        {displayName}
                      </span>
                    </td>
                    <td className="px-4 py-2.5">
                      <div className="flex gap-1">
                        {ALL_STATUSES.map((s) => {
                          const cfg = STATUS_CONFIG[s];
                          const active = record?.status === s;
                          return (
                            <button
                              key={s}
                              title={s}
                              disabled={!session.open || isMarking}
                              onClick={() => {
                                if (!session.open || isMarking) return;
                                setMarkingId(e.studentId);
                                markMutation.mutate({ studentId: e.studentId, status: s });
                              }}
                              className={`h-7 w-7 rounded border text-xs font-bold transition-colors ${
                                active ? cfg.activeClass : cfg.idleClass
                              } disabled:cursor-not-allowed disabled:opacity-50`}
                            >
                              {cfg.label}
                            </button>
                          );
                        })}
                        {isMarking && <Spinner size="sm" />}
                      </div>
                    </td>
                    <td className="px-4 py-2.5">
                      {record ? (
                        <Badge
                          variant={
                            record.status === 'PRESENT'
                              ? 'success'
                              : record.status === 'ABSENT'
                                ? 'danger'
                                : record.status === 'LATE'
                                  ? 'warning'
                                  : 'info'
                          }
                        >
                          {record.status}
                        </Badge>
                      ) : (
                        <span className="text-xs text-gray-300">—</span>
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
  );
}

// ---------------------------------------------------------------------------
// Create session form (Zod-validated)
// ---------------------------------------------------------------------------
const createSchema = z.object({
  sessionDate: z.string().min(1, 'Date is required'),
  topic: z.string().optional(),
});
type CreateForm = z.infer<typeof createSchema>;

interface CreateSessionFormProps {
  pscId: UUID;
  onClose: () => void;
}

function CreateSessionForm({ pscId, onClose }: CreateSessionFormProps) {
  const queryClient = useQueryClient();

  const today = new Date().toISOString().split('T')[0];

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CreateForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { sessionDate: today, topic: '' },
  });

  const mutation = useMutation({
    mutationFn: (data: CreateForm) =>
      api
        .post<SessionResponse>('/sessions', {
          pscId,
          sessionDate: data.sessionDate,
          topic: data.topic || undefined,
        })
        .then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offerings', pscId, 'sessions'] });
      onClose();
    },
  });

  return (
    <form
      onSubmit={handleSubmit((d) => mutation.mutate(d))}
      className="mb-6 rounded-xl border border-primary-200 bg-primary-50 p-4"
    >
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-primary-800">New Session</h3>
        <button
          type="button"
          onClick={onClose}
          className="text-gray-400 hover:text-gray-600"
        >
          <X size={16} />
        </button>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          label="Session Date"
          type="date"
          error={errors.sessionDate?.message}
          {...register('sessionDate')}
        />
        <Input
          label="Topic (optional)"
          placeholder="e.g. Arrays and Pointers"
          error={errors.topic?.message}
          {...register('topic')}
        />
      </div>
      {mutation.error && (
        <p className="mt-2 text-xs text-red-600">Failed to create session. Please try again.</p>
      )}
      <div className="mt-3 flex justify-end gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" size="sm" loading={mutation.isPending}>
          Create
        </Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------------------
// Class Roster — per-student attendance summary (teacher/admin)
// ---------------------------------------------------------------------------
function ClassRosterView({ pscId }: { pscId: UUID; isAdmin: boolean }) {
  const enrollmentsQ = useQuery({
    queryKey: ['offerings', pscId, 'enrollments'],
    queryFn: () =>
      api
        .get<EnrollmentResponse[]>(`/offerings/${pscId}/enrollments?status=ACTIVE`)
        .then((r) => r.data),
  });
  // Only STUDENT-role enrollments are tracked for attendance
  const enrollments = (enrollmentsQ.data ?? []).filter((e) => e.courseRole === 'STUDENT');

  const summaryQueries = useQueries({
    queries: enrollments.map((e) => ({
      queryKey: ['offerings', pscId, 'attendance', e.studentId],
      queryFn: () =>
        api
          .get<AttendanceSummaryResponse>(`/offerings/${pscId}/attendance/${e.studentId}`)
          .then((r) => r.data),
      enabled: enrollments.length > 0,
      retry: false,
    })),
  });

  if (enrollmentsQ.isLoading) {
    return (
      <div className="flex justify-center py-8">
        <Spinner />
      </div>
    );
  }

  if (enrollments.length === 0) {
    return (
      <div className="flex flex-col items-center rounded-xl border border-dashed border-gray-200 py-14 text-center">
        <Users size={36} className="mb-3 text-gray-300" />
        <p className="text-sm text-gray-400">No enrolled students.</p>
      </div>
    );
  }

  const rows = enrollments.map((e, idx) => ({
    studentId: e.studentId,
    name: e.studentName ?? null,
    summary: summaryQueries[idx]?.data,
    loading: summaryQueries[idx]?.isLoading ?? false,
  }));

  // Sort: lowest attendance % first so at-risk students appear on top
  const sorted = [...rows].sort((a, b) => {
    const pa = a.summary?.percentage ?? 100;
    const pb = b.summary?.percentage ?? 100;
    return pa - pb;
  });

  const atRisk = sorted.filter((r) => r.summary && r.summary.percentage < 75).length;

  return (
    <div className="space-y-4">
      {atRisk > 0 && (
        <div className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-700">
          <BarChart2 size={15} />
          <span>
            <strong>{atRisk}</strong> student{atRisk !== 1 ? 's' : ''} below the 75%
            attendance threshold.
          </span>
        </div>
      )}

      <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 bg-gray-50">
              <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500">
                Student
              </th>
              <th className="px-4 py-2.5 text-center text-xs font-medium text-gray-500">
                Attended
              </th>
              <th className="px-4 py-2.5 text-center text-xs font-medium text-gray-500">
                Total
              </th>
              <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500">
                Rate
              </th>
              <th className="px-4 py-2.5 text-left text-xs font-medium text-gray-500">
                Progress
              </th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((row) => (
              <tr
                key={row.studentId}
                className="border-b border-gray-50 last:border-0 hover:bg-gray-50"
              >
                <td className="px-4 py-2.5">
                  {row.name ? (
                    <span className="font-medium text-gray-900">{row.name}</span>
                  ) : (
                    <span className="font-mono text-xs text-gray-500">
                      {row.studentId.slice(0, 12)}…
                    </span>
                  )}
                </td>
                <td className="px-4 py-2.5 text-center font-semibold text-gray-700">
                  {row.loading ? '…' : (row.summary?.attended ?? '—')}
                </td>
                <td className="px-4 py-2.5 text-center text-gray-500">
                  {row.loading ? '…' : (row.summary?.total ?? '—')}
                </td>
                <td className="px-4 py-2.5">
                  {row.loading ? (
                    <Spinner size="sm" />
                  ) : row.summary ? (
                    <PercentBadge pct={row.summary.percentage} />
                  ) : (
                    <span className="text-xs text-gray-300">N/A</span>
                  )}
                </td>
                <td className="px-4 py-2.5">
                  {row.summary && (
                    <div className="h-2 w-24 overflow-hidden rounded-full bg-gray-100">
                      <div
                        className={`h-2 rounded-full ${
                          row.summary.percentage >= 85
                            ? 'bg-green-500'
                            : row.summary.percentage >= 75
                              ? 'bg-amber-400'
                              : 'bg-red-500'
                        }`}
                        style={{
                          width: `${Math.min(row.summary.percentage, 100)}%`,
                        }}
                      />
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Teacher / Admin view — session list + attendance sheet
// ---------------------------------------------------------------------------
interface ManageViewProps {
  pscId: UUID;
  isAdmin: boolean;
}

function ManageView({ pscId, isAdmin }: ManageViewProps) {
  const [activeTab, setActiveTab] = useState<'sessions' | 'roster'>('sessions');
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [expandedSessionId, setExpandedSessionId] = useState<UUID | null>(null);

  const sessionsQ = useQuery({
    queryKey: ['offerings', pscId, 'sessions'],
    queryFn: () =>
      api
        .get<SessionResponse[]>(`/offerings/${pscId}/sessions`)
        .then((r) => r.data),
  });

  const sessions = (sessionsQ.data ?? []).slice().sort(
    (a, b) => new Date(b.sessionDate).getTime() - new Date(a.sessionDate).getTime(),
  );

  return (
    <div className="space-y-4">
      {/* Tab bar */}
      <div className="flex items-center gap-1 rounded-lg border border-gray-200 bg-gray-50 p-1 w-fit">
        <button
          onClick={() => setActiveTab('sessions')}
          className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
            activeTab === 'sessions'
              ? 'bg-white text-gray-900 shadow-sm'
              : 'text-gray-500 hover:text-gray-700'
          }`}
        >
          <CalendarCheck size={14} />
          Sessions
        </button>
        <button
          onClick={() => setActiveTab('roster')}
          className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
            activeTab === 'roster'
              ? 'bg-white text-gray-900 shadow-sm'
              : 'text-gray-500 hover:text-gray-700'
          }`}
        >
          <BarChart2 size={14} />
          Class Roster
        </button>
      </div>

      {/* Roster tab */}
      {activeTab === 'roster' && <ClassRosterView pscId={pscId} isAdmin={isAdmin} />}

      {/* Sessions tab */}
      {activeTab === 'sessions' && (
      <>
      {sessionsQ.isLoading ? (
        <div className="flex justify-center py-16"><Spinner /></div>
      ) : (
      <>
      {/* Actions bar */}
      <div className="flex items-center justify-between">
        <p className="text-sm text-gray-500">
          {sessions.length} session{sessions.length !== 1 ? 's' : ''}
        </p>
        {!showCreateForm && (
          <Button size="sm" onClick={() => setShowCreateForm(true)}>
            <Plus size={15} className="mr-1" />
            New Session
          </Button>
        )}
      </div>

      {/* Create form */}
      {showCreateForm && (
        <CreateSessionForm pscId={pscId} onClose={() => setShowCreateForm(false)} />
      )}

      {/* Sessions list */}
      {sessions.length === 0 ? (
        <div className="flex flex-col items-center rounded-xl border border-dashed border-gray-200 py-14 text-center">
          <CalendarCheck size={36} className="mb-3 text-gray-300" />
          <p className="text-sm text-gray-400">No sessions yet. Create one to get started.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {sessions.map((session) => {
            const isExpanded = expandedSessionId === session.id;
            return (
              <div
                key={session.id}
                className="rounded-xl border border-gray-200 bg-white shadow-sm"
              >
                {/* Session card header — clickable */}
                <button
                  className="flex w-full items-center justify-between gap-3 p-4 text-left"
                  onClick={() =>
                    setExpandedSessionId(isExpanded ? null : session.id)
                  }
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-primary-50 text-primary-600">
                      <CalendarCheck size={18} />
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-gray-900">
                        {formatDate(session.sessionDate)}
                      </p>
                      {session.topic && (
                        <p className="mt-0.5 truncate text-xs text-gray-500">
                          {session.topic}
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="flex flex-shrink-0 items-center gap-2">
                    {session.open ? (
                      <Badge variant="success">
                        <Unlock size={11} className="mr-1 inline" />
                        Open
                      </Badge>
                    ) : (
                      <Badge variant="default">
                        <Lock size={11} className="mr-1 inline" />
                        Closed
                      </Badge>
                    )}
                    <span className="text-xs text-primary-600">
                      {isExpanded ? 'Hide ▲' : 'View ▼'}
                    </span>
                  </div>
                </button>

                {/* Expanded attendance sheet */}
                {isExpanded && (
                  <div className="px-4 pb-4">
                    <SessionSheet session={session} pscId={pscId} isAdmin={isAdmin} />
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
      </>
      )}
      </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Student view — read-only attendance summary
// ---------------------------------------------------------------------------
function StudentView({ pscId }: { pscId: UUID }) {
  const summaryQ = useQuery({
    queryKey: ['me', 'attendance', pscId],
    queryFn: () =>
      api
        .get<AttendanceSummaryResponse>(`/me/attendance/${pscId}`)
        .then((r) => r.data),
    retry: false,
  });

  if (summaryQ.isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    );
  }

  const summary = summaryQ.data;

  return (
    <div className="space-y-6">
      {/* Summary card */}
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-400">
            Sessions Attended
          </p>
          <p className="mt-1 text-3xl font-bold text-gray-900">
            {summary?.attended ?? '—'}
          </p>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-400">
            Total Sessions
          </p>
          <p className="mt-1 text-3xl font-bold text-gray-900">
            {summary?.total ?? '—'}
          </p>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-400">
            Attendance Rate
          </p>
          <div className="mt-1 flex items-center gap-2">
            <p className="text-3xl font-bold text-gray-900">
              {summary ? `${summary.percentage.toFixed(1)}%` : '—'}
            </p>
            {summary && <PercentBadge pct={summary.percentage} />}
          </div>
        </div>
      </div>

      {summary && (
        <>
          {/* Progress bar */}
          <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-sm font-medium text-gray-700">Overall Attendance</span>
              <span className="text-sm font-semibold text-gray-900">
                {summary.attended} / {summary.total}
              </span>
            </div>
            <div className="h-3 w-full overflow-hidden rounded-full bg-gray-100">
              <div
                className={`h-3 rounded-full transition-all ${
                  summary.percentage >= 85
                    ? 'bg-green-500'
                    : summary.percentage >= 75
                      ? 'bg-amber-400'
                      : 'bg-red-500'
                }`}
                style={{ width: `${Math.min(summary.percentage, 100)}%` }}
              />
            </div>
            {summary.percentage < 75 && (
              <p className="mt-3 text-xs text-red-600">
                ⚠ Your attendance is below 75%. Please attend classes to avoid
                consequences.
              </p>
            )}
          </div>
        </>
      )}

      {summaryQ.isError && (
        <div className="rounded-xl border border-gray-200 bg-white p-8 text-center">
          <p className="text-sm text-gray-400">
            Attendance data is not available yet for this course.
          </p>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Page root
// ---------------------------------------------------------------------------
export default function CourseAttendancePage() {
  const { pscId } = useParams<{ pscId: string }>();
  const user = useAuthStore((s) => s.user);

  const isStudent = user?.role === 'STUDENT';
  const isAdmin = user?.role === 'ADMIN';

  const offeringQ = useQuery({
    queryKey: ['offerings', pscId],
    queryFn: () =>
      api
        .get<OfferingSummaryResponse>(`/offerings/${pscId}`)
        .then((r) => r.data),
    enabled: !!pscId,
  });

  if (!pscId) {
    return (
      <div className="py-8 text-center text-sm text-gray-400">
        No course specified.
      </div>
    );
  }

  const offering = offeringQ.data;
  const title = offering
    ? `${offering.courseCode} — ${offering.courseName}`
    : '…';

  return (
    <div className="space-y-6">
      {/* Breadcrumb */}
      <div className="flex items-center gap-2">
        <Link
          to="/attendance"
          className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-800"
        >
          <ChevronLeft size={16} />
          Attendance
        </Link>
        <span className="text-gray-300">/</span>
        <span className="text-sm text-gray-600 truncate">{title}</span>
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
          {isStudent ? 'Your attendance record' : 'Session management & attendance marking'}
        </p>
      </div>

      {/* Role-based content */}
      {isStudent ? (
        <StudentView pscId={pscId} />
      ) : (
        <ManageView pscId={pscId} isAdmin={isAdmin} />
      )}
    </div>
  );
}
