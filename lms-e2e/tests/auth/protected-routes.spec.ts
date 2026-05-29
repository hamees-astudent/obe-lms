import { test, expect } from '../../fixtures';
import { loginFull } from '../../helpers/auth.helper';

test.describe('Authentication — Protected Routes', () => {
  test('valid access token grants access to GET /api/users/me', async ({ request }) => {
    const { accessToken } = await loginFull(request, {
      email: process.env.ADMIN_EMAIL ?? 'admin@lms.test',
      password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
    });

    const response = await request.get('/api/users/me', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body).toHaveProperty('email');
    expect(body).toHaveProperty('role');
    expect(body).toHaveProperty('id');
  });

  test('/api/users/me response matches the logged-in user', async ({ request }) => {
    const email = process.env.ADMIN_EMAIL ?? 'admin@lms.test';
    const { accessToken, user } = await loginFull(request, {
      email,
      password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
    });

    const response = await request.get('/api/users/me', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    const body = await response.json();
    expect(body.id).toBe(user.id);
    expect(body.email).toBe(user.email);
    expect(body.role).toBe(user.role);
  });

  test('unauthenticated request to protected route returns 401', async ({ request }) => {
    const response = await request.get('/api/users/me');

    expect(response.status()).toBe(401);
  });

  test('malformed Bearer token returns 401', async ({ request }) => {
    const response = await request.get('/api/users/me', {
      headers: { Authorization: 'Bearer this-is-not-a-valid-jwt' },
    });

    expect(response.status()).toBe(401);
  });

  test('Authorization header with wrong scheme returns 401', async ({ request }) => {
    const { accessToken } = await loginFull(request, {
      email: process.env.ADMIN_EMAIL ?? 'admin@lms.test',
      password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
    });

    // "Basic" instead of "Bearer"
    const response = await request.get('/api/users/me', {
      headers: { Authorization: `Basic ${accessToken}` },
    });

    expect(response.status()).toBe(401);
  });

  test('admin-only endpoint rejects student token with 403', async ({ request }) => {
    const { accessToken } = await loginFull(request, {
      email: process.env.STUDENT_EMAIL ?? 'student@lms.test',
      password: process.env.STUDENT_PASSWORD ?? 'Student@12345',
    });

    // GET /api/admin/users is restricted to ADMIN role
    const response = await request.get('/api/admin/users', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    expect(response.status()).toBe(403);
  });
});
