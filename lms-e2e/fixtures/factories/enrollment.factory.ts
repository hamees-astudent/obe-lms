import { ApiHelper } from '../../helpers/api.helper';

export interface CreateEnrollmentPayload {
  /** Offering / PSC id */
  pscId: string;
  studentId: string;
}

export interface EnrollmentResponse {
  id: string;
  pscId: string;
  studentId: string;
  studentName: string;
  enrolledAt: string;
  status: 'ACTIVE' | 'DROPPED' | 'COMPLETED';
}

export async function enrollStudent(
  api: ApiHelper,
  adminToken: string,
  pscId: string,
  studentId: string,
): Promise<EnrollmentResponse> {
  return api.post<EnrollmentResponse>(
    '/admin/enrollments',
    { pscId, studentId } satisfies CreateEnrollmentPayload,
    adminToken,
  );
}

export async function dropEnrollment(
  api: ApiHelper,
  adminToken: string,
  enrollmentId: string,
): Promise<void> {
  await api.delete(`/admin/enrollments/${enrollmentId}`, adminToken);
}
