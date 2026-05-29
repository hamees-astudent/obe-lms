import { ApiHelper } from '../../helpers/api.helper';

export interface CreateCoursePayload {
  /** Uppercase letters, digits, or underscores — e.g. "CS101" */
  code: string;
  name: string;
  creditHours: number;
  description?: string;
}

export interface CourseResponse {
  id: string;
  code: string;
  name: string;
  creditHours: number;
  status: string;
}

/**
 * Creates a course via the admin API.
 * Course codes must be unique across the system (use buildCourse for auto-unique codes).
 */
export async function createCourse(
  api: ApiHelper,
  adminToken: string,
  payload: CreateCoursePayload,
): Promise<CourseResponse> {
  return api.post<CourseResponse>('/admin/courses', payload, adminToken);
}

export function buildCourse(overrides: Partial<CreateCoursePayload> = {}): CreateCoursePayload {
  // Use a random 4-digit suffix to avoid collisions between parallel test workers.
  const uid = Math.floor(Math.random() * 9000) + 1000;
  return {
    code: `TST${uid}`,
    name: `Test Course ${uid}`,
    creditHours: 3,
    ...overrides,
  };
}
