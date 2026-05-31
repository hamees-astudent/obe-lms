import { useEffect } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useQueries } from '@tanstack/react-query';
import { ClipboardList, ChevronRight } from 'lucide-react';
import api from '@/lib/api';
import { useAuthStore } from '@/store/authStore';
import Spinner from '@/components/ui/Spinner';
import type {
  EnrollmentResponse,
  OfferingSummaryResponse,
  AssignmentResponse,
  QuizResponse,
  ProgramSummaryResponse,
  SemesterResponse,
  Page,
  UUID,
} from '@/types/api';

// ---------------------------------------------------------------------------
// Query-param redirect: /assessment?assignment={id} → /assessment/{pscId}?assignment={id}
// ---------------------------------------------------------------------------
function QueryParamRedirect() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const assignmentId = searchParams.get('assignment');
  const quizId = searchParams.get('quiz');

  const assignmentQ = useQuery({
    queryKey: ['assignments', assignmentId],
    queryFn: () =>
      api.get<AssignmentResponse>(`/assignments/${assignmentId}`).then((r) => r.data),
    enabled: !!assignmentId,
    retry: false,
  });

  const quizQ = useQuery({
    queryKey: ['quizzes', quizId],
    queryFn: () =>
      api.get<QuizResponse>(`/quizzes/${quizId}`).then((r) => r.data),
    enabled: !!quizId,
    retry: false,
  });

  useEffect(() => {
    if (assignmentId && assignmentQ.data) {
      navigate(
        `/assessment/${assignmentQ.data.pscId}?assignment=${assignmentId}`,
        { replace: true },
      );
    }
  }, [assignmentId, assignmentQ.data, navigate]);

  useEffect(() => {
    if (quizId && quizQ.data) {
      navigate(`/assessment/${quizQ.data.pscId}?quiz=${quizId}`, { replace: true });
    }
  }, [quizId, quizQ.data, navigate]);

  return (
    <div className="flex justify-center py-16">
      <Spinner />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Shared course card
// ---------------------------------------------------------------------------
interface CourseCardData {
  pscId: UUID;
  courseCode: string;
  courseName: string;
  semesterName?: string;
}

function CourseCard({ card }: { card: CourseCardData }) {
  return (
    <Link
      to={`/assessment/${card.pscId}`}
      className="group flex flex-col rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all duration-200 hover:shadow-md hover:-translate-y-1 hover:border-primary-200 active:scale-95"
    >
      <div className="mb-3 flex items-start justify-between">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-50 text-primary-600">
          <ClipboardList size={20} />
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
      <p className="mt-2 text-xs font-medium text-primary-600">View assessments →</p>
    </Link>
  );
}

// ---------------------------------------------------------------------------
// Student landing
// ---------------------------------------------------------------------------
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

  if (enrollmentsQ.isLoading) {
    return (
      <div className="flex justify-center py-16">
        <Spinner />
      </div>
    );
  }

  const cards: CourseCardData[] = offeringQueries
    .map((q) => q.data)
    .filter((o): o is OfferingSummaryResponse => !!o)
    .map((o) => ({
      pscId: o.id,
      courseCode: o.courseCode,
      courseName: o.courseName,
    }));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Assessments</h1>
        <p className="mt-1 text-sm text-gray-500">
          Select a course to view assignments and quizzes
        </p>
      </div>
      {cards.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-center">
          <ClipboardList size={40} className="mb-3 text-gray-300" />
          <p className="text-sm text-gray-400">You are not enrolled in any courses.</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {cards.map((c, idx) => (
            <div key={c.pscId} className="animate-slide-up" style={{ animationDelay: `${idx * 50}ms` }}>
              <CourseCard card={c} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Teacher / Admin landing
// ---------------------------------------------------------------------------
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
      queryKey: ['programs', p.id, 'semesters', 'open'],
      queryFn: () =>
        api
          .get<SemesterResponse[]>(`/programs/${p.id}/semesters?status=OPEN`)
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

  const cards: CourseCardData[] = offeringQueries
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
          <h1 className="text-2xl font-bold text-gray-900">Assessments</h1>
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
        <h1 className="text-2xl font-bold text-gray-900">Assessments</h1>
        <p className="mt-1 text-sm text-gray-500">
          Select a course to manage assignments and quizzes
        </p>
      </div>
      {cards.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-center">
          <ClipboardList size={40} className="mb-3 text-gray-300" />
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
                <CourseCard card={c} />
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
export default function AssessmentPage() {
  const [searchParams] = useSearchParams();
  const user = useAuthStore((s) => s.user);

  // If navigated with query params (from CourseDetailPage material links), redirect
  if (searchParams.get('assignment') || searchParams.get('quiz')) {
    return <QueryParamRedirect />;
  }

  if (user?.role === 'STUDENT') return <StudentLanding />;
  return <ManageLanding />;
}
