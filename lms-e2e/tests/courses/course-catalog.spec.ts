import { test, expect } from '../../fixtures';
import { buildCourse, createCourse } from '../../fixtures/factories/course.factory';

// ---------------------------------------------------------------------------
// Admin: course CRUD
// ---------------------------------------------------------------------------

test.describe('Courses — Course Catalog (admin CRUD)', () => {
  test('admin can create a course and response has required fields', async ({
    api,
    adminToken,
  }) => {
    const payload = buildCourse({ name: 'Introduction to Algorithms', creditHours: 3 });
    const course = await createCourse(api, adminToken, payload);

    expect(course.id).toBeTruthy();
    expect(course.code).toBe(payload.code);
    expect(course.name).toBe(payload.name);
    expect(course.creditHours).toBe(payload.creditHours);
    expect(course.status).toBeTruthy();
  });

  test('duplicate course code returns 409', async ({ api, adminToken, request }) => {
    const payload = buildCourse();
    await createCourse(api, adminToken, payload); // first — succeeds

    const response = await request.post('/api/admin/courses', {
      data: payload,
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(409);
  });

  test('course code with lowercase letters returns 400', async ({ request, adminToken }) => {
    const response = await request.post('/api/admin/courses', {
      data: { code: 'cs101', name: 'Test', creditHours: 3 },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('missing required fields returns 400', async ({ request, adminToken }) => {
    const response = await request.post('/api/admin/courses', {
      data: { name: 'No code course' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('student cannot create a course (403)', async ({ request, studentToken }) => {
    const payload = buildCourse();
    const response = await request.post('/api/admin/courses', {
      data: payload,
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(403);
  });

  test('admin can update a course', async ({ api, adminToken, request }) => {
    const course = await createCourse(api, adminToken, buildCourse());

    const response = await request.put(`/api/admin/courses/${course.id}`, {
      data: {
        code: course.code,
        name: 'Updated Course Name',
        creditHours: 4,
      },
      headers: { Authorization: `Bearer ${adminToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.name).toBe('Updated Course Name');
    expect(body.creditHours).toBe(4);
  });

  test('admin can change course status to INACTIVE', async ({ api, adminToken, request }) => {
    const course = await createCourse(api, adminToken, buildCourse());

    const response = await request.patch(`/api/admin/courses/${course.id}/status`, {
      data: { status: 'INACTIVE' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('INACTIVE');
  });

  test('invalid status value returns 400', async ({ api, adminToken, request }) => {
    const course = await createCourse(api, adminToken, buildCourse());

    const response = await request.patch(`/api/admin/courses/${course.id}/status`, {
      data: { status: 'DELETED' },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(400);
  });
});

// ---------------------------------------------------------------------------
// Read-only: list + detail
// ---------------------------------------------------------------------------

test.describe('Courses — Course Catalog (read access)', () => {
  test('authenticated user can list courses (paginated)', async ({ api, studentToken }) => {
    const result = await api.get<{ content: unknown[]; totalElements: number }>(
      '/courses',
      studentToken,
    );

    // Spring returns a Page object with a `content` array
    expect(Array.isArray(result.content)).toBe(true);
    expect(typeof result.totalElements).toBe('number');
  });

  test('course list includes a previously created course', async ({ api, adminToken }) => {
    const created = await createCourse(api, adminToken, buildCourse());

    const result = await api.get<{ content: Array<{ id: string }> }>(
      '/courses',
      adminToken,
    );

    const found = result.content.find((c) => c.id === created.id);
    expect(found).toBeDefined();
  });

  test('can fetch a course by id', async ({ api, adminToken }) => {
    const created = await createCourse(api, adminToken, buildCourse());

    const detail = await api.get<{ id: string; code: string; name: string }>(
      `/courses/${created.id}`,
      adminToken,
    );

    expect(detail.id).toBe(created.id);
    expect(detail.code).toBe(created.code);
    expect(detail.name).toBe(created.name);
  });

  test('fetching unknown course id returns 404', async ({ request, adminToken }) => {
    const fakeId = '00000000-0000-0000-0000-000000000001';
    const response = await request.get(`/api/courses/${fakeId}`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(404);
  });

  test('unauthenticated request to course list returns 401', async ({ request }) => {
    const response = await request.get('/api/courses');
    expect(response.status()).toBe(401);
  });
});

// ---------------------------------------------------------------------------
// CLO management
// ---------------------------------------------------------------------------

test.describe('Courses — CLO Management', () => {
  test('admin can create a CLO for a course', async ({ api, adminToken, request }) => {
    const course = await createCourse(api, adminToken, buildCourse());

    const response = await request.post(`/api/admin/courses/${course.id}/clos`, {
      data: { code: 'CLO1', title: 'Understand algorithms', orderIndex: 1 },
      headers: { Authorization: `Bearer ${adminToken}` },
    });

    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.code).toBe('CLO1');
    expect(body.title).toBe('Understand algorithms');
    expect(body.id).toBeTruthy();
  });

  test('can list CLOs for a course', async ({ api, adminToken, request }) => {
    const course = await createCourse(api, adminToken, buildCourse());
    // Add one CLO
    await request.post(`/api/admin/courses/${course.id}/clos`, {
      data: { code: 'CLO1', title: 'First outcome', orderIndex: 1 },
      headers: { Authorization: `Bearer ${adminToken}` },
    });

    const clos = await api.get<unknown[]>(`/courses/${course.id}/clos`, adminToken);
    expect(Array.isArray(clos)).toBe(true);
    expect(clos.length).toBeGreaterThanOrEqual(1);
  });

  test('admin can delete a CLO', async ({ api, adminToken, request }) => {
    const course = await createCourse(api, adminToken, buildCourse());
    const cloRes = await request.post(`/api/admin/courses/${course.id}/clos`, {
      data: { code: 'CLO1', title: 'To be deleted', orderIndex: 1 },
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const { id: cloId } = await cloRes.json();

    const deleteRes = await request.delete(
      `/api/admin/courses/${course.id}/clos/${cloId}`,
      { headers: { Authorization: `Bearer ${adminToken}` } },
    );
    expect(deleteRes.status()).toBe(204);
  });

  test('student cannot create a CLO (403)', async ({ api, adminToken, studentToken, request }) => {
    const course = await createCourse(api, adminToken, buildCourse());

    const response = await request.post(`/api/admin/courses/${course.id}/clos`, {
      data: { code: 'CLO1', title: 'Outcome', orderIndex: 1 },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(403);
  });
});

// ---------------------------------------------------------------------------
// Offerings (PSC)
// ---------------------------------------------------------------------------

test.describe('Courses — Offerings', () => {
  test('seeded offering is retrievable by id', async ({ api, world }) => {
    const offering = await api.get<{ id: string; courseId: string }>(
      `/offerings/${world.pscId}`,
      world.teacherToken,
    );

    expect(offering.id).toBe(world.pscId);
    expect(offering.courseId).toBe(world.courseId);
  });

  test('can list offerings for a semester', async ({ api, world, adminToken }) => {
    const offerings = await api.get<unknown[]>(
      `/semesters/${world.semesterId}/offerings`,
      adminToken,
    );

    expect(Array.isArray(offerings)).toBe(true);
    const found = (offerings as Array<{ id: string }>).find((o) => o.id === world.pscId);
    expect(found).toBeDefined();
  });

  test('can list offerings for a course', async ({ api, world, adminToken }) => {
    const offerings = await api.get<unknown[]>(
      `/courses/${world.courseId}/offerings`,
      adminToken,
    );

    expect(Array.isArray(offerings)).toBe(true);
    const found = (offerings as Array<{ id: string }>).find((o) => o.id === world.pscId);
    expect(found).toBeDefined();
  });

  test('student cannot create an offering (403)', async ({ request, studentToken }) => {
    const response = await request.post('/api/admin/offerings', {
      data: {
        semesterId: '00000000-0000-0000-0000-000000000000',
        courseId:   '00000000-0000-0000-0000-000000000000',
        teacherId:  '00000000-0000-0000-0000-000000000000',
        maxCapacity: 30,
      },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(403);
  });
});

