import { ApiHelper } from '../../helpers/api.helper';

export interface CreateSemesterPayload {
  name: string;
  /** ISO date string — "YYYY-MM-DD" */
  startDate: string;
  /** ISO date string — "YYYY-MM-DD", must be after startDate */
  endDate: string;
}

export interface SemesterResponse {
  id: string;
  programId: string;
  programName: string;
  name: string;
  startDate: string;
  endDate: string;
  status: 'UPCOMING' | 'ACTIVE' | 'COMPLETED';
  createdAt: string;
}

export async function createSemester(
  api: ApiHelper,
  adminToken: string,
  programId: string,
  payload: CreateSemesterPayload,
): Promise<SemesterResponse> {
  return api.post<SemesterResponse>(
    `/admin/programs/${programId}/semesters`,
    payload,
    adminToken,
  );
}

export function buildSemester(
  overrides: Partial<CreateSemesterPayload> = {},
): CreateSemesterPayload {
  const uid = Math.floor(Math.random() * 9000) + 1000;
  const now  = new Date();
  const start = new Date(now);
  start.setDate(start.getDate() + 1);           // tomorrow
  const end = new Date(start);
  end.setMonth(end.getMonth() + 4);             // 4 months later

  const fmt = (d: Date) => d.toISOString().slice(0, 10);

  return {
    name: `Test Semester ${uid}`,
    startDate: fmt(start),
    endDate: fmt(end),
    ...overrides,
  };
}
