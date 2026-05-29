import { ApiHelper } from '../../helpers/api.helper';

// ---------------------------------------------------------------------------
// Programs
// ---------------------------------------------------------------------------

export interface CreateProgramPayload {
  /** Uppercase letters, digits, or underscores — e.g. "BSCS" */
  code: string;
  name: string;
  description?: string;
  durationYears: number;
}

export interface ProgramResponse {
  id: string;
  code: string;
  name: string;
  description?: string;
  durationYears: number;
  status: string;
  createdAt: string;
}

export async function createProgram(
  api: ApiHelper,
  adminToken: string,
  payload: CreateProgramPayload,
): Promise<ProgramResponse> {
  return api.post<ProgramResponse>('/admin/programs', payload, adminToken);
}

export function buildProgram(
  overrides: Partial<CreateProgramPayload> = {},
): CreateProgramPayload {
  const uid = Math.floor(Math.random() * 9000) + 1000;
  return {
    code: `PRG${uid}`,
    name: `Test Program ${uid}`,
    durationYears: 4,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// PLOs (Program Learning Outcomes)
// ---------------------------------------------------------------------------

export interface CreatePloPayload {
  /** Uppercase short code — e.g. "PLO1" */
  code: string;
  title: string;
  description?: string;
  orderIndex: number;
}

export interface PloResponse {
  id: string;
  code: string;
  title: string;
  description?: string;
  orderIndex: number;
}

export async function createPlo(
  api: ApiHelper,
  adminToken: string,
  programId: string,
  payload: CreatePloPayload,
): Promise<PloResponse> {
  return api.post<PloResponse>(`/admin/programs/${programId}/plos`, payload, adminToken);
}

export function buildPlo(
  orderIndex: number,
  overrides: Partial<CreatePloPayload> = {},
): CreatePloPayload {
  return {
    code: `PLO${orderIndex}`,
    title: `PLO ${orderIndex} — Test outcome`,
    orderIndex,
    ...overrides,
  };
}
