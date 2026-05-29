import { ApiHelper } from '../../helpers/api.helper';

export interface CreateOfferingPayload {
  semesterId: string;
  courseId: string;
  teacherId: string;
  maxCapacity: number;
}

export interface OfferingResponse {
  id: string;
  semesterId: string;
  courseId: string;
  courseCode: string;
  courseName: string;
  creditHours: number;
  teacherId: string;
  maxCapacity: number;
  createdAt: string;
}

/**
 * Creates a Program-Semester-Course (PSC) offering.
 * The `id` of the returned response is the `pscId` used throughout the API.
 */
export async function createOffering(
  api: ApiHelper,
  adminToken: string,
  payload: CreateOfferingPayload,
): Promise<OfferingResponse> {
  return api.post<OfferingResponse>('/admin/offerings', payload, adminToken);
}

export function buildOffering(
  semesterId: string,
  courseId: string,
  teacherId: string,
  overrides: Partial<CreateOfferingPayload> = {},
): CreateOfferingPayload {
  return {
    semesterId,
    courseId,
    teacherId,
    maxCapacity: 40,
    ...overrides,
  };
}
