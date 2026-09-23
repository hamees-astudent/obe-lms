import { useQueries, useQuery } from '@tanstack/react-query';
import api from '@/lib/api';
import { statusOf } from '@/lib/apiError';
import { useAuthStore } from '@/store/authStore';
import type {
  OfferingSummaryResponse,
  Page,
  ProgramSummaryResponse,
  SemesterResponse,
  TeachingOfferingResponse,
  UserSummaryResponse,
  UUID,
} from '@/types/api';

/**
 * Active programs, as a plain array.
 *
 * Use this rather than an inline `useQuery` on the same key. React Query shares
 * the cache by key across pages, so two call sites that stored different
 * shapes under `['programs', 'active']` — one the whole `Page`, the others its
 * `content` — crashed whichever page read the other's entry ("i.map is not a
 * function" for teachers who had opened the dashboard first).
 */
export function useActivePrograms({ enabled = true }: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: ['programs', 'active'],
    queryFn: () =>
      api
        .get<Page<ProgramSummaryResponse>>('/programs?status=ACTIVE&size=100')
        .then((r) => r.data.content),
    enabled,
  });
}

/** The shape every course-picker landing page renders. */
export type ManagedOffering = TeachingOfferingResponse;

export interface OfferingsResult {
  offerings: ManagedOffering[];
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  refetch: () => void;
}

/**
 * Offerings the signed-in user teaches or assists, in open semesters.
 *
 * One server call that counts every way staff are assigned — teacher of
 * record, course assistant, or TEACHER/ASSISTANT course member. The pages used
 * to walk programs → semesters → offerings and keep only `teacherId === me`,
 * so a teacher added as a course member could mark attendance but never saw
 * the course listed.
 */
export function useTeachingOfferings({ enabled = true }: { enabled?: boolean } = {}): OfferingsResult {
  const q = useQuery({
    queryKey: ['me', 'teaching-offerings'],
    queryFn: () =>
      api.get<TeachingOfferingResponse[]>('/me/teaching-offerings').then((r) => r.data),
    enabled,
  });
  return {
    offerings: q.data ?? [],
    isLoading: q.isLoading,
    isError: q.isError,
    error: q.error,
    refetch: () => void q.refetch(),
  };
}

/**
 * Every offering in an open semester of an active program — the admin's view.
 * Walks programs → open semesters → offerings; there is no single endpoint.
 */
export function useOpenOfferings({ enabled = true }: { enabled?: boolean } = {}): OfferingsResult {
  const programsQ = useActivePrograms({ enabled });
  const programs = programsQ.data ?? [];

  const semesterQueries = useQueries({
    queries: programs.map((p) => ({
      queryKey: ['programs', p.id, 'semesters', 'open'],
      queryFn: () =>
        api
          .get<SemesterResponse[]>(`/programs/${p.id}/semesters?status=OPEN`)
          .then((r) => r.data),
      enabled,
    })),
  });
  const semesters = semesterQueries.flatMap((q) => q.data ?? []);

  const offeringQueries = useQueries({
    queries: semesters.map((s) => ({
      queryKey: ['semesters', s.id, 'offerings'],
      queryFn: () =>
        api.get<OfferingSummaryResponse[]>(`/semesters/${s.id}/offerings`).then((r) => r.data),
      enabled,
    })),
  });

  const offerings = offeringQueries.flatMap((q, idx) =>
    (q.data ?? []).map(
      (o): ManagedOffering => ({
        id: o.id,
        semesterId: o.semesterId,
        semesterName: semesters[idx].name,
        programName: semesters[idx].programName,
        courseId: o.courseId,
        courseCode: o.courseCode,
        courseName: o.courseName,
        creditHours: o.creditHours,
        teacherId: o.teacherId,
      }),
    ),
  );

  const all = [programsQ, ...semesterQueries, ...offeringQueries];
  const failed = all.find((q) => q.isError);
  return {
    offerings,
    isLoading: all.some((q) => q.isLoading),
    isError: !!failed,
    error: failed?.error ?? null,
    refetch: () => all.forEach((q) => void q.refetch()),
  };
}

/**
 * The offerings a staff landing page should list: all open ones for an admin,
 * the user's own for a teacher or assistant.
 */
export function useManagedOfferings(): OfferingsResult {
  const isAdmin = useAuthStore((s) => s.user?.role === 'ADMIN');
  const open = useOpenOfferings({ enabled: isAdmin });
  const mine = useTeachingOfferings({ enabled: !isAdmin });
  return isAdmin ? open : mine;
}

/**
 * One user by id (admin only), for showing a name where only the id is known.
 * Replaces pages loading every teacher up front just to look names up — lists
 * capped at a few hundred, so anyone past the cap showed as "Unknown".
 */
export function useUserSummary(id: UUID | null | undefined) {
  return useQuery({
    queryKey: ['admin-users', 'detail', id],
    queryFn: () => api.get<UserSummaryResponse>(`/admin/users/${id}`).then((r) => r.data),
    enabled: !!id,
    staleTime: 5 * 60_000,
  });
}

/**
 * Resolves to `null` when the server answers 404, for endpoints where "not
 * found" is an ordinary answer rather than a failure — `/me/…/submission`
 * before the student has submitted, say. Without this, the global query error
 * toast would fire for every unsubmitted assignment on the page.
 */
export function nullIfNotFound<T>(request: Promise<T>): Promise<T | null> {
  return request.catch((error: unknown) => {
    if (statusOf(error) === 404) return null;
    throw error;
  });
}
