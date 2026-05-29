import { ApiHelper } from '../../helpers/api.helper';

export type SubmissionType = 'FILE' | 'TEXT' | 'BOTH';

export interface CreateAssignmentPayload {
  title: string;
  description?: string;
  submissionType: SubmissionType;
  totalMarks: number;
  /** ISO-8601 datetime string — e.g. "2025-12-31T23:59:00" */
  dueDate: string;
  allowLateSubmission?: boolean;
  latePenaltyPercent?: number;
}

export interface AssignmentResponse {
  id: string;
  pscId: string;
  title: string;
  description?: string;
  submissionType: SubmissionType;
  totalMarks: number;
  dueDate: string;
  allowLateSubmission: boolean;
  latePenaltyPercent: number;
  createdAt: string;
}

/**
 * Creates an assignment for a given offering (pscId).
 * Requires a TEACHER token for that offering.
 */
export async function createAssignment(
  api: ApiHelper,
  teacherToken: string,
  pscId: string,
  payload: CreateAssignmentPayload,
): Promise<AssignmentResponse> {
  return api.post<AssignmentResponse>(
    `/offerings/${pscId}/assignments`,
    { pscId, ...payload },   // pscId required by @NotNull validation; controller also sets it from path
    teacherToken,
  );
}

export function buildAssignment(
  overrides: Partial<CreateAssignmentPayload> = {},
): CreateAssignmentPayload {
  const uid = Math.floor(Math.random() * 9000) + 1000;
  // Due date set to 7 days from now
  const due = new Date();
  due.setDate(due.getDate() + 7);

  return {
    title: `Test Assignment ${uid}`,
    submissionType: 'TEXT',
    totalMarks: 100,
    dueDate: due.toISOString().replace('Z', '').slice(0, 19),
    allowLateSubmission: false,
    ...overrides,
  };
}
