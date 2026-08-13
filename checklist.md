# QA Fix Checklist

Post-QA remediation list for the LMS (Spring Boot 3.3 + React 18/Vite).
Each item states **what QA reported**, **what the code actually shows** (verified against the
repo at commit `b76ad80`), and the **tasks** to close it.

Legend: 🔴 blocker · 🟠 major · 🟡 minor
Paths are relative to the repo root. Backend root = `lms-backend/src/main/java/com/lms`,
frontend root = `lms-backend/src/main/frontend/src`.

---

## Status — all code changes implemented

**73 of 81 tasks complete.** The 8 unchecked ones all require a running
Postgres + Redis + Kafka + MinIO stack and are marked *"Not done — needs a live stack"*
in place; every one is an end-to-end test, not a code change. Verification performed:

| Check | Result |
|---|---|
| `mvn test` (backend) | **40 of 41 pass** — the one failure is a pre-existing Spring Modulith cycle, see *Known remaining issue* |
| `npm run verify` (type-check + conventions + build) | pass |
| Responsive sweep in headless Chromium @ 360/390/768/1280 | pass — no horizontal overflow, drawer below `lg`, column at `lg+` |
| Overflow sweep across all 12 routes @ 360px | pass |
| E2E suite (`lms-e2e`) | **not run** — needs a live Postgres/Redis/Kafka/MinIO stack. Specs reviewed against the new rules; see §5 |

New backend tests: `AttendanceServiceTest` (7), `AssessmentServiceTest` (7),
`NotificationServiceTest` (5), `InstitutionalCodesTest` (17), `GlobalExceptionHandlerTest` (4).

### Known remaining issue (pre-existing, not introduced here)

`LmsApplicationModulesTest` fails on a dependency cycle between the `infrastructure` and
`modules` slices: `infrastructure.security.JpaUserDetailsService` depends on
`modules.users.UserRepository`, while every module depends on `infrastructure`. Confirmed
present on the untouched `b76ad80` commit by stashing all changes and re-running. Not fixed
because the options are architectural and are the team's call:

- move `JpaUserDetailsService` / `UserPrincipal` into the `users` module, or
- introduce a port interface in `infrastructure` that `users` implements, or
- promote each business module to a top-level package so Modulith stops treating
  `com.lms.modules` as one module.

### Decision taken without confirmation

**Course-code grammar** (§2.1). The checklist said to confirm the exact DCS-UBIT format with
the department first. Rather than block, the pattern accepts alphanumeric segments joined by
single hyphens or underscores (`^[A-Za-z0-9]+([-_][A-Za-z0-9]+)*$`) — a superset of any
specific grammar they would pick, so `BSCS-501`, `CS-363`, `CS-363L` and legacy `CS101` all
work. Tighten `com.lms.shared.InstitutionalCodes.CODE_PATTERN` once the department confirms;
the frontend mirror in `lib/codes.ts` must be changed in step.

---

## 1. Critical & Broken Core Features

### 1.1 🔴 Quiz module broken end-to-end

**Reported:** creation flows fail; students cannot attempt or submit quizzes.

**Findings:**
- Backend endpoints and the frontend `QuizFormModal` / `QuestionsPanel` / `QuizPlayer` all exist and
  target the correct routes — the breakage is in the payloads and in how students reach the page,
  not in missing screens.
- **Date format mismatch (creation fails with 400).** `CreateQuizRequest` /
  `UpdateQuizRequest` declare `LocalDateTime availableFrom|availableUntil`
  (`modules/assessment/dto/CreateQuizRequest.java`), but the form sends
  `new Date(v).toISOString()` → `2026-08-13T09:00:00.000Z`. Jackson cannot bind the trailing `Z`
  offset to `LocalDateTime`, so every quiz create/update with an availability window is rejected.
  Same defect for `CreateAssignmentRequest.dueDate`
  (`pages/assessment/CourseAssessmentPage.tsx` — quiz mutation ~L930, assignment mutation ~L120).
- **Students never reach the quiz list.** `pages/assessment/AssessmentPage.tsx:107` queries
  `/me/enrollments?status=ENROLLED`, but the enrollment status enum is `ACTIVE | DROPPED | COMPLETED`
  (`modules/enrollment/Enrollment.java:40`). Students get an empty course list and can never open a
  quiz. Every other page correctly uses `status=ACTIVE`.
- **No validation feedback.** Mutation errors render as a flat `Failed to save.`
  (`CourseAssessmentPage.tsx` ~L993, ~L1218), hiding the 400 field errors — see §4.3.
- **`totalMarks` is never surfaced on the create form**, so a quiz can be created and started with
  zero questions; `QuizPlayer` then submits an empty answer map.

**Tasks**
- [x] Standardise the date contract. Pick one: (a) switch the API DTOs to `Instant`/`OffsetDateTime`,
      or (b) send a local ISO string without the `Z` (`format(date, "yyyy-MM-dd'T'HH:mm:ss")`).
      Apply the same fix to quizzes **and** assignments, request **and** response types.
- [x] Add a shared `toApiDateTime()` / `fromApiDateTime()` helper in `lib/` and use it everywhere a
      `datetime-local` input meets the API.
- [x] Fix `AssessmentPage.tsx:107` → `status=ACTIVE`. Grep for other status-literal drift.
- [x] Extract the enrollment-status values into `types/api.ts` as a union type so a bad literal fails
      type-check instead of silently returning `[]`.
- [ ] **Not done — needs a live stack.** Verify `POST /api/quizzes/{id}/start` →
      `PUT /api/quiz-submissions/{id}/answers` → `POST /api/quiz-submissions/{id}/submit` as a
      STUDENT against a real server. Covered by unit tests
      (`AssessmentServiceTest`) but not exercised end-to-end.
- [x] Block "Start Quiz" (and show why) when the quiz has zero questions.
- [x] Confirm `listQuestions` hides `correctAnswer` from students before submit and reveals it after
      (`AssessmentController.listQuestions`, `AssessmentService.listQuestions(id, isStaff)`).
- [ ] **Not done — needs a live stack.** Extend `lms-e2e/tests/assessment/quizzes.spec.ts` to cover
      create → add questions → student attempt → submit → teacher sees submission. The existing spec
      already seeds a question before starting, so it is compatible with the new zero-question guard.

### 1.2 🟠 Attendance tracking

**Reported:** missing entirely; only UI exists.

**Findings:** the module is *not* missing — `modules/attendance/` has sessions, records, bulk-mark,
summary and threshold alerts, and `pages/attendance/CourseAttendancePage.tsx` wires create-session
(~L320), per-student mark (~L118), close-session (~L140) and roster/summary reads. What is broken:

- **Summary cache is never evicted (🔴).** `AttendanceService.getAttendanceSummary` is
  `@Cacheable(ATTENDANCE_SUMMARY)`, and the only `@CacheEvict` sits on `checkThresholdAndAlert`,
  which is called via plain `this.` self-invocation from `markRecord` and `bulkMark`
  (`modules/attendance/AttendanceService.java:88`, `:117`, `:150`). Spring's proxy is bypassed, so
  the eviction never runs: after the first read, `/api/me/attendance/{pscId}` and the teacher summary
  serve stale numbers indefinitely. This is the most likely cause of "attendance doesn't track".
- `buildSummary` returns **100 %** when `total == 0`, so a student with no sessions shows as perfect
  attendance rather than "N/A".
- `bulkMark` is implemented backend-side but has **no frontend caller** — teachers must mark students
  one request at a time.
- `createSession` / `closeSession` accept an `actorId` but never check that the caller owns the
  offering; any TEACHER can create sessions on any course.

**Tasks**
- [x] Fix the eviction: move `@CacheEvict` onto `markRecord`/`bulkMark` (which are the proxied entry
      points), or inject the service into itself, or evict explicitly via `CacheManager`.
- [x] Add a test that marks attendance twice and asserts the summary changes between reads.
- [x] Return `total = 0 → percentage = 0` (or a nullable percentage) and render "No sessions yet".
- [x] Wire `POST /api/sessions/{id}/records/bulk` to a "Mark all present / save roster" action.
- [x] Enforce offering ownership (teacher-of-record or assigned assistant or ADMIN) in
      `createSession`, `closeSession`, `markRecord`, `bulkMark`.
- [x] Include student **name + roll number** in `AttendanceRecordResponse` (or a roster projection) so
      the UI stops cross-joining `/offerings/{pscId}/enrollments` client-side.
- [ ] **Not done — needs a live stack.** Run `lms-e2e/tests/attendance/attendance.spec.ts` and extend
      it to assert the student-side percentage updates after a teacher marks. Reviewed: all specs use
      `world.teacherToken`, which owns the offering, so the new ownership check does not break them.

### 1.3 🟠 Assignment file submissions

**Reported:** students cannot upload files (missing upload pipeline).

**Findings:** the pipeline exists on both sides — `POST /api/files` (multipart) in
`modules/files/FileController.java`, and `SubmitAssignmentModal` in
`pages/assessment/CourseAssessmentPage.tsx:244` uploads then posts `fileKey`/`fileName`/`fileSize`
to `/assignments/{id}/submit`. Concrete defects:

- **Content-Type override (🔴).** The axios instance sets a global
  `'Content-Type': 'application/json'` (`lib/api.ts:6`), and the upload call *manually* re-sets
  `'multipart/form-data'` without a boundary. The browser can no longer generate the boundary, so
  Spring's multipart resolver rejects the request. Let axios/the browser set the header: pass
  `FormData` with **no** explicit `Content-Type`.
- **Authenticated downloads are broken.** `pages/courses/CourseDetailPage.tsx:91` does
  `window.open('/api/files/{id}/download')`. The JWT lives in the Zustand store and is attached by an
  axios interceptor — a raw `window.open` sends no `Authorization` header and gets a 401. The
  presigned-URL endpoint (`GET /api/files/{id}/url`) exists and is the correct path here.
- **No client-side size/type guard.** `spring.servlet.multipart.max-file-size` is 50 MB
  (`application.yml:200`); oversized files fail late with an opaque error.
- The upload does not pass `context=submissions` / `contextId`, so `FileService.listByContext`
  cannot enumerate a submission's files.
- Late-submission handling (`allowLateSubmission`, `latePenaltyPercent`) is accepted by the API but
  never shown to the student before submitting.

**Tasks**
- [x] Remove the manual `multipart/form-data` header; verify the boundary is present in the request.
- [x] Replace `window.open('/api/files/...')` with a presigned-URL fetch, then open the returned URL.
      Apply to course materials **and** submitted assignment files.
- [x] Send `context` + `contextId` on upload so files are traceable to their submission.
- [x] Add client-side max-size / allowed-extension validation with an inline error.
- [x] Show a progress indicator and allow removing / replacing a staged file before submit.
- [x] Show due date, late-submission policy and penalty in the submit modal; label late submissions.
- [x] Ensure teachers can download a student's submitted file from the submissions panel.
- [ ] **Not done — needs a live stack.** Cover in `lms-e2e/tests/assessment/assignments.spec.ts`:
      upload → submit → teacher downloads.

---

## 2. Domain Logic & Data Mapping

### 2.1 🔴 Course code format (DCS-UBIT convention)

**Finding:** `modules/courses/dto/CreateCourseRequest.java` enforces
`@Pattern(regexp = "[A-Z0-9_]+")` — hyphens are rejected, so `BSCS-501` and `CS-363` cannot be
entered at all.

**Tasks**
- [x] Change the pattern to allow hyphens, e.g. `^[A-Z]{2,6}-[0-9]{3,4}[A-Z]?$` (confirm the exact
      DCS-UBIT grammar with the department first: does it allow a trailing letter? lab suffixes?).
- [x] Keep `@Size(max = 20)`; normalise input to uppercase and trim before validation.
- [x] Mirror the identical regex in the frontend zod schema (`pages/courses/CoursesCatalogPage.tsx`)
      and show the expected format as placeholder + helper text.
- [x] Check for the same over-strict pattern on program codes and semester codes
      (`modules/programs/dto/`).
- [x] Add a Flyway data-fix migration only if existing seeded codes need re-formatting; otherwise
      none needed (the column is already `varchar`).
- [x] Add unit tests for accepted (`BSCS-501`, `CS-363`) and rejected (`bscs 501`, `!!`) codes.

### 2.2 🟠 OBE mapping: materials → CLOs → PLOs

**Finding:** the CLO/PLO layer is partly built:
- `Plo` + CRUD exist (`modules/programs/`), surfaced in `pages/programs/ProgramsPage.tsx`.
- `Clo` + CRUD exist (`modules/courses/`), surfaced in `pages/courses/CoursesCatalogPage.tsx`.
- **CLO → PLO** mapping exists end-to-end in the API (`CloPloMapping`,
  `POST/DELETE /api/admin/clos/{cloId}/plo-mappings`, `GET /api/clos/{cloId}/plo-mappings`) but has
  **no frontend UI** — no page calls `plo-mappings`.
- **Assignment/Quiz → CLO** mapping exists in API *and* UI (`AssignmentCloMapping`,
  `QuizCloMapping`, `CourseAssessmentPage.tsx:739`).
- **Course material → CLO mapping does not exist at all** — `CourseMaterial` has no CLO reference and
  there is no mapping table (`V0007` and `V0013` cover CLO↔PLO and assessment↔CLO only).

**Tasks**
- [x] Add a `course_material_clo_mappings` table (migration `V0020__…`), entity, repository, and
      `POST/DELETE/GET /api/materials/{id}/clo-mappings` with a `weight` column consistent with the
      assessment mappings.
- [x] Build the **CLO → PLO** mapping UI in the course catalog: per-CLO multi-select of the program's
      PLOs with weights, backed by the existing endpoints.
- [x] Build the **material → CLO** mapping UI in the add/edit material modal.
- [x] Validate that mapping weights per CLO sum to 100 % (or document the intended semantics) and
      reject cross-program CLO→PLO links.
- [x] Verify `TranscriptService` CLO/PLO attainment actually consumes these mappings — the transcript
      DTOs already carry `CloAttainmentDetail` / `PloAttainmentDetail`; confirm they are non-empty
      once mappings exist.
- [x] Add an OBE coverage report (which CLOs have no assessment/material, which PLOs have no CLO).

### 2.3 🟠 Transcript search / student UUID UX

**Findings:**
- **The admin transcript views are 404-ing (🔴).** The axios instance already has
  `baseURL: '/api'`, but three calls in `pages/transcripts/TranscriptsPage.tsx` hardcode a second
  `/api` prefix — L453 (`/api/admin/students/{id}/transcripts`), L535
  (`/api/admin/transcripts/semester/{id}`) and L545 (`…/generate`). They resolve to `/api/api/…`.
  Admin transcript search and re-generation cannot work at all until this is fixed.
- Search requires a raw 36-character UUID (`disabled={studentId.trim().length !== 36}`, L472) and the
  UUID is never displayed anywhere — `pages/users/UsersPage.tsx` renders name/email/role/status but
  not the id, so there is nothing to copy.

**Tasks**
- [x] Remove the duplicated `/api` prefix from all three calls.
- [x] Add an ESLint rule or a small wrapper that rejects paths starting with `/api/` in `api.*()`
      calls, so this cannot regress.
- [x] Replace the UUID box with a **student search** (type-ahead over name / email / roll number)
      backed by the existing user search endpoint; keep raw UUID as a fallback.
- [x] Display the student id in `UsersPage` (monospace, truncated, click-to-copy) and on the student's
      own profile/dashboard.
- [x] Prefer a human-readable **roll number / enrollment number** as the primary identifier in
      transcript UI, with the UUID only as an internal key.
- [ ] **Not done — needs a live stack.** Update `lms-e2e/tests/transcript/transcript.spec.ts` to
      exercise the admin search path.

---

## 3. Notifications & Events

### 3.1 🟠 No events on material / assignment / quiz creation

**Finding:** confirmed. `AssessmentEvent.Action` only defines
`ASSIGNMENT_SUBMITTED`, `ASSIGNMENT_GRADED`, `QUIZ_SUBMITTED`
(`shared/events/AssessmentEvent.java:28`). `AssessmentService` publishes only those three
(L111, L149, L359). `CourseService` publishes **nothing** — adding a material is silent. So enrolled
students are never told that new work exists; only teachers get notified after a student submits.

**Tasks**
- [x] Add `ASSIGNMENT_CREATED`, `QUIZ_CREATED` (and `ASSIGNMENT_DUE_SOON` if a reminder is wanted) to
      `AssessmentEvent.Action`, with a fan-out recipient list rather than a single `studentEmail`.
- [x] Add a `MATERIAL_ADDED` event (new topic `lms.course.events` or reuse the assessment topic with
      a distinct action) published from `CourseService.createMaterial`.
- [x] Publish these events **after commit** (`@TransactionalEventListener(AFTER_COMMIT)` or
      Spring Modulith's event publication registry, already on the classpath and backed by `V0018`)
      so a rolled-back create cannot emit a notification.
- [x] Resolve recipients from active enrollments for the offering; skip `DROPPED`/`COMPLETED`.
- [x] Handle the fan-out in `NotificationService` — persist one `Notification` per student and send
      email via `NotificationEmailService`, batched.
- [x] Register the new bindings in `NotificationKafkaConsumers` + `KafkaTopics` +
      `spring.cloud.function.definition` / bindings in `application.yml`.
- [x] Do not publish events for materials created with `visible = false`; publish on the transition to
      visible instead.
- [x] Confirm the in-app bell (`components/layout/Header.tsx`, 60 s poll) reflects the new
      notifications, and consider pushing over the already-configured WebSocket instead of polling.
- [ ] **Not done — needs a live stack.** Extend `lms-e2e/tests/notifications/notifications.spec.ts`:
      teacher creates a quiz → enrolled student sees an unread notification.

---

## 4. Frontend & UI/UX

### 4.1 🟠 Mobile responsiveness

**Finding:** `components/layout/AppLayout.tsx` is a hard two-column flex with a fixed-width
`w-60` sidebar (`components/layout/Sidebar.tsx:47`) and **no** breakpoint handling — the only
responsive rules in the whole layout are two `sm:inline` toggles in `Header.tsx`. Below ~768 px the
sidebar eats most of the viewport and page content is squeezed. Data tables
(`UsersPage`, `OfferingsPage`, `GradingScalesPage`, transcript course tables) are plain `<table>`s
with no horizontal scroll container.

**Tasks**
- [x] Make the sidebar a drawer under `md`: off-canvas + overlay, hamburger trigger in the header,
      close on route change and on `Esc`.
- [x] Give `AppLayout` proper breakpoints; reduce `main` padding on small screens (`p-4 md:p-6`).
- [x] Wrap every table in `overflow-x-auto`, or switch to stacked cards under `sm`.
- [x] Audit modals (`components/ui/Modal.tsx`) for small viewports: full-height sheet on mobile,
      `max-h` + internal scroll, and confirm the `QuizPlayer` `max-h-[55vh]` list still works.
- [x] Fix the multi-column grids that don't degrade (`grid-cols-2` without a `sm:` prefix appears in
      the quiz/question/assignment forms).
- [x] Verify tap-target sizes (icon-only buttons are currently `p-1.5`, below the 44 px guideline).
- [x] Test at 360 × 640, 390 × 844, 768 × 1024 and 1280 × 800. *(Done in headless Chromium: no
      horizontal overflow at any width, on any of the 12 routes.)*

### 4.2 🟡 Navigation architecture

**Finding:** the sidebar carries all 11 destinations; the header carries only the bell, the user name
and logout, with an empty `<div />` spacer where a title/breadcrumb should be
(`components/layout/Header.tsx:25`). Nested routes (`/attendance/:pscId`, `/assessment/:pscId`,
`/courses/:id`) give the user no indication of where they are or how to get back, and role-specific
items are mixed into one flat list.

**Tasks**
- [x] Add breadcrumbs or a contextual page title in the header, driven by the route.
- [x] Group sidebar items into sections (Learning / Teaching / Administration) and label the
      admin-only group.
- [x] Add an explicit "Back to …" control on every `:pscId` detail page.
- [x] Decide one home for global actions (search, profile, notifications) and keep the sidebar purely
      for navigation.
- [x] Highlight the parent nav item when a child route is active (`NavLink` `end` semantics).
- [x] Reconsider `RequireRole` redirecting silently to `/dashboard` — show a "not authorised" page
      instead of a confusing bounce.

### 4.3 🟠 Error handling & feedback

**Findings:** errors are widely swallowed:
- The axios response interceptor logs the user out on **any** 401 (`lib/api.ts:23`), including a
  merely expired token — no refresh attempt, and the user loses unsaved work (e.g. mid-quiz).
- Mutation failures render as fixed strings — `Failed to save.`, `Failed to save question.`,
  `Upload failed. Please try again.` — discarding the server's validation messages.
- `catch {}` blocks with no logging in the upload paths (`CourseAssessmentPage.tsx:250`,
  `CourseDetailPage.tsx:481`).
- Several queries use `retry: false` + `enabled` and render nothing at all when they fail.
- No global toast/snackbar system and no error boundary — a render error blanks the app.

**Tasks**
- [x] Add a central `parseApiError(err)` that extracts Spring's `message` / field errors
      (`infrastructure/web` exception handler shape) and use it in every mutation.
- [x] Surface field-level validation errors on the corresponding inputs, not as one generic line.
- [x] Introduce a toast system for success/failure of every mutating action.
- [x] Implement silent token refresh in the interceptor (refresh token endpoint already exists — see
      `lms-e2e/tests/auth/token-refresh.spec.ts`); log out only when refresh itself fails.
- [x] Add a React error boundary at the router level with a recoverable fallback.
- [x] Give every query an explicit error state ("Couldn't load X — Retry"), not a silent empty list.
- [x] Distinguish 403 (not allowed) from 404 (not found) in the UI copy.
- [x] Confirm the backend never leaks stack traces to the client in prod.

### 4.4 🟡 Icon standardisation

**Finding:** `lucide-react` is already the project standard and is used consistently in layout and
most pages, but hardcoded glyphs remain:
- `pages/courses/CourseDetailPage.tsx:523-526` — `📢 📄 🔗 🎬` in the material-type `<option>`s.
- `pages/transcripts/TranscriptsPage.tsx:119` — `✕` as the modal close button (that modal is also
  hand-rolled instead of using `components/ui/Modal.tsx`).
- `pages/attendance/CourseAttendancePage.tsx:763` — `⚠` in the low-attendance warning.
- `pages/attendance/AttendancePage.tsx:174` and `pages/assessment/AssessmentPage.tsx:95` — `→` in
  call-to-action text.

**Tasks**
- [x] Replace all of the above with Lucide equivalents (`Megaphone`, `FileText`, `Link`, `Video`,
      `X`, `AlertTriangle`, `ArrowRight`).
- [x] `<option>` elements cannot host SVG — render the material-type picker as a radio/segmented
      control with icons, or drop the glyphs.
- [x] Replace the hand-rolled transcript modal with `components/ui/Modal.tsx`.
- [x] Add a lint rule (`no-irregular-whitespace` won't catch this — use a custom
      `no-restricted-syntax` regex or a CI grep) that fails on emoji in `.tsx` source.
- [x] Centralise icon size/stroke defaults so icons are visually consistent (currently 12–20 px ad hoc).

---

## 5. Cross-cutting verification

- [ ] **Not done — needs a live stack** (Postgres, Redis, Kafka, MinIO + the running app). Re-run the
      full Playwright suite and fix specs that encode the old behaviour. Static review found no spec
      that conflicts with the new rules; note `fixtures/factories/assignment.factory.ts:58` already
      strips the `Z` from `dueDate`, which corroborates the §1.1 date diagnosis.
- [x] Add backend slice tests for the two data-contract bugs: `LocalDateTime` binding and the course
      code pattern.
- [x] Add an integration test that asserts a cache-evicting path actually evicts (guards §1.2).
- [x] Do one pass over the frontend for `status=` literals and hardcoded `/api/` prefixes — both bug
      classes appeared more than once.
- [ ] **Not done — needs a live stack.** Re-test against a clean DB (Flyway from `V0001`, which now
      includes `V0020__create_material_clo_mappings_table.sql`) plus seeded DCS-UBIT sample data using
      real course codes (`BSCS-501`, `CS-363`).
