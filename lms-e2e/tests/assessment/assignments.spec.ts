import { test, expect } from '../../fixtures';
import { buildAssignment, createAssignment } from '../../fixtures/factories/assignment.factory';

// Helper: log in as the seeded world student and return an access token.
async function loginAsWorldStudent(
  request: import('@playwright/test').APIRequestContext,
  world: import('../../fixtures').WorldContext,
): Promise<string> {
  const res = await request.post('/api/auth/login', {
    data: { email: world.studentEmail, password: world.studentPassword },
  });
  const { accessToken } = await res.json();
  return accessToken as string;
}

// ---------------------------------------------------------------------------
// Assignment CRUD
// ---------------------------------------------------------------------------

test.describe('Assessment — Assignments (CRUD)', () => {
  test('teacher can create an assignment and response has all fields', async ({
    api,
    world,
  }) => {
    const payload = buildAssignment({ title: 'Homework 1', submissionType: 'TEXT', totalMarks: 50 });
    const assignment = await createAssignment(api, world.teacherToken, world.pscId, payload);

    expect(assignment.id).toBeTruthy();
    expect(assignment.pscId).toBe(world.pscId);
    expect(assignment.title).toBe(payload.title);
    expect(assignment.submissionType).toBe('TEXT');
    expect(Number(assignment.totalMarks)).toBe(50);
    expect(assignment.dueDate).toBeTruthy();
  });

  test('missing title returns 400', async ({ request, world }) => {
    const due = new Date();
    due.setDate(due.getDate() + 7);
    const response = await request.post(`/api/offerings/${world.pscId}/assignments`, {
      data: {
        pscId: world.pscId,
        submissionType: 'TEXT',
        totalMarks: 10,
        dueDate: due.toISOString(),
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('invalid submissionType returns 400', async ({ request, world }) => {
    const due = new Date();
    due.setDate(due.getDate() + 7);
    const response = await request.post(`/api/offerings/${world.pscId}/assignments`, {
      data: {
        pscId: world.pscId,
        title: 'Bad type',
        submissionType: 'INVALID',
        totalMarks: 10,
        dueDate: due.toISOString(),
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('past dueDate returns 400 (@Future validation)', async ({ request, world }) => {
    const past = new Date();
    past.setDate(past.getDate() - 1);
    const response = await request.post(`/api/offerings/${world.pscId}/assignments`, {
      data: {
        pscId: world.pscId,
        title: 'Late assignment',
        submissionType: 'TEXT',
        totalMarks: 10,
        dueDate: past.toISOString(),
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('student cannot create an assignment (403)', async ({ request, world }) => {
    const studentToken = await loginAsWorldStudent(request, world);
    const due = new Date();
    due.setDate(due.getDate() + 7);
    const response = await request.post(`/api/offerings/${world.pscId}/assignments`, {
      data: {
        pscId: world.pscId,
        title: 'Unauthorized',
        submissionType: 'TEXT',
        totalMarks: 10,
        dueDate: due.toISOString(),
      },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(403);
  });

  test('teacher can list assignments for an offering', async ({ api, world }) => {
    await createAssignment(api, world.teacherToken, world.pscId, buildAssignment());

    const list = await api.get<unknown[]>(`/offerings/${world.pscId}/assignments`, world.teacherToken);
    expect(Array.isArray(list)).toBe(true);
    expect(list.length).toBeGreaterThanOrEqual(1);
  });

  test('can fetch an assignment by id', async ({ api, world }) => {
    const created = await createAssignment(
      api, world.teacherToken, world.pscId, buildAssignment({ title: 'Fetch me' }),
    );

    const fetched = await api.get<{ id: string; title: string }>(
      `/assignments/${created.id}`,
      world.teacherToken,
    );
    expect(fetched.id).toBe(created.id);
    expect(fetched.title).toBe('Fetch me');
  });

  test('teacher can update an assignment', async ({ api, request, world }) => {
    const created = await createAssignment(api, world.teacherToken, world.pscId, buildAssignment());
    const due = new Date();
    due.setDate(due.getDate() + 14);

    const response = await request.put(`/api/assignments/${created.id}`, {
      data: {
        title: 'Updated Assignment',
        submissionType: 'BOTH',
        totalMarks: 75,
        dueDate: due.toISOString(),
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.title).toBe('Updated Assignment');
    expect(body.submissionType).toBe('BOTH');
  });

  test('teacher can delete an assignment', async ({ api, request, world }) => {
    const created = await createAssignment(api, world.teacherToken, world.pscId, buildAssignment());

    const response = await request.delete(`/api/assignments/${created.id}`, {
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(204);
  });
});

// ---------------------------------------------------------------------------
// Assignment Submissions
// ---------------------------------------------------------------------------

test.describe('Assessment — Assignment Submissions', () => {
  test('enrolled student can submit a text assignment', async ({ api, request, world }) => {
    const assignment = await createAssignment(
      api, world.teacherToken, world.pscId,
      buildAssignment({ submissionType: 'TEXT' }),
    );
    const studentToken = await loginAsWorldStudent(request, world);

    const response = await request.post(`/api/assignments/${assignment.id}/submit`, {
      data: { textContent: 'This is my answer to the question.' },
      headers: { Authorization: `Bearer ${studentToken}` },
    });

    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.id).toBeTruthy();
    expect(body.assignmentId).toBe(assignment.id);
    expect(body.textContent).toBe('This is my answer to the question.');
    expect(body.status).toBeTruthy();
  });

  test('student can retrieve their own submission', async ({ api, request, world }) => {
    const assignment = await createAssignment(
      api, world.teacherToken, world.pscId,
      buildAssignment({ submissionType: 'TEXT' }),
    );
    const studentToken = await loginAsWorldStudent(request, world);

    await request.post(`/api/assignments/${assignment.id}/submit`, {
      data: { textContent: 'My submitted answer.' },
      headers: { Authorization: `Bearer ${studentToken}` },
    });

    const response = await request.get(`/api/me/assignments/${assignment.id}/submission`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.assignmentId).toBe(assignment.id);
    expect(body.textContent).toBe('My submitted answer.');
  });

  test('teacher can view all submissions for an assignment', async ({ api, request, world }) => {
    const assignment = await createAssignment(
      api, world.teacherToken, world.pscId,
      buildAssignment({ submissionType: 'TEXT' }),
    );
    const studentToken = await loginAsWorldStudent(request, world);
    await request.post(`/api/assignments/${assignment.id}/submit`, {
      data: { textContent: 'My work.' },
      headers: { Authorization: `Bearer ${studentToken}` },
    });

    const subs = await api.get<unknown[]>(
      `/assignments/${assignment.id}/submissions`,
      world.teacherToken,
    );
    expect(Array.isArray(subs)).toBe(true);
    expect(subs.length).toBeGreaterThanOrEqual(1);
  });

  test('teacher can grade a student submission', async ({ api, request, world }) => {
    const assignment = await createAssignment(
      api, world.teacherToken, world.pscId,
      buildAssignment({ submissionType: 'TEXT', totalMarks: 100 }),
    );
    const studentToken = await loginAsWorldStudent(request, world);
    const submitRes = await request.post(`/api/assignments/${assignment.id}/submit`, {
      data: { textContent: 'Answer.' },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    const { id: submissionId } = await submitRes.json();

    const gradeRes = await request.post(
      `/api/submissions/assignments/${submissionId}/grade`,
      {
        data: { marksObtained: 85, feedback: 'Well done!' },
        headers: { Authorization: `Bearer ${world.teacherToken}` },
      },
    );

    expect(gradeRes.status()).toBe(200);
    const body = await gradeRes.json();
    expect(Number(body.marksObtained)).toBe(85);
    expect(body.feedback).toBe('Well done!');
    expect(body.gradedBy).toBeTruthy();
  });

  test('grading marks exceeding totalMarks should be rejected', async ({ api, request, world }) => {
    const assignment = await createAssignment(
      api, world.teacherToken, world.pscId,
      buildAssignment({ submissionType: 'TEXT', totalMarks: 10 }),
    );
    const studentToken = await loginAsWorldStudent(request, world);
    const submitRes = await request.post(`/api/assignments/${assignment.id}/submit`, {
      data: { textContent: 'Answer.' },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    const { id: submissionId } = await submitRes.json();

    const gradeRes = await request.post(
      `/api/submissions/assignments/${submissionId}/grade`,
      {
        data: { marksObtained: 999 },
        headers: { Authorization: `Bearer ${world.teacherToken}` },
      },
    );
    // Backend should reject marks > totalMarks
    expect(gradeRes.status()).toBeGreaterThanOrEqual(400);
  });

  test('student cannot grade a submission (403)', async ({ api, request, world }) => {
    const assignment = await createAssignment(
      api, world.teacherToken, world.pscId,
      buildAssignment({ submissionType: 'TEXT' }),
    );
    const studentToken = await loginAsWorldStudent(request, world);
    const submitRes = await request.post(`/api/assignments/${assignment.id}/submit`, {
      data: { textContent: 'Answer.' },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    const { id: submissionId } = await submitRes.json();

    const gradeRes = await request.post(
      `/api/submissions/assignments/${submissionId}/grade`,
      {
        data: { marksObtained: 5 },
        headers: { Authorization: `Bearer ${studentToken}` },
      },
    );
    expect(gradeRes.status()).toBe(403);
  });
});

