import { useQueries, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { BookOpen, ChevronRight } from 'lucide-react';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import type {
  EnrollmentResponse,
  OfferingSummaryResponse,
  ProgramSummaryResponse,
  SemesterResponse,
  Page,
  UUID,
} from '@/types/api';

// ---------------------------------------------------------------------------
// Shared card data shape
// ---------------------------------------------------------------------------
interface CourseCardData {
  pscId: UUID;
  courseCode: string;
  courseName: string;
  creditHours: number;
  semesterName?: string;
  programName?: string;
}

function CourseCard({ card }: { card: CourseCardData }) {
  return (
    <Link
      to={`/courses/${card.pscId}`}
      className="group flex flex-col rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all duration-200 hover:shadow-md hover:-translate-y-1 hover:border-primary-200 active:scale-95"
    >
      <div className="mb-4 flex items-start justify-between">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-50 text-primary-600">
          <BookOpen size={20} />
        </div>
        <Badge variant="success">Active</Badge>
      </div>
      <p className="text-base font-semibold text-gray-900 group-hover:text-primary-700">
        {card.courseCode}
      </p>
      <p className="mt-0.5 line-clamp-2 text-sm text-gray-500">{card.courseName}</p>
      <div className="mt-auto flex items-center justify-between pt-4 text-xs text-gray-400">
        <div className="space-y-0.5">
          <span className="block">{card.creditHours} credit hrs</span>
          {card.semesterName && <span className="block">{card.semesterName}</span>}
          {card.programName && (
            <span className="block text-gray-300">{card.programName}</span>
          )}
        </div>
        <ChevronRight size={15} className="text-primary-400" />
      </div>
    </Link>
  );
}

function CourseGrid({
  title,
  cards,
  emptyMsg,
  loading,
}: {
  title: string;
  cards: CourseCardData[];
  emptyMsg: string;
  loading: boolean;
}) {
  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    );
  }
  if (cards.length === 0) {
    return (
      <div className="flex flex-col items-center py-16 text-center">
        <BookOpen size={40} className="mb-3 text-gray-300" />
        <p className="text-sm text-gray-400">{emptyMsg}</p>
      </div>
    );
  }
  return (
    <>
      <p className="text-sm text-gray-500">
        {title} · {cards.length} course{cards.length !== 1 ? 's' : ''}
      </p>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {cards.map((c, idx) => (
          <div key={c.pscId} className="animate-slide-up" style={{ animationDelay: `${idx * 50}ms` }}>
            <CourseCard card={c} />
          </div>
        ))}
      </div>
    </>
  );
}

// ---------------------------------------------------------------------------
// Student view — enrolled offerings
// ---------------------------------------------------------------------------
function StudentCourses() {
  const enrollmentsQ = useQuery({
    queryKey: ['me', 'enrollments', 'active'],
    queryFn: () =>
      api
        .get<EnrollmentResponse[]>('/me/enrollments?status=ENROLLED')
        .then((r) => r.data),
  });
  const enrollments = enrollmentsQ.data ?? [];

  const offeringQueries = useQueries({
    queries: enrollments.map((e) => ({
      queryKey: ['offerings', e.pscId],
      queryFn: () =>
        api
          .get<OfferingSummaryResponse>(`/offerings/${e.pscId}`)
          .then((r) => r.data),
      enabled: enrollments.length > 0,
    })),
  });

  const loading =
    enrollmentsQ.isLoading || offeringQueries.some((q) => q.isLoading);

  const cards: CourseCardData[] = offeringQueries
    .map((q) => q.data)
    .filter((o): o is OfferingSummaryResponse => !!o)
    .map((o) => ({
      pscId: o.id,
      courseCode: o.courseCode,
      courseName: o.courseName,
      creditHours: o.creditHours,
    }));

  return (
    <CourseGrid
      title="Enrolled courses"
      cards={cards}
      emptyMsg="You are not enrolled in any courses."
      loading={loading}
    />
  );
}

// ---------------------------------------------------------------------------
// Teacher / Assistant view — offerings assigned to this user
// ---------------------------------------------------------------------------
function TeacherCourses() {
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

  const cards: CourseCardData[] = offeringQueries
    .flatMap((q, idx) =>
      (q.data ?? []).map((o) => ({ o, semester: allSemesters[idx] })),
    )
    .filter(({ o }) => o.teacherId === user?.id)
    .map(({ o, semester }) => ({
      pscId: o.id,
      courseCode: o.courseCode,
      courseName: o.courseName,
      creditHours: o.creditHours,
      semesterName: semester?.name ?? semesterMap.get(o.semesterId)?.name,
      programName: semesterMap.get(o.semesterId)?.programName,
    }));

  return (
    <CourseGrid
      title="My courses"
      cards={cards}
      emptyMsg="You are not assigned to any active courses."
      loading={loading}
    />
  );
}

// ---------------------------------------------------------------------------
// Admin view — all active offerings across all programs
// ---------------------------------------------------------------------------
function AdminCourses() {
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

  const cards: CourseCardData[] = offeringQueries
    .flatMap((q, idx) =>
      (q.data ?? []).map((o) => ({ o, semester: allSemesters[idx] })),
    )
    .map(({ o, semester }) => ({
      pscId: o.id,
      courseCode: o.courseCode,
      courseName: o.courseName,
      creditHours: o.creditHours,
      semesterName: semester?.name ?? semesterMap.get(o.semesterId)?.name,
      programName: semesterMap.get(o.semesterId)?.programName,
    }));

  return (
    <CourseGrid
      title="All active courses"
      cards={cards}
      emptyMsg="No active courses found."
      loading={loading}
    />
  );
}

// ---------------------------------------------------------------------------
// Page shell
// ---------------------------------------------------------------------------
export default function CoursesPage() {
  const role = useAuthStore((s) => s.user?.role);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Courses</h1>
        <p className="mt-1 text-sm text-gray-500">
          Browse course materials and resources
        </p>
      </div>

      {role === 'STUDENT' && <StudentCourses />}
      {(role === 'TEACHER' || role === 'ASSISTANT') && <TeacherCourses />}
      {role === 'ADMIN' && <AdminCourses />}
    </div>
  );
}

