import { useQueries, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { CalendarCheck, ChevronRight } from 'lucide-react';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import type {
  EnrollmentResponse,
  OfferingSummaryResponse,
  AttendanceSummaryResponse,
  ProgramSummaryResponse,
  SemesterResponse,
  Page,
  UUID,
} from '@/types/api';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function PercentBadge({ pct }: { pct: number }) {
  const variant = pct >= 85 ? 'success' : pct >= 75 ? 'warning' : 'danger';
  return <Badge variant={variant}>{pct.toFixed(1)}%</Badge>;
}

// ---------------------------------------------------------------------------
// Student — attendance summary cards
// ---------------------------------------------------------------------------
interface SummaryCardProps {
  pscId: UUID;
  courseCode: string;
  courseName: string;
  summary?: AttendanceSummaryResponse;
  loading: boolean;
}

function SummaryCard({ pscId, courseCode, courseName, summary, loading }: SummaryCardProps) {
  return (
    <Link
      to={`/attendance/${pscId}`}
      className="group flex flex-col rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all duration-200 hover:shadow-md hover:-translate-y-1 hover:border-primary-200 active:scale-95"
    >
      <div className="mb-3 flex items-start justify-between">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-50 text-primary-600">
          <CalendarCheck size={20} />
        </div>
        {loading ? (
          <Spinner size="sm" />
        ) : summary ? (
          <PercentBadge pct={summary.percentage} />
        ) : (
          <Badge variant="default">N/A</Badge>
        )}
      </div>
      <p className="text-base font-semibold text-gray-900 group-hover:text-primary-700">
        {courseCode}
      </p>
      <p className="mt-0.5 line-clamp-2 text-sm text-gray-500">{courseName}</p>
      {summary && (
        <p className="mt-3 text-xs text-gray-400">
          {summary.attended} / {summary.total} sessions attended
        </p>
      )}
      <div className="mt-auto flex items-center justify-end pt-3">
        <ChevronRight size={15} className="text-primary-400" />
      </div>
    </Link>
  );
}

function StudentLanding() {
  const enrollmentsQ = useQuery({
    queryKey: ['me', 'enrollments', 'active'],
    queryFn: () =>
      api.get<EnrollmentResponse[]>('/me/enrollments?status=ENROLLED').then((r) => r.data),
  });
  const enrollments = enrollmentsQ.data ?? [];

  const offeringQueries = useQueries({
    queries: enrollments.map((e) => ({
      queryKey: ['offerings', e.pscId],
      queryFn: () =>
        api.get<OfferingSummaryResponse>(`/offerings/${e.pscId}`).then((r) => r.data),
      enabled: enrollments.length > 0,
    })),
  });

  const summaryQueries = useQueries({
    queries: enrollments.map((e) => ({
      queryKey: ['me', 'attendance', e.pscId],
      queryFn: () =>
        api.get<AttendanceSummaryResponse>(`/me/attendance/${e.pscId}`).then((r) => r.data),
      enabled: enrollments.length > 0,
      retry: false,
    })),
  });

  if (enrollmentsQ.isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">My Attendance</h1>
        <p className="mt-1 text-sm text-gray-500">
          Attendance summary across all enrolled courses
        </p>
      </div>
      {enrollments.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-center">
          <CalendarCheck size={40} className="mb-3 text-gray-300" />
          <p className="text-sm text-gray-400">You are not enrolled in any courses.</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {enrollments.map((e, idx) => {
            const offering = offeringQueries[idx]?.data;
            const summary = summaryQueries[idx]?.data;
            const loading =
              (offeringQueries[idx]?.isLoading ?? false) ||
              (summaryQueries[idx]?.isLoading ?? false);
            return (
              <div key={e.pscId} className="animate-slide-up" style={{ animationDelay: `${idx * 50}ms` }}>
                <SummaryCard
                  pscId={e.pscId}
                  courseCode={offering?.courseCode ?? '…'}
                  courseName={offering?.courseName ?? '…'}
                  summary={summary}
                  loading={loading}
                />
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Teacher / Admin — course list linking to course attendance
// ---------------------------------------------------------------------------
interface ManageCardData {
  pscId: UUID;
  courseCode: string;
  courseName: string;
  semesterName?: string;
}

function ManageCard({ card }: { card: ManageCardData }) {
  return (
    <Link
      to={`/attendance/${card.pscId}`}
      className="group flex flex-col rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all duration-200 hover:shadow-md hover:-translate-y-1 hover:border-primary-200 active:scale-95"
    >
      <div className="mb-3 flex items-start justify-between">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-50 text-primary-600">
          <CalendarCheck size={20} />
        </div>
        <ChevronRight size={15} className="mt-1 text-primary-400" />
      </div>
      <p className="text-base font-semibold text-gray-900 group-hover:text-primary-700">
        {card.courseCode}
      </p>
      <p className="mt-0.5 line-clamp-2 text-sm text-gray-500">{card.courseName}</p>
      {card.semesterName && (
        <p className="mt-3 text-xs text-gray-400">{card.semesterName}</p>
      )}
      <p className="mt-2 text-xs font-medium text-primary-600">Manage attendance →</p>
    </Link>
  );
}

function ManageLanding() {
  const user = useAuthStore((s) => s.user);

  const programsQ = useQuery({
    queryKey: ['programs', 'active'],
    queryFn: () =>
      api
        .get<Page<ProgramSummaryResponse>>('/programs?status=ACTIVE&size=100')
        .then((r) => r.data.content),
  });
  const programs = programsQ.data ?? [];

  const semesterQueries = useQueries({
    queries: programs.map((p) => ({
      queryKey: ['programs', p.id, 'semesters', 'active'],
      queryFn: () =>
        api
          .get<SemesterResponse[]>(`/programs/${p.id}/semesters?status=ACTIVE`)
          .then((r) => r.data),
      enabled: programs.length > 0,
    })),
  });
  const allSemesters = semesterQueries.flatMap((q) => q.data ?? []);

  const offeringQueries = useQueries({
    queries: allSemesters.map((s) => ({
      queryKey: ['semesters', s.id, 'offerings'],
      queryFn: () =>
        api
          .get<OfferingSummaryResponse[]>(`/semesters/${s.id}/offerings`)
          .then((r) => r.data),
      enabled: allSemesters.length > 0,
    })),
  });

  const loading =
    programsQ.isLoading ||
    semesterQueries.some((q) => q.isLoading) ||
    offeringQueries.some((q) => q.isLoading);

  const semesterMap = new Map<UUID, SemesterResponse>(
    allSemesters.map((s) => [s.id, s]),
  );

  const isAdmin = user?.role === 'ADMIN';

  const cards: ManageCardData[] = offeringQueries
    .flatMap((q, idx) =>
      (q.data ?? []).map((o) => ({ o, semester: allSemesters[idx] })),
    )
    .filter(({ o }) => isAdmin || o.teacherId === user?.id)
    .map(({ o, semester }) => ({
      pscId: o.id,
      courseCode: o.courseCode,
      courseName: o.courseName,
      semesterName: semester?.name ?? semesterMap.get(o.semesterId)?.name,
    }));

  if (loading) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Attendance</h1>
        </div>
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Attendance</h1>
        <p className="mt-1 text-sm text-gray-500">
          Select a course to manage sessions and mark attendance
        </p>
      </div>
      {cards.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-center">
          <CalendarCheck size={40} className="mb-3 text-gray-300" />
          <p className="text-sm text-gray-400">No active courses found.</p>
        </div>
      ) : (
        <>
          <p className="text-sm text-gray-500">
            {cards.length} course{cards.length !== 1 ? 's' : ''}
          </p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {cards.map((c, idx) => (
              <div key={c.pscId} className="animate-slide-up" style={{ animationDelay: `${idx * 50}ms` }}>
                <ManageCard card={c} />
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Root — role dispatcher
// ---------------------------------------------------------------------------
export default function AttendancePage() {
  const user = useAuthStore((s) => s.user);
  if (user?.role === 'STUDENT') return <StudentLanding />;
  return <ManageLanding />;
}
