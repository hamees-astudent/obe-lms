import { ApiHelper } from '../../helpers/api.helper';

export interface CreateSessionPayload {
  /** Offering / PSC id */
  pscId: string;
  /** ISO date string — "YYYY-MM-DD" */
  sessionDate: string;
  topic?: string;
}

export interface SessionResponse {
  id: string;
  pscId: string;
  sessionDate: string;
  topic?: string;
  createdBy: string;
  openedAt?: string;
  closedAt?: string;
  open: boolean;
  createdAt: string;
}

/**
 * Creates an attendance session.
 * Requires a TEACHER, ASSISTANT, or ADMIN token.
 */
export async function createSession(
  api: ApiHelper,
  token: string,
  payload: CreateSessionPayload,
): Promise<SessionResponse> {
  return api.post<SessionResponse>('/sessions', payload, token);
}

export function buildSession(
  pscId: string,
  overrides: Partial<CreateSessionPayload> = {},
): CreateSessionPayload {
  const today = new Date().toISOString().slice(0, 10);
  return {
    pscId,
    sessionDate: today,
    topic: 'Test lecture topic',
    ...overrides,
  };
}
