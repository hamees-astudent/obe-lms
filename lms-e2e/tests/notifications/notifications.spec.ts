import { test, expect } from '../../fixtures';
import { createAssignment, buildAssignment } from '../../fixtures/factories/assignment.factory';

interface NotificationPage {
  content: NotificationItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  unreadCount: number;
}

interface NotificationItem {
  id: string;
  recipientId: string;
  title: string;
  body: string;
  eventType: string;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

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
// Core endpoint shape & auth
// ---------------------------------------------------------------------------

test.describe('Notifications — core endpoints', () => {
  test('authenticated user gets paginated notification feed with correct shape', async ({
    request,
    studentToken,
  }) => {
    const response = await request.get('/api/notifications', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(200);
    const body = (await response.json()) as NotificationPage;

    expect(Array.isArray(body.content)).toBe(true);
    expect(typeof body.page).toBe('number');
    expect(typeof body.size).toBe('number');
    expect(typeof body.totalElements).toBe('number');
    expect(typeof body.totalPages).toBe('number');
    expect(typeof body.unreadCount).toBe('number');
  });

  test('default page is 0 and size is 20', async ({ request, studentToken }) => {
    const response = await request.get('/api/notifications', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    const body = (await response.json()) as NotificationPage;
    expect(body.page).toBe(0);
    expect(body.size).toBe(20);
  });

  test('custom page and size are respected', async ({ request, studentToken }) => {
    const response = await request.get('/api/notifications?page=0&size=5', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    const body = (await response.json()) as NotificationPage;
    expect(body.size).toBe(5);
    expect(body.content.length).toBeLessThanOrEqual(5);
  });

  test('size is capped at 100', async ({ request, studentToken }) => {
    const response = await request.get('/api/notifications?size=999', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    const body = (await response.json()) as NotificationPage;
    expect(body.size).toBeLessThanOrEqual(100);
  });

  test('unreadOnly=true returns a subset (no read items)', async ({
    request,
    studentToken,
  }) => {
    const response = await request.get('/api/notifications?unreadOnly=true', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(200);
    const body = (await response.json()) as NotificationPage;
    for (const item of body.content) {
      expect(item.isRead).toBe(false);
    }
  });

  test('unread-count endpoint returns numeric unreadCount', async ({
    request,
    studentToken,
  }) => {
    const response = await request.get('/api/notifications/unread-count', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(typeof body.unreadCount).toBe('number');
    expect(body.unreadCount).toBeGreaterThanOrEqual(0);
  });

  test('mark-all-as-read returns { updated: number }', async ({
    request,
    studentToken,
  }) => {
    const response = await request.put('/api/notifications/read-all', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(typeof body.updated).toBe('number');
    expect(body.updated).toBeGreaterThanOrEqual(0);
  });

  test('marking unknown notification id returns 404', async ({
    request,
    studentToken,
  }) => {
    const response = await request.put(
      '/api/notifications/00000000-0000-0000-0000-000000000000/read',
      { headers: { Authorization: `Bearer ${studentToken}` } },
    );
    expect(response.status()).toBe(404);
  });

  test('GET /api/notifications without auth returns 401', async ({ request }) => {
    expect((await request.get('/api/notifications')).status()).toBe(401);
  });

  test('GET /api/notifications/unread-count without auth returns 401', async ({ request }) => {
    expect((await request.get('/api/notifications/unread-count')).status()).toBe(401);
  });

  test('PUT /api/notifications/read-all without auth returns 401', async ({ request }) => {
    expect((await request.put('/api/notifications/read-all')).status()).toBe(401);
  });

  test('PUT /api/notifications/{id}/read without auth returns 401', async ({ request }) => {
    const response = await request.put(
      '/api/notifications/00000000-0000-0000-0000-000000000000/read',
    );
    expect(response.status()).toBe(401);
  });
});

// ---------------------------------------------------------------------------
// Notification lifecycle (triggered via assignment creation → Kafka → consumer)
// ---------------------------------------------------------------------------

test.describe('Notifications — lifecycle via Kafka events', () => {
  test('enrolled student receives notification when teacher posts an assignment', async ({
    api,
    request,
    world,
  }) => {
    const studentToken = await loginAsWorldStudent(request, world);

    // Trigger the notification by creating an assignment for the offering
    await createAssignment(api, world.teacherToken, world.pscId, buildAssignment());

    // Poll until the student's feed has at least one notification (Kafka is async)
    await expect
      .poll(
        async () => {
          const res = await request.get('/api/notifications', {
            headers: { Authorization: `Bearer ${studentToken}` },
          });
          return ((await res.json()) as NotificationPage).totalElements;
        },
        { timeout: 10_000, intervals: [500, 1000, 2000, 3000] },
      )
      .toBeGreaterThan(0);
  });

  test('notification item has the expected fields', async ({
    api,
    request,
    world,
  }) => {
    const studentToken = await loginAsWorldStudent(request, world);

    await createAssignment(api, world.teacherToken, world.pscId, buildAssignment());

    let notification: NotificationItem | undefined;
    await expect
      .poll(
        async () => {
          const res = await request.get('/api/notifications', {
            headers: { Authorization: `Bearer ${studentToken}` },
          });
          const page = (await res.json()) as NotificationPage;
          notification = page.content[0];
          return page.totalElements;
        },
        { timeout: 10_000, intervals: [500, 1000, 2000, 3000] },
      )
      .toBeGreaterThan(0);

    expect(notification!.id).toBeTruthy();
    expect(notification!.recipientId).toBeTruthy();
    expect(notification!.title).toBeTruthy();
    expect(typeof notification!.isRead).toBe('boolean');
    expect(notification!.createdAt).toBeTruthy();
  });

  test('unread-count increases after new notification arrives', async ({
    api,
    request,
    world,
  }) => {
    const studentToken = await loginAsWorldStudent(request, world);

    // Get baseline
    const beforeRes = await request.get('/api/notifications/unread-count', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    const before = (await beforeRes.json()).unreadCount as number;

    await createAssignment(api, world.teacherToken, world.pscId, buildAssignment());

    await expect
      .poll(
        async () => {
          const res = await request.get('/api/notifications/unread-count', {
            headers: { Authorization: `Bearer ${studentToken}` },
          });
          return (await res.json()).unreadCount as number;
        },
        { timeout: 10_000, intervals: [500, 1000, 2000, 3000] },
      )
      .toBeGreaterThan(before);
  });

  test('student can mark a notification as read', async ({
    api,
    request,
    world,
  }) => {
    const studentToken = await loginAsWorldStudent(request, world);

    await createAssignment(api, world.teacherToken, world.pscId, buildAssignment());

    let notifId = '';
    await expect
      .poll(
        async () => {
          const res = await request.get('/api/notifications', {
            headers: { Authorization: `Bearer ${studentToken}` },
          });
          const page = (await res.json()) as NotificationPage;
          const unread = page.content.find((n) => !n.isRead);
          if (unread) notifId = unread.id;
          return page.content.filter((n) => !n.isRead).length;
        },
        { timeout: 10_000, intervals: [500, 1000, 2000, 3000] },
      )
      .toBeGreaterThan(0);

    const response = await request.put(`/api/notifications/${notifId}/read`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.isRead).toBe(true);
    expect(body.readAt).toBeTruthy();
  });

  test('mark-all-as-read clears the unread count', async ({
    api,
    request,
    world,
  }) => {
    const studentToken = await loginAsWorldStudent(request, world);

    await createAssignment(api, world.teacherToken, world.pscId, buildAssignment());

    // Wait for notification
    await expect
      .poll(
        async () => {
          const res = await request.get('/api/notifications/unread-count', {
            headers: { Authorization: `Bearer ${studentToken}` },
          });
          return (await res.json()).unreadCount as number;
        },
        { timeout: 10_000, intervals: [500, 1000, 2000, 3000] },
      )
      .toBeGreaterThan(0);

    // Mark all as read
    await request.put('/api/notifications/read-all', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });

    const afterRes = await request.get('/api/notifications/unread-count', {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect((await afterRes.json()).unreadCount).toBe(0);
  });

  test('one user cannot mark another user\'s notification as read (403)', async ({
    api,
    request,
    world,
    adminToken,
  }) => {
    const studentToken = await loginAsWorldStudent(request, world);

    await createAssignment(api, world.teacherToken, world.pscId, buildAssignment());

    let notifId = '';
    await expect
      .poll(
        async () => {
          const res = await request.get('/api/notifications', {
            headers: { Authorization: `Bearer ${studentToken}` },
          });
          const page = (await res.json()) as NotificationPage;
          if (page.content.length > 0) notifId = page.content[0].id;
          return page.totalElements;
        },
        { timeout: 10_000, intervals: [500, 1000, 2000, 3000] },
      )
      .toBeGreaterThan(0);

    // Admin tries to mark the student's notification — should be forbidden
    const response = await request.put(`/api/notifications/${notifId}/read`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(response.status()).toBe(403);
  });
});

