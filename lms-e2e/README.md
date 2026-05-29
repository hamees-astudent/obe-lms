# LMS E2E Tests

Playwright TypeScript end-to-end test suite for the LMS backend API. Tests run against a live Spring Boot instance and cover auth, courses, assessment, attendance, transcript, and notifications.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Installation](#installation)
3. [Configuration](#configuration)
4. [Test Structure](#test-structure)
5. [Running Tests](#running-tests)
6. [How Authentication Works](#how-authentication-works)
7. [Fixtures and Factories](#fixtures-and-factories)
8. [Writing New Tests](#writing-new-tests)
9. [Reports and Artifacts](#reports-and-artifacts)
10. [CI Notes](#ci-notes)

---

## Prerequisites

| Tool | Minimum version |
|---|---|
| Node.js | 20 LTS |
| npm | 10 |
| A running LMS backend | `http://localhost:8080` |

The LMS backend (including PostgreSQL, Redis, Kafka, and MinIO) must be fully started before running any tests. See the main [README](../README.md) for backend setup instructions.

---

## Installation

```bash
cd lms-e2e
npm install
npx playwright install chromium   # download the Chromium browser binary
```

---

## Configuration

Copy the example env file and fill in credentials that match your running backend:

```bash
cp .env.example .env
```

`.env` variables:

| Variable | Default | Description |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Root URL of the running LMS app |
| `API_BASE_URL` | `http://localhost:8080/api` | API base (used by `ApiHelper`) |
| `ADMIN_EMAIL` | `admin@lms.test` | Email of a seeded ADMIN user |
| `ADMIN_PASSWORD` | `Admin@12345` | Password for the ADMIN user |
| `TEACHER_EMAIL` | `teacher@lms.test` | Email of a seeded TEACHER user |
| `TEACHER_PASSWORD` | `Teacher@12345` | Password for the TEACHER user |
| `STUDENT_EMAIL` | `student@lms.test` | Email of a seeded STUDENT user |
| `STUDENT_PASSWORD` | `Student@12345` | Password for the STUDENT user |

> The three static users (admin / teacher / student) referenced by `ADMIN_EMAIL` etc. must exist in the database before running the auth-setup step. Create them once via `POST /api/admin/users` or seed them in a Flyway migration.

---

## Test Structure

```
lms-e2e/
├── .env.example
├── playwright.config.ts          # Project config, STORAGE_STATE paths
├── fixtures/
│   ├── index.ts                  # Custom test() with LmsFixtures (api, tokens, world)
│   └── factories/
│       ├── user.factory.ts       # createUser, buildStudent, buildTeacher …
│       ├── course.factory.ts     # createCourse, buildCourse
│       ├── program.factory.ts    # createProgram, createPlo
│       ├── semester.factory.ts   # createSemester, buildSemester
│       ├── offering.factory.ts   # createOffering, buildOffering
│       ├── enrollment.factory.ts # enrollStudent, dropEnrollment
│       ├── session.factory.ts    # createSession, buildSession
│       ├── assignment.factory.ts # createAssignment, buildAssignment
│       ├── quiz.factory.ts       # createQuiz, buildMcqQuestion …
│       └── world.factory.ts      # seedWorld → full WorldContext
├── helpers/
│   ├── api.helper.ts             # ApiHelper — typed get/post/put/delete/patch
│   └── auth.helper.ts            # loginFull, loginViaApi, refreshTokens,
│                                 #   logoutViaApi, injectAuthState, loginAsUser
└── tests/
    ├── auth.setup.ts             # Logs in as all three roles, saves .auth/*.json
    ├── auth/
    │   ├── login.spec.ts
    │   ├── logout.spec.ts
    │   ├── token-refresh.spec.ts
    │   ├── password-reset.spec.ts
    │   └── protected-routes.spec.ts
    ├── courses/
    │   ├── course-catalog.spec.ts
    │   └── course-materials.spec.ts
    ├── assessment/
    │   ├── assignments.spec.ts
    │   └── quizzes.spec.ts
    ├── attendance/
    │   └── attendance.spec.ts
    ├── transcript/
    │   └── transcript.spec.ts    # Grading scales CRUD + transcript endpoints
    └── notifications/
        └── notifications.spec.ts
```

---

## Running Tests

### Run the full suite

```bash
npm test
```

### Run a specific module

```bash
npm run test:auth
npm run test:courses
npm run test:assessment
npm run test:attendance
npm run test:transcript
npm run test:notifications
```

### Run a single file

```bash
npx playwright test tests/assessment/quizzes.spec.ts
```

### Run in headed mode (watch browser actions)

```bash
npm run test:headed
```

### Open the interactive Playwright UI

```bash
npm run test:ui
```

### View the last HTML report

```bash
npm run report
```

---

## How Authentication Works

Tests use a **three-project pipeline** defined in `playwright.config.ts`:

```
auth setup  →  chromium (UI)
            →  api (API-only, *.api.spec.ts)
```

### 1. Auth setup (`tests/auth.setup.ts`)

Runs **once before all other projects**. For each role (admin, teacher, student) it:

1. Calls `POST /api/auth/login` to obtain a JWT access token.
2. Seeds the Zustand `lms_auth` key in localStorage via `injectAuthState`.
3. Saves the full browser storage state to `.auth/{role}.json`.

### 2. Per-test token fixtures

The custom `test` exported from `fixtures/index.ts` provides pre-authenticated tokens as fixtures:

| Fixture | Type | Description |
|---|---|---|
| `api` | `ApiHelper` | Typed HTTP client pre-configured with `API_BASE_URL` |
| `adminToken` | `string` | JWT access token for the static admin user |
| `teacherToken` | `string` | JWT access token for the static teacher user |
| `studentToken` | `string` | JWT access token for the static student user |
| `world` | `WorldContext` | Fully seeded test world (program, semester, course, offering, enrolled student) — created fresh per test |

### 3. Using storage state in UI tests

For tests that need a pre-logged-in browser page, declare which role is needed:

```typescript
import { test } from '../../fixtures';
import { STORAGE_STATE } from '../../playwright.config';

test.use({ storageState: STORAGE_STATE.student });

test('student sees dashboard', async ({ page }) => {
  await page.goto('/dashboard');
  // ...
});
```

---

## Fixtures and Factories

### `ApiHelper` (`helpers/api.helper.ts`)

Thin typed wrapper around Playwright's `APIRequestContext`. All methods prefix the path with `API_BASE_URL` and attach a `Bearer` token header:

```typescript
const course = await api.get<CourseResponse>('/courses/123', adminToken);
const created = await api.post<CourseResponse>('/admin/courses', payload, adminToken);
await api.delete('/admin/courses/123', adminToken);
```

### `WorldContext` (`fixtures/factories/world.factory.ts`)

`seedWorld()` creates a minimal but complete academic world in the following order:

1. **Program** — a new academic program
2. **Semester** — with future start/end dates
3. **Course** — with a unique code (`TST` + 4 random digits)
4. **Teacher user** — `Teacher@12345`
5. **Offering** — links the course + semester + teacher
6. **Student user** — `Student@12345`
7. **Enrollment** — student enrolled in the offering

Returned `WorldContext`:

```typescript
{
  programId:      string;
  semesterId:     string;
  courseId:       string;
  pscId:          string;   // offering id — used on all offering-scoped endpoints
  teacherId:      string;
  teacherToken:   string;   // freshly-obtained JWT for the world teacher
  studentId:      string;
  studentEmail:   string;
  studentPassword:string;
  enrollmentId:   string;
}
```

Every test that uses the `world` fixture gets its **own isolated world** — parallel tests do not share state.

### Factories quick reference

| Factory function | Endpoint | Notes |
|---|---|---|
| `createUser(api, adminToken, payload)` | `POST /admin/users` | Use `buildStudent()` / `buildTeacher()` for payloads |
| `createCourse(api, adminToken, payload)` | `POST /admin/courses` | `buildCourse()` generates a unique code |
| `createProgram(api, adminToken, payload)` | `POST /admin/programs` | |
| `createSemester(api, adminToken, programId, payload)` | `POST /admin/programs/{id}/semesters` | |
| `createOffering(api, adminToken, payload)` | `POST /admin/offerings` | Returns `pscId` |
| `enrollStudent(api, adminToken, pscId, studentId)` | `POST /admin/enrollments` | |
| `createSession(api, token, payload)` | `POST /sessions` | Requires TEACHER/ADMIN token |
| `createAssignment(api, teacherToken, pscId, payload)` | `POST /offerings/{pscId}/assignments` | Body must include `pscId` |
| `createQuiz(api, teacherToken, pscId, payload)` | `POST /offerings/{pscId}/quizzes` | Body must include `pscId` |

---

## Writing New Tests

### Basic API test

```typescript
import { test, expect } from '../../fixtures';

test.describe('My feature', () => {
  test('admin can do X', async ({ api, adminToken, world }) => {
    const response = await api.post('/admin/x', { name: 'test' }, adminToken);
    expect(response.id).toBeTruthy();
  });

  test('student is blocked (403)', async ({ request, world }) => {
    const studentToken = await loginAsWorldStudent(request, world);
    const res = await request.post('/api/admin/x', {
      data: { name: 'test' },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(res.status()).toBe(403);
  });
});
```

### Rules

- Always import `test` and `expect` from `../../fixtures` (not from `@playwright/test`) to get the custom fixtures.
- Use `api.get/post/put/delete` for typed calls where you need the response body.
- Use the raw `request` fixture when you need the HTTP status code directly (e.g. for 4xx checks).
- The `world` fixture creates real database state — do **not** depend on state left by other tests.
- For Kafka-driven side effects (notifications), use `expect.poll()` with a `timeout` of `10_000` ms.

### Polling for async events

```typescript
await expect
  .poll(
    async () => {
      const res = await request.get('/api/notifications', {
        headers: { Authorization: `Bearer ${token}` },
      });
      return (await res.json()).totalElements as number;
    },
    { timeout: 10_000, intervals: [500, 1000, 2000] },
  )
  .toBeGreaterThan(0);
```

---

## Reports and Artifacts

After a test run, the following are written to disk:

| Path | Contents |
|---|---|
| `playwright-report/` | Interactive HTML report (`npm run report` to open) |
| `test-results/` | Traces, screenshots, and videos for failed tests |
| `.auth/` | Storage-state JSON files produced by `auth.setup.ts` (gitignored) |

Traces can be inspected with:

```bash
npx playwright show-trace test-results/<test-name>/trace.zip
```

---

## CI Notes

Set `CI=true` to apply stricter settings automatically (enforced by `playwright.config.ts`):

- `forbidOnly: true` — the build fails if `test.only` was accidentally committed.
- `retries: 2` — flaky tests are retried up to twice before being marked as failed.
- `workers: 1` — tests run serially to avoid database contention on shared CI infrastructure.

```bash
CI=true npm test
```
