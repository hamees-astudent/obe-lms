import { Link } from 'react-router-dom';
import { useQuery, useQueries } from '@tanstack/react-query';
import { ScanLine, ChevronRight, ArrowRight } from 'lucide-react';
import api from '@/lib/api';
import { useManagedOfferings } from '@/lib/queries';
import QueryError from '@/components/ui/QueryError';
import Spinner from '@/components/ui/Spinner';
import { useAuthStore } from '@/store/authStore';
import { ENROLLMENT_STATUS } from '@/types/api';
import type {
  EnrollmentResponse,
  OfferingSummaryResponse,
  UUID,
} from '@/types/api';

// ---------------------------------------------------------------------------
// Shared course card
// ---------------------------------------------------------------------------
interface CourseCardData {
  pscId: UUID;
  courseCode: string;
  courseName: string;
  semesterName?: string;
}

function CourseCard({ card, cta }: { card: CourseCardData; cta: string }) {
  return (
    <Link
      to={`/exams/${card.pscId}`}
      className="group flex flex-col rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all duration-200 hover:shadow-md hover:-translate-y-1 hover:border-primary-200 active:scale-95"
    >
      <div className="mb-3 flex items-start justify-between">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-50 text-primary-600">
          <ScanLine size={20} />
        </div>
        <ChevronRight size={15} className="mt-1 text-primary-400" />
      </div>
      <p className="text-base font-semibold text-gray-900 group-hover:text-primary-700">
        {card.courseCode}
      </p>
      <p className="mt-0.5 line-clamp-2 text-sm text-gray-500">{card.courseName}</p>
      {card.semesterName && <p className="mt-3 text-xs text-gray-400">{card.semesterName}</p>}
      <p className="mt-2 flex items-center gap-1 text-xs font-medium text-primary-600">
        {cta}
        <ArrowRight size={12} />
      </p>
    </Link>
  );
}

function CardGrid({ cards, cta }: { cards: CourseCardData[]; cta: string }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {cards.map((c, idx) => (
        <div key={c.pscId} className="animate-slide-up" style={{ animationDelay: `${idx * 50}ms` }}>
          <CourseCard card={c} cta={cta} />
        </div>
      ))}
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center py-16 text-center">
      <ScanLine size={40} className="mb-3 text-gray-300" />
      <p className="text-sm text-gray-400">{message}</p>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Student landing — read-only view of their own exam marks
// ---------------------------------------------------------------------------
function StudentLanding() {
  const enrollmentsQ = useQuery({
    queryKey: ['me', 'enrollments', 'active'],
    meta: { errorShownInline: true },
    queryFn: () =>
      api
        .get<EnrollmentResponse[]>(`/me/enrollments?status=${ENROLLMENT_STATUS.ACTIVE}`)
        .then((r) => r.data),
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

  if (enrollmentsQ.isError) {
    return <QueryError error={enrollmentsQ.error} onRetry={() => enrollmentsQ.refetch()} />;
  }
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
    .map((o) => ({ pscId: o.id, courseCode: o.courseCode, courseName: o.courseName }));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Exams</h1>
        <p className="mt-1 text-sm text-gray-500">Select a course to see your exam marks</p>
      </div>
      {cards.length === 0 ? (
        <EmptyState message="You are not enrolled in any courses." />
      ) : (
        <CardGrid cards={cards} cta="View marks" />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Teacher / Admin landing
// ---------------------------------------------------------------------------
function ManageLanding() {
  const { offerings, isLoading: loading, isError, error, refetch } = useManagedOfferings();

  const cards: CourseCardData[] = offerings.map((o) => ({
    pscId: o.id,
    courseCode: o.courseCode,
    courseName: o.courseName,
    semesterName: o.semesterName,
  }));

  if (isError) {
    return <QueryError error={error} onRetry={refetch} />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Exams</h1>
        <p className="mt-1 text-sm text-gray-500">
          Select a course to set up exams and record marks from marked copies
        </p>
      </div>
      {loading ? (
        <div className="flex justify-center py-16">
          <Spinner />
        </div>
      ) : cards.length === 0 ? (
        <EmptyState message="No active courses found." />
      ) : (
        <>
          <p className="text-sm text-gray-500">
            {cards.length} course{cards.length !== 1 ? 's' : ''}
          </p>
          <CardGrid cards={cards} cta="Manage exams" />
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Root — role dispatcher
// ---------------------------------------------------------------------------
export default function ExamsPage() {
  const user = useAuthStore((s) => s.user);
  if (user?.role === 'STUDENT') return <StudentLanding />;
  return <ManageLanding />;
}
