import { test, expect } from '../../fixtures';
import { loginFull } from '../../helpers/auth.helper';

test.describe('Authentication — Login', () => {
  test('admin can log in and receives a full token response', async ({ request }) => {
    const data = await loginFull(request, {
      email: process.env.ADMIN_EMAIL ?? 'admin@lms.test',
      password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
    });

    expect(data.accessToken).toBeTruthy();
    expect(data.refreshToken).toBeTruthy();
    expect(data.expiresIn).toBeGreaterThan(0);
    expect(data.user.role).toBe('ADMIN');
    expect(data.user.email).toBe(process.env.ADMIN_EMAIL ?? 'admin@lms.test');
  });

  test('teacher can log in', async ({ request }) => {
    const data = await loginFull(request, {
      email: process.env.TEACHER_EMAIL ?? 'teacher@lms.test',
      password: process.env.TEACHER_PASSWORD ?? 'Teacher@12345',
    });

    expect(data.accessToken).toBeTruthy();
    expect(data.user.role).toBe('TEACHER');
  });

  test('student can log in', async ({ request }) => {
    const data = await loginFull(request, {
      email: process.env.STUDENT_EMAIL ?? 'student@lms.test',
      password: process.env.STUDENT_PASSWORD ?? 'Student@12345',
    });

    expect(data.accessToken).toBeTruthy();
    expect(data.user.role).toBe('STUDENT');
  });

  test('wrong password returns 401', async ({ request }) => {
    const response = await request.post('/api/auth/login', {
      data: { email: 'admin@lms.test', password: 'wrongpassword' },
    });

    expect(response.status()).toBe(401);
  });

  test('unknown email returns 401', async ({ request }) => {
    const response = await request.post('/api/auth/login', {
      data: { email: 'nobody-xyz@lms.test', password: 'Any@12345' },
    });

    expect(response.status()).toBe(401);
  });

  test('missing email field returns 400', async ({ request }) => {
    const response = await request.post('/api/auth/login', {
      data: { password: 'Admin@12345' },
    });

    expect(response.status()).toBe(400);
  });

  test('missing password field returns 400', async ({ request }) => {
    const response = await request.post('/api/auth/login', {
      data: { email: 'admin@lms.test' },
    });

    expect(response.status()).toBe(400);
  });

  test('empty request body returns 400', async ({ request }) => {
    const response = await request.post('/api/auth/login', { data: {} });

    expect(response.status()).toBe(400);
  });
});
