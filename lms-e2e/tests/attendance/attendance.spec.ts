import { test, expect } from '../../fixtures';
import { createSession, buildSession } from '../../fixtures/factories/session.factory';

// Helper: log in as the seeded world student.
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
// Sessions — CRUD
// ---------------------------------------------------------------------------

test.describe('Attendance — Sessions (CRUD)', () => {
  test('teacher can create a session and response has required fields', async ({
    api,
    world,
  }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));

    expect(session.id).toBeTruthy();
    expect(session.pscId).toBe(world.pscId);
    expect(session.sessionDate).toBeTruthy();
    expect(session.open).toBe(true);
    expect(session.createdBy).toBeTruthy();
  });

  test('session topic is stored correctly', async ({ api, world }) => {
    const session = await createSession(
      api,
      world.teacherToken,
      buildSession(world.pscId, { topic: 'Introduction to Algorithms' }),
    );
    expect(session.topic).toBe('Introduction to Algorithms');
  });

  test('missing pscId returns 400', async ({ request, world }) => {
    const response = await request.post('/api/sessions', {
      data: { sessionDate: new Date().toISOString().slice(0, 10) },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('missing sessionDate returns 400', async ({ request, world }) => {
    const response = await request.post('/api/sessions', {
      data: { pscId: world.pscId },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('student cannot create a session (403)', async ({ request, world }) => {
    const studentToken = await loginAsWorldStudent(request, world);
    const response = await request.post('/api/sessions', {
      data: buildSession(world.pscId),
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(403);
  });

  test('teacher can list sessions for an offering', async ({ api, world }) => {
    await createSession(api, world.teacherToken, buildSession(world.pscId));

    const sessions = await api.get<unknown[]>(
      `/offerings/${world.pscId}/sessions`,
      world.teacherToken,
    );
    expect(Array.isArray(sessions)).toBe(true);
    expect(sessions.length).toBeGreaterThanOrEqual(1);
  });

  test('created session appears in listing', async ({ api, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));

    const sessions = await api.get<Array<{ id: string }>>(
      `/offerings/${world.pscId}/sessions`,
      world.teacherToken,
    );
    const found = sessions.find((s) => s.id === session.id);
    expect(found).toBeDefined();
  });

  test('can fetch a session by id', async ({ api, world }) => {
    const created = await createSession(api, world.teacherToken, buildSession(world.pscId));

    const fetched = await api.get<{ id: string; pscId: string; open: boolean }>(
      `/sessions/${created.id}`,
      world.teacherToken,
    );
    expect(fetched.id).toBe(created.id);
    expect(fetched.pscId).toBe(world.pscId);
  });

  test('teacher can close a session', async ({ api, request, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));

    const response = await request.post(`/api/sessions/${session.id}/close`, {
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.open).toBe(false);
    expect(body.closedAt).toBeTruthy();
  });

  test('student cannot list sessions for an offering (403)', async ({ request, world }) => {
    const studentToken = await loginAsWorldStudent(request, world);
    const response = await request.get(`/api/offerings/${world.pscId}/sessions`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(403);
  });
});

// ---------------------------------------------------------------------------
// Records — mark individual + bulk
// ---------------------------------------------------------------------------

test.describe('Attendance — Records (mark + bulk)', () => {
  test('teacher can mark a student as PRESENT', async ({ api, request, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));

    const response = await request.put(
      `/api/sessions/${session.id}/records/${world.studentId}`,
      {
        data: { status: 'PRESENT' },
        headers: { Authorization: `Bearer ${world.teacherToken}` },
      },
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('PRESENT');
    expect(body.studentId).toBe(world.studentId);
  });

  test('teacher can mark a student as ABSENT', async ({ api, request, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));

    const response = await request.put(
      `/api/sessions/${session.id}/records/${world.studentId}`,
      {
        data: { status: 'ABSENT', remarks: 'No notification received' },
        headers: { Authorization: `Bearer ${world.teacherToken}` },
      },
    );

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('ABSENT');
    expect(body.remarks).toBe('No notification received');
  });

  test('teacher can mark a student as LATE or EXCUSED', async ({ api, request, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));

    for (const status of ['LATE', 'EXCUSED'] as const) {
      const response = await request.put(
        `/api/sessions/${session.id}/records/${world.studentId}`,
        {
          data: { status },
          headers: { Authorization: `Bearer ${world.teacherToken}` },
        },
      );
      expect(response.status()).toBe(200);
      expect((await response.json()).status).toBe(status);
    }
  });

  test('invalid attendance status returns 400', async ({ api, request, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));

    const response = await request.put(
      `/api/sessions/${session.id}/records/${world.studentId}`,
      {
        data: { status: 'MAYBE' },
        headers: { Authorization: `Bearer ${world.teacherToken}` },
      },
    );
    expect(response.status()).toBe(400);
  });

  test('student cannot mark attendance (403)', async ({ api, request, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));
    const studentToken = await loginAsWorldStudent(request, world);

    const response = await request.put(
      `/api/sessions/${session.id}/records/${world.studentId}`,
      {
        data: { status: 'PRESENT' },
        headers: { Authorization: `Bearer ${studentToken}` },
      },
    );
    expect(response.status()).toBe(403);
  });

  test('teacher can bulk-mark all students in a session', async ({ api, request, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));

    const response = await request.post(
      `/api/sessions/${session.id}/records/bulk`,
      {
        data: {
          records: [
            { studentId: world.studentId, status: 'PRESENT' },
          ],
        },
        headers: { Authorization: `Bearer ${world.teacherToken}` },
      },
    );

    expect(response.status()).toBe(200);
    const records = await response.json();
    expect(Array.isArray(records)).toBe(true);
    expect(records).toHaveLength(1);
    expect(records[0].status).toBe('PRESENT');
  });

  test('bulk mark with empty records array returns 400', async ({ api, request, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));

    const response = await request.post(
      `/api/sessions/${session.id}/records/bulk`,
      {
        data: { records: [] },
        headers: { Authorization: `Bearer ${world.teacherToken}` },
      },
    );
    expect(response.status()).toBe(400);
  });

  test('bulk mark with invalid status returns 400', async ({ api, request, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));

    const response = await request.post(
      `/api/sessions/${session.id}/records/bulk`,
      {
        data: {
          records: [{ studentId: world.studentId, status: 'UNKNOWN' }],
        },
        headers: { Authorization: `Bearer ${world.teacherToken}` },
      },
    );
    expect(response.status()).toBe(400);
  });

  test('teacher can list all records for a session', async ({ api, request, world }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));
    // Mark one record
    await request.put(`/api/sessions/${session.id}/records/${world.studentId}`, {
      data: { status: 'PRESENT' },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    const records = await api.get<unknown[]>(
      `/sessions/${session.id}/records`,
      world.teacherToken,
    );
    expect(Array.isArray(records)).toBe(true);
    expect(records.length).toBeGreaterThanOrEqual(1);
  });
});

// ---------------------------------------------------------------------------
// Attendance summaries
// ---------------------------------------------------------------------------

test.describe('Attendance — Summaries', () => {
  test('teacher can view student attendance summary for an offering', async ({
    api,
    request,
    world,
  }) => {
    // Create session + mark student PRESENT so the summary has data
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));
    await request.put(`/api/sessions/${session.id}/records/${world.studentId}`, {
      data: { status: 'PRESENT' },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    const summary = await api.get<{
      pscId: string;
      studentId: string;
      attended: number;
      total: number;
      percentage: number;
    }>(`/offerings/${world.pscId}/attendance/${world.studentId}`, world.teacherToken);

    expect(summary.pscId).toBe(world.pscId);
    expect(summary.studentId).toBe(world.studentId);
    expect(summary.total).toBeGreaterThanOrEqual(1);
    expect(summary.attended).toBeGreaterThanOrEqual(1);
    expect(summary.percentage).toBeGreaterThanOrEqual(0);
  });

  test('enrolled student can view their own attendance summary', async ({
    api,
    request,
    world,
  }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));
    await request.put(`/api/sessions/${session.id}/records/${world.studentId}`, {
      data: { status: 'PRESENT' },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    const studentToken = await loginAsWorldStudent(request, world);

    const summary = await api.get<{
      pscId: string;
      studentId: string;
      attended: number;
      total: number;
      percentage: number;
    }>(`/me/attendance/${world.pscId}`, studentToken);

    expect(summary.pscId).toBe(world.pscId);
    expect(summary.attended).toBeGreaterThanOrEqual(1);
    expect(summary.percentage).toBeGreaterThanOrEqual(0);
    expect(summary.percentage).toBeLessThanOrEqual(100);
  });

  test('teacher token on /me/attendance returns 403 (STUDENT only)', async ({
    request,
    world,
  }) => {
    const response = await request.get(`/api/me/attendance/${world.pscId}`, {
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(403);
  });

  test('admin can view student attendance records across all offerings', async ({
    api,
    request,
    world,
    adminToken,
  }) => {
    const session = await createSession(api, world.teacherToken, buildSession(world.pscId));
    await request.put(`/api/sessions/${session.id}/records/${world.studentId}`, {
      data: { status: 'PRESENT' },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    const records = await api.get<unknown[]>(
      `/admin/students/${world.studentId}/attendance`,
      adminToken,
    );
    expect(Array.isArray(records)).toBe(true);
    expect(records.length).toBeGreaterThanOrEqual(1);
  });

  test('student cannot access admin attendance records (403)', async ({
    request,
    world,
  }) => {
    const studentToken = await loginAsWorldStudent(request, world);
    const response = await request.get(
      `/api/admin/students/${world.studentId}/attendance`,
      { headers: { Authorization: `Bearer ${studentToken}` } },
    );
    expect(response.status()).toBe(403);
  });
});
