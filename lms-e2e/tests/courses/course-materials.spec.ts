import { test, expect } from '../../fixtures';

// ---------------------------------------------------------------------------
// Course Materials — TEACHER creates, STUDENT reads
// All tests use the `world` fixture which provides a seeded offering (pscId),
// teacher token, and an enrolled student.
// ---------------------------------------------------------------------------

test.describe('Courses — Course Materials (create)', () => {
  test('teacher can post an ANNOUNCEMENT material', async ({ request, world }) => {
    const response = await request.post(`/api/offerings/${world.pscId}/materials`, {
      data: {
        type: 'ANNOUNCEMENT',
        title: 'Welcome to the course',
        description: 'Please read the syllabus',
        content: {},
        visible: true,
        orderIndex: 1,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.id).toBeTruthy();
    expect(body.type).toBe('ANNOUNCEMENT');
    expect(body.title).toBe('Welcome to the course');
    expect(body.pscId).toBe(world.pscId);
  });

  test('teacher can post a URL material with content payload', async ({ request, world }) => {
    const response = await request.post(`/api/offerings/${world.pscId}/materials`, {
      data: {
        type: 'URL',
        title: 'Lecture Slides',
        content: { url: 'https://example.com/slides.pdf', label: 'Week 1 Slides' },
        visible: true,
        orderIndex: 2,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.type).toBe('URL');
    expect(body.content).toMatchObject({ url: 'https://example.com/slides.pdf' });
  });

  test('teacher can post a VIDEO_LINK material', async ({ request, world }) => {
    const response = await request.post(`/api/offerings/${world.pscId}/materials`, {
      data: {
        type: 'VIDEO_LINK',
        title: 'Intro lecture recording',
        content: { url: 'https://example.com/video.mp4' },
        visible: true,
        orderIndex: 3,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    expect(response.status()).toBe(201);
  });

  test('invalid material type returns 400', async ({ request, world }) => {
    const response = await request.post(`/api/offerings/${world.pscId}/materials`, {
      data: {
        type: 'INVALID_TYPE',
        title: 'Bad material',
        content: {},
        visible: true,
        orderIndex: 1,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('missing title returns 400', async ({ request, world }) => {
    const response = await request.post(`/api/offerings/${world.pscId}/materials`, {
      data: { type: 'ANNOUNCEMENT', content: {}, visible: true, orderIndex: 1 },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('student cannot create a material (403)', async ({ request, world }) => {
    const { accessToken: studentToken } = await (
      await request.post('/api/auth/login', {
        data: { email: world.studentEmail, password: world.studentPassword },
      })
    ).json();

    const response = await request.post(`/api/offerings/${world.pscId}/materials`, {
      data: {
        type: 'ANNOUNCEMENT',
        title: 'Unauthorized post',
        content: {},
        visible: true,
        orderIndex: 1,
      },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(403);
  });
});

test.describe('Courses — Course Materials (read)', () => {
  test('teacher can list materials for their offering', async ({ request, world }) => {
    // Post one material first
    await request.post(`/api/offerings/${world.pscId}/materials`, {
      data: {
        type: 'ANNOUNCEMENT',
        title: 'Listable material',
        content: {},
        visible: true,
        orderIndex: 1,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    const listRes = await request.get(`/api/offerings/${world.pscId}/materials`, {
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    expect(listRes.status()).toBe(200);
    const materials = await listRes.json();
    expect(Array.isArray(materials)).toBe(true);
    expect(materials.length).toBeGreaterThanOrEqual(1);
  });

  test('enrolled student can list visible materials', async ({ request, world }) => {
    // Post a visible material
    await request.post(`/api/offerings/${world.pscId}/materials`, {
      data: {
        type: 'ANNOUNCEMENT',
        title: 'Student-visible material',
        content: {},
        visible: true,
        orderIndex: 1,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    const { accessToken: studentToken } = await (
      await request.post('/api/auth/login', {
        data: { email: world.studentEmail, password: world.studentPassword },
      })
    ).json();

    const listRes = await request.get(
      `/api/offerings/${world.pscId}/materials?visibleOnly=true`,
      { headers: { Authorization: `Bearer ${studentToken}` } },
    );

    expect(listRes.status()).toBe(200);
    const materials = await listRes.json();
    expect(Array.isArray(materials)).toBe(true);
  });
});

test.describe('Courses — Course Materials (update + delete)', () => {
  test('teacher can update a material title', async ({ request, world }) => {
    const createRes = await request.post(`/api/offerings/${world.pscId}/materials`, {
      data: {
        type: 'ANNOUNCEMENT',
        title: 'Original title',
        content: {},
        visible: true,
        orderIndex: 1,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    const { id } = await createRes.json();

    const updateRes = await request.put(`/api/materials/${id}`, {
      data: {
        type: 'ANNOUNCEMENT',
        title: 'Updated title',
        content: {},
        visible: true,
        orderIndex: 1,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });

    expect(updateRes.status()).toBe(200);
    const body = await updateRes.json();
    expect(body.title).toBe('Updated title');
  });

  test('teacher can delete a material', async ({ request, world }) => {
    const createRes = await request.post(`/api/offerings/${world.pscId}/materials`, {
      data: {
        type: 'ANNOUNCEMENT',
        title: 'To be deleted',
        content: {},
        visible: false,
        orderIndex: 99,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    const { id } = await createRes.json();

    const deleteRes = await request.delete(`/api/materials/${id}`, {
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(deleteRes.status()).toBe(204);
  });
});

