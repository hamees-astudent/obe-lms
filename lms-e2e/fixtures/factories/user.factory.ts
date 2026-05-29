import { ApiHelper } from '../../helpers/api.helper';

export type UserRole = 'ADMIN' | 'TEACHER' | 'ASSISTANT' | 'STUDENT';

export interface CreateUserPayload {
  /** Full name — the backend stores it as a single `name` field. */
  name: string;
  email: string;
  password: string;
  role: UserRole;
}

export interface UserResponse {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  status: string;
  createdAt: string;
}

/**
 * Seeds a user via the admin API and returns the created resource.
 * Requires an admin access token.
 */
export async function createUser(
  api: ApiHelper,
  adminToken: string,
  payload: CreateUserPayload,
): Promise<UserResponse> {
  return api.post<UserResponse>('/admin/users', payload, adminToken);
}

/** Builds a unique CreateUserPayload; override any field as needed. */
export function buildUser(overrides: Partial<CreateUserPayload> = {}): CreateUserPayload {
  const uid = Date.now();
  return {
    name: `Test User ${uid}`,
    email: `user-${uid}@lms.test`,
    password: 'Test@12345',
    role: 'STUDENT',
    ...overrides,
  };
}

/** Convenience builder for a STUDENT user. */
export const buildStudent = (overrides: Partial<CreateUserPayload> = {}) =>
  buildUser({ role: 'STUDENT', ...overrides });

/** Convenience builder for a TEACHER user. */
export const buildTeacher = (overrides: Partial<CreateUserPayload> = {}) =>
  buildUser({ role: 'TEACHER', ...overrides });

/** Convenience builder for an ASSISTANT user. */
export const buildAssistant = (overrides: Partial<CreateUserPayload> = {}) =>
  buildUser({ role: 'ASSISTANT', ...overrides });
