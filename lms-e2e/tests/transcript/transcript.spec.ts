import { test, expect } from '../../fixtures';

// ---------------------------------------------------------------------------
// Grading Scales — CRUD (ADMIN only)
// ---------------------------------------------------------------------------

test.describe('Grading Scales — CRUD', () => {
  test('admin can create a grading scale', async ({ request, adminToken }) => {
    const response = await request.post('/api/admin/grading-scales', {
      data: { name: 'Standard Scale' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.id).toBeTruthy();
    expect(body.name).toBe('Standard Scale');
    expect(typeof body.isDefault).toBe('boolean');
  });

  test('missing name returns 400', async ({ request, adminToken }) => {
    const response = await request.post('/api/admin/grading-scales', {
      data: {},
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('student cannot create a grading scale (403)', async ({ request, studentToken }) => {
    const response = await request.post('/api/admin/grading-scales', {
      data: { name: 'Forbidden Scale' },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(403);
  });

  test('unauthenticated create returns 401', async ({ request }) => {
    const response = await request.post('/api/admin/grading-scales', {
      data: { name: 'Anon Scale' },
    });
    expect(response.status()).toBe(401);
  });

  test('admin can list global grading scales', async ({ request, adminToken }) => {
    // Ensure at least one exists
    await request.post('/api/admin/grading-scales', {
      data: { name: 'List Scale' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });

    const response = await request.get('/api/admin/grading-scales', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(200);
    const scales = await response.json();
    expect(Array.isArray(scales)).toBe(true);
    expect(scales.length).toBeGreaterThanOrEqual(1);
  });

  test('admin can list scales for a specific program', async ({
    request,
    adminToken,
    world,
  }) => {
    await request.post('/api/admin/grading-scales', {
      data: { name: 'Program Scale', programId: world.programId },
      headers: { Authorization: `Bearer ${adminToken}` },
    });

    const response = await request.get(
      `/api/admin/grading-scales?programId=${world.programId}`,
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );
    expect(response.status()).toBe(200);
    const scales = await response.json();
    expect(Array.isArray(scales)).toBe(true);
    expect(scales.length).toBeGreaterThanOrEqual(1);
    expect(scales[0].programId).toBe(world.programId);
  });

  test('admin can fetch a grading scale by id', async ({ request, adminToken }) => {
    const create = await request.post('/api/admin/grading-scales', {
      data: { name: 'Fetch Me Scale' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const { id } = await create.json();

    const response = await request.get(`/api/admin/grading-scales/${id}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.id).toBe(id);
    expect(body.name).toBe('Fetch Me Scale');
  });

  test('unknown grading scale id returns 404', async ({ request, adminToken }) => {
    const response = await request.get(
      '/api/admin/grading-scales/00000000-0000-0000-0000-000000000000',
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );
    expect(response.status()).toBe(404);
  });

  test('admin can update a grading scale name', async ({ request, adminToken }) => {
    const create = await request.post('/api/admin/grading-scales', {
      data: { name: 'Old Name' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const { id } = await create.json();

    const response = await request.put(`/api/admin/grading-scales/${id}`, {
      data: { name: 'New Name' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(200);
    expect((await response.json()).name).toBe('New Name');
  });

  test('admin can add an entry to a grading scale', async ({ request, adminToken }) => {
    const create = await request.post('/api/admin/grading-scales', {
      data: { name: 'Entry Scale' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const { id: scaleId } = await create.json();

    const response = await request.post(`/api/admin/grading-scales/${scaleId}/entries`, {
      data: {
        gradeLetter: 'A',
        minPercentage: 90,
        maxPercentage: 100,
        gradePoints: 4.0,
        orderIndex: 1,
      },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(201);
    const entry = await response.json();
    expect(entry.id).toBeTruthy();
    expect(entry.gradeLetter).toBe('A');
    expect(entry.scaleId).toBe(scaleId);
    expect(Number(entry.gradePoints)).toBe(4.0);
  });

  test('entry missing required fields returns 400', async ({ request, adminToken }) => {
    const create = await request.post('/api/admin/grading-scales', {
      data: { name: 'Validation Scale' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const { id: scaleId } = await create.json();

    const response = await request.post(`/api/admin/grading-scales/${scaleId}/entries`, {
      data: { gradeLetter: 'B' }, // missing minPercentage, maxPercentage, gradePoints
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('admin can remove an entry from a grading scale', async ({ request, adminToken }) => {
    const create = await request.post('/api/admin/grading-scales', {
      data: { name: 'Remove Entry Scale' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const { id: scaleId } = await create.json();

    const addEntry = await request.post(`/api/admin/grading-scales/${scaleId}/entries`, {
      data: {
        gradeLetter: 'C',
        minPercentage: 70,
        maxPercentage: 79,
        gradePoints: 2.0,
        orderIndex: 1,
      },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const { id: entryId } = await addEntry.json();

    const response = await request.delete(
      `/api/admin/grading-scales/${scaleId}/entries/${entryId}`,
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );
    expect(response.status()).toBe(204);
  });

  test('admin can delete a grading scale', async ({ request, adminToken }) => {
    const create = await request.post('/api/admin/grading-scales', {
      data: { name: 'Delete Me Scale' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const { id } = await create.json();

    const del = await request.delete(`/api/admin/grading-scales/${id}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(del.status()).toBe(204);

    // Confirm it is gone
    const get = await request.get(`/api/admin/grading-scales/${id}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(get.status()).toBe(404);
  });
});

// ---------------------------------------------------------------------------
// Transcripts — endpoints & auth
// ---------------------------------------------------------------------------

test.describe('Transcripts — endpoints & auth', () => {
  test('admin can trigger transcript regeneration for a semester (202 Accepted)', async ({
    request,
    adminToken,
    world,
  }) => {
    const response = await request.post(
      `/api/admin/transcripts/semester/${world.semesterId}/generate?programId=${world.programId}`,
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );
    expect(response.status()).toBe(202);
  });

  test('student cannot trigger transcript regeneration (403)', async ({
    request,
    studentToken,
    world,
  }) => {
    const response = await request.post(
      `/api/admin/transcripts/semester/${world.semesterId}/generate?programId=${world.programId}`,
      { headers: { Authorization: `Bearer ${studentToken}` } },
    );
    expect(response.status()).toBe(403);
  });

  test('unauthenticated transcript trigger returns 401', async ({ request, world }) => {
    const response = await request.post(
      `/api/admin/transcripts/semester/${world.semesterId}/generate?programId=${world.programId}`,
    );
    expect(response.status()).toBe(401);
  });

  test('admin can list transcripts for a semester (returns array)', async ({
    request,
    adminToken,
    world,
  }) => {
    const response = await request.get(
      `/api/admin/transcripts/semester/${world.semesterId}`,
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('student cannot list semester transcripts (403)', async ({
    request,
    studentToken,
    world,
  }) => {
    const response = await request.get(
      `/api/admin/transcripts/semester/${world.semesterId}`,
      { headers: { Authorization: `Bearer ${studentToken}` } },
    );
    expect(response.status()).toBe(403);
  });

  test('admin can list transcripts for a student (returns array)', async ({
    request,
    adminToken,
    world,
  }) => {
    const response = await request.get(
      `/api/admin/students/${world.studentId}/transcripts`,
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('teacher can list transcripts for a student (returns array)', async ({
    request,
    world,
  }) => {
    const response = await request.get(
      `/api/admin/students/${world.studentId}/transcripts`,
      { headers: { Authorization: `Bearer ${world.teacherToken}` } },
    );
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('student can view own transcript list via /me/transcripts', async ({
    request,
    studentToken,
  }) => {
    const response = await request.get('/api/me/transcripts', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('teacher cannot access /me/transcripts (403 — STUDENT only)', async ({
    request,
    world,
  }) => {
    const response = await request.get('/api/me/transcripts', {
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(403);
  });

  test('unauthenticated /me/transcripts returns 401', async ({ request }) => {
    const response = await request.get('/api/me/transcripts');
    expect(response.status()).toBe(401);
  });

  test('fetching unknown transcript id returns 404', async ({ request, adminToken }) => {
    const response = await request.get(
      '/api/transcripts/00000000-0000-0000-0000-000000000000',
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );
    expect(response.status()).toBe(404);
  });

  test('fetching unknown transcript pdf returns 404', async ({ request, adminToken }) => {
    const response = await request.get(
      '/api/transcripts/00000000-0000-0000-0000-000000000000/pdf',
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );
    expect(response.status()).toBe(404);
  });

  test('unauthenticated transcript detail returns 401', async ({ request }) => {
    const response = await request.get(
      '/api/transcripts/00000000-0000-0000-0000-000000000000',
    );
    expect(response.status()).toBe(401);
  });
});
