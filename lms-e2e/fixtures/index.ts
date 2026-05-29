import { test as base } from '@playwright/test';
import { ApiHelper } from '../helpers/api.helper';
import { loginViaApi } from '../helpers/auth.helper';
import { seedWorld, WorldContext } from './factories/world.factory';

// Re-export individual factories for ad-hoc use in tests.
export * from './factories/user.factory';
export * from './factories/course.factory';
export * from './factories/program.factory';
export * from './factories/semester.factory';
export * from './factories/offering.factory';
export * from './factories/enrollment.factory';
export * from './factories/session.factory';
export * from './factories/assignment.factory';
export * from './factories/quiz.factory';
export { seedWorld, WorldContext };

export interface LmsFixtures {
  /** Typed API helper scoped to the current test's APIRequestContext. */
  api: ApiHelper;
  /** Access token for the configured ADMIN account. */
  adminToken: string;
  /** Access token for the configured TEACHER account. */
  teacherToken: string;
  /** Access token for the configured STUDENT account. */
  studentToken: string;
  /**
   * A fully seeded world (program → semester → course → offering →
   * teacher → student → enrollment).  Created fresh per test.
   */
  world: WorldContext;
}

export const test = base.extend<LmsFixtures>({
  api: async ({ request }, use) => {
    await use(new ApiHelper(request));
  },

  adminToken: async ({ request }, use) => {
    const { accessToken } = await loginViaApi(request, {
      email: process.env.ADMIN_EMAIL ?? 'admin@lms.test',
      password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
    });
    await use(accessToken);
  },

  teacherToken: async ({ request }, use) => {
    const { accessToken } = await loginViaApi(request, {
      email: process.env.TEACHER_EMAIL ?? 'teacher@lms.test',
      password: process.env.TEACHER_PASSWORD ?? 'Teacher@12345',
    });
    await use(accessToken);
  },

  studentToken: async ({ request }, use) => {
    const { accessToken } = await loginViaApi(request, {
      email: process.env.STUDENT_EMAIL ?? 'student@lms.test',
      password: process.env.STUDENT_PASSWORD ?? 'Student@12345',
    });
    await use(accessToken);
  },

  world: async ({ request }, use) => {
    const api = new ApiHelper(request);
    const { accessToken: adminToken } = await loginViaApi(request, {
      email: process.env.ADMIN_EMAIL ?? 'admin@lms.test',
      password: process.env.ADMIN_PASSWORD ?? 'Admin@12345',
    });
    const ctx = await seedWorld(api, adminToken, async (email, password) => {
      const { accessToken } = await loginViaApi(request, { email, password });
      return accessToken;
    });
    await use(ctx);
    // No cleanup here — tests run against a shared test DB that is reset
    // between CI runs.  Add teardown logic here if needed.
  },
});

export { expect } from '@playwright/test';
