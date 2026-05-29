import { test, expect } from '../../fixtures';

test.describe('Authentication — Password Reset', () => {
  test('requesting reset for a registered email returns 202', async ({ request }) => {
    const response = await request.post('/api/auth/password-reset/request', {
      data: { email: process.env.ADMIN_EMAIL ?? 'admin@lms.test' },
    });

    // Always 202 regardless — backend sends an email out-of-band
    expect(response.status()).toBe(202);
  });

  test('requesting reset for an unknown email still returns 202 (anti-enumeration)', async ({
    request,
  }) => {
    const response = await request.post('/api/auth/password-reset/request', {
      data: { email: 'nonexistent-user-xyz-99@lms.test' },
    });

    expect(response.status()).toBe(202);
  });

  test('password-reset request with invalid email format returns 400', async ({ request }) => {
    const response = await request.post('/api/auth/password-reset/request', {
      data: { email: 'not-an-email' },
    });

    expect(response.status()).toBe(400);
  });

  test('confirm with an invalid reset token returns 4xx', async ({ request }) => {
    const response = await request.post('/api/auth/password-reset/confirm', {
      data: { token: 'invalid-reset-token-xyz', newPassword: 'NewPass@12345' },
    });

    expect(response.status()).toBeGreaterThanOrEqual(400);
    expect(response.status()).toBeLessThan(500);
  });

  test('confirm with a short new password returns 400', async ({ request }) => {
    const response = await request.post('/api/auth/password-reset/confirm', {
      data: { token: 'any-token', newPassword: 'short' },
    });

    expect(response.status()).toBe(400);
  });

  test('confirm with missing token field returns 400', async ({ request }) => {
    const response = await request.post('/api/auth/password-reset/confirm', {
      data: { newPassword: 'NewPass@12345' },
    });

    expect(response.status()).toBe(400);
  });
});
