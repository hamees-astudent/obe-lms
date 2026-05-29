import { test, expect } from '../../fixtures';
import { loginFull } from '../../helpers/auth.helper';

const ADMIN_CREDS = {
  email:    process.env.ADMIN_EMAIL    ?? 'admin@lms.test',
  password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
};

test.describe('Authentication — Token Refresh', () => {
  test('valid refresh token returns a new complete token pair', async ({ request }) => {
    const { refreshToken } = await loginFull(request, ADMIN_CREDS);

    const response = await request.post('/api/auth/refresh', {
      data: { refreshToken },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.accessToken).toBeTruthy();
    expect(body.refreshToken).toBeTruthy();
    expect(body.expiresIn).toBeGreaterThan(0);
    expect(body.user).toHaveProperty('id');
    expect(body.user).toHaveProperty('role');
  });

  test('refresh token rotation: the original token cannot be reused', async ({ request }) => {
    const { refreshToken: original } = await loginFull(request, ADMIN_CREDS);

    // First rotation — consumes `original`, issues a new pair
    await request.post('/api/auth/refresh', { data: { refreshToken: original } });

    // Second attempt with the same token must be rejected
    const second = await request.post('/api/auth/refresh', {
      data: { refreshToken: original },
    });
    expect(second.status()).toBe(401);
  });

  test('new access token from refresh grants access to protected routes', async ({ request }) => {
    const { refreshToken } = await loginFull(request, ADMIN_CREDS);

    const refreshRes = await request.post('/api/auth/refresh', {
      data: { refreshToken },
    });
    const { accessToken: newToken } = await refreshRes.json();

    const meRes = await request.get('/api/users/me', {
      headers: { Authorization: `Bearer ${newToken}` },
    });
    expect(meRes.status()).toBe(200);
  });

  test('invalid refresh token returns 401', async ({ request }) => {
    const response = await request.post('/api/auth/refresh', {
      data: { refreshToken: 'not-a-real-token-xyz' },
    });
    expect(response.status()).toBe(401);
  });

  test('missing refreshToken field returns 400', async ({ request }) => {
    const response = await request.post('/api/auth/refresh', { data: {} });
    expect(response.status()).toBe(400);
  });
});
