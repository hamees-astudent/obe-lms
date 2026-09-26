import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, AlertTriangle } from 'lucide-react';
import api from '@/lib/api';
import type { CohortEnrollmentResponse, UUID } from '@/types/api';

/**
 * Enrolls every member of a cohort in one offering. The offering's roster is
 * refreshed afterwards so an open member list shows the new students.
 */
export function useEnrollCohort(onDone?: (result: CohortEnrollmentResponse) => void) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ cohortId, pscId }: { cohortId: UUID; pscId: UUID }) =>
      api
        .post<CohortEnrollmentResponse>(`/admin/cohorts/${cohortId}/enrollments`, { pscId })
        .then((r) => r.data),
    onSuccess: (result) => {
      qc.invalidateQueries({ queryKey: ['enrollments', result.pscId] });
      onDone?.(result);
    },
  });
}

/**
 * What a cohort enrollment did, student by student. Skips are listed with
 * their reason: "3 skipped" alone leaves the admin to diff two rosters by hand.
 */
export function CohortEnrollmentSummary({ result }: { result: CohortEnrollmentResponse }) {
  const skipped = result.results.filter((r) => r.outcome === 'SKIPPED');
  const total = result.results.length;

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-3 rounded-lg bg-green-50 px-4 py-3 text-sm text-green-800">
        <CheckCircle2 size={18} className="mt-0.5 flex-shrink-0" />
        <p>
          Enrolled <strong>{result.enrolled}</strong> of {total} student{total === 1 ? '' : 's'}.
          {result.enrolled > 0 && ' Each one has been notified.'}
        </p>
      </div>

      {skipped.length > 0 && (
        <div className="space-y-2">
          <p className="flex items-center gap-1.5 text-sm font-medium text-amber-800">
            <AlertTriangle size={15} />
            {skipped.length} skipped
          </p>
          <ul className="max-h-64 divide-y divide-gray-100 overflow-y-auto rounded-lg border border-gray-200 text-sm">
            {skipped.map((r) => (
              <li key={r.studentId} className="flex items-center justify-between gap-3 px-3 py-1.5">
                <span className="min-w-0 truncate text-gray-800">
                  {r.studentName}
                  {r.studentNumber && (
                    <span className="ml-1.5 font-mono text-xs text-gray-400">{r.studentNumber}</span>
                  )}
                </span>
                <span className="flex-shrink-0 text-xs text-gray-500">{r.reason}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
