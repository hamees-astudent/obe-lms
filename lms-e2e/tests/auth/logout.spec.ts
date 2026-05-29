import { test, expect } from '../../fixtures';
import { loginFull } from '../../helpers/auth.helper';

const ADMIN_CREDS = {
  email:    process.env.ADMIN_EMAIL    ?? 'admin@lms.test',
  password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
};

test.describe('Authentication — Logout', () => {
  test('logout with a valid refresh token returns 204', async ({ request }) => {
    const { refreshToken } = await loginFull(request, ADMIN_CREDS);

    const response = await request.post('/api/auth/logout', {
      data: { refreshToken },
    });

    expect(response.status()).toBe(204);
  });

  test('after logout, the same refresh token is revoked', async ({ request }) => {
    const { refreshToken } = await loginFull(request, ADMIN_CREDS);

    // Revoke
    await request.post('/api/auth/logout', { data: { refreshToken } });

    // Attempt to rotate the now-revoked token
    const refreshRes = await request.post('/api/auth/refresh', {
      data: { refreshToken },
    });
    expect(refreshRes.status()).toBe(401);
  });

  test('logout with a bogus refresh token still returns 204 (silent no-op)', async ({ request }) => {
    // The backend deletes the Redis key; if it does not exist it is a no-op.
    const response = await request.post('/api/auth/logout', {
      data: { refreshToken: 'completely-bogus-token-xyz' },
    });
    expect(response.status()).toBe(204);
  });

  test('access token remains usable after logout (JWT is stateless)', async ({ request }) => {
    // The logout endpoint only revokes the refresh token stored in Redis.
    // The short-lived access token keeps working until it expires.
    const { accessToken, refreshToken } = await loginFull(request, ADMIN_CREDS);

    await request.post('/api/auth/logout', { data: { refreshToken } });

    const meRes = await request.get('/api/users/me', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(meRes.status()).toBe(200);
  });

  test('logout without a body returns 400', async ({ request }) => {
    const response = await request.post('/api/auth/logout', { data: {} });
    expect(response.status()).toBe(400);
  });
});
