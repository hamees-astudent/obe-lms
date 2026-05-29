import { test, expect } from '../../fixtures';
import { buildQuiz, createQuiz, buildMcqQuestion, addQuestion } from '../../fixtures/factories/quiz.factory';

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
// Quiz + Question CRUD
// ---------------------------------------------------------------------------

test.describe('Assessment — Quizzes (CRUD)', () => {
  test('teacher can create a quiz and response has required fields', async ({ api, world }) => {
    const payload = buildQuiz({ title: 'Midterm Quiz', durationMinutes: 45 });
    const quiz = await createQuiz(api, world.teacherToken, world.pscId, payload);

    expect(quiz.id).toBeTruthy();
    expect(quiz.pscId).toBe(world.pscId);
    expect(quiz.title).toBe('Midterm Quiz');
    expect(quiz.durationMinutes).toBe(45);
  });

  test('missing title returns 400', async ({ request, world }) => {
    const response = await request.post(`/api/offerings/${world.pscId}/quizzes`, {
      data: { pscId: world.pscId },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('student cannot create a quiz (403)', async ({ request, world }) => {
    const studentToken = await loginAsWorldStudent(request, world);
    const response = await request.post(`/api/offerings/${world.pscId}/quizzes`, {
      data: { pscId: world.pscId, title: 'Unauthorized quiz' },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(403);
  });

  test('teacher can add an MCQ question to a quiz', async ({ api, world }) => {
    const quiz = await createQuiz(api, world.teacherToken, world.pscId, buildQuiz());
    const question = await addQuestion(
      api, world.teacherToken, quiz.id, buildMcqQuestion(1),
    );

    expect(question.id).toBeTruthy();
    expect(question.quizId).toBe(quiz.id);
    expect(question.type).toBe('MCQ');
    expect(question.options).toHaveLength(4);
  });

  test('question with < 2 options returns 400', async ({ request, world, api }) => {
    const quiz = await createQuiz(api, world.teacherToken, world.pscId, buildQuiz());

    const response = await request.post(`/api/quizzes/${quiz.id}/questions`, {
      data: {
        questionText: 'Bad question?',
        type: 'MCQ',
        options: [{ id: 'a', text: 'Only one' }],
        correctAnswer: ['a'],
        marks: 5,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('invalid question type returns 400', async ({ request, world, api }) => {
    const quiz = await createQuiz(api, world.teacherToken, world.pscId, buildQuiz());

    const response = await request.post(`/api/quizzes/${quiz.id}/questions`, {
      data: {
        questionText: 'A question?',
        type: 'TRUE_FALSE',
        options: [{ id: 'a', text: 'A' }, { id: 'b', text: 'B' }],
        correctAnswer: ['a'],
        marks: 5,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(400);
  });

  test('teacher can list quizzes for an offering', async ({ api, world }) => {
    await createQuiz(api, world.teacherToken, world.pscId, buildQuiz());

    const list = await api.get<unknown[]>(
      `/offerings/${world.pscId}/quizzes`,
      world.teacherToken,
    );
    expect(Array.isArray(list)).toBe(true);
    expect(list.length).toBeGreaterThanOrEqual(1);
  });

  test('can fetch a quiz by id', async ({ api, world }) => {
    const created = await createQuiz(
      api, world.teacherToken, world.pscId, buildQuiz({ title: 'Fetch Quiz' }),
    );

    const fetched = await api.get<{ id: string; title: string }>(
      `/quizzes/${created.id}`, world.teacherToken,
    );
    expect(fetched.id).toBe(created.id);
    expect(fetched.title).toBe('Fetch Quiz');
  });

  test('staff can list questions (with correct answers)', async ({ api, world }) => {
    const quiz = await createQuiz(api, world.teacherToken, world.pscId, buildQuiz());
    await addQuestion(api, world.teacherToken, quiz.id, buildMcqQuestion(1));

    const questions = await api.get<unknown[]>(`/quizzes/${quiz.id}/questions`, world.teacherToken);
    expect(Array.isArray(questions)).toBe(true);
    expect(questions.length).toBeGreaterThanOrEqual(1);
  });

  test('teacher can update a quiz title', async ({ api, request, world }) => {
    const quiz = await createQuiz(api, world.teacherToken, world.pscId, buildQuiz());

    const response = await request.put(`/api/quizzes/${quiz.id}`, {
      data: { title: 'Updated Quiz Title', pscId: world.pscId },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.title).toBe('Updated Quiz Title');
  });

  test('teacher can update a question', async ({ api, request, world }) => {
    const quiz = await createQuiz(api, world.teacherToken, world.pscId, buildQuiz());
    const question = await addQuestion(api, world.teacherToken, quiz.id, buildMcqQuestion(1));

    const response = await request.put(`/api/quiz-questions/${question.id}`, {
      data: {
        questionText: 'Updated question text?',
        type: 'MCQ',
        options: [
          { id: 'a', text: 'Option A' },
          { id: 'b', text: 'Option B' },
        ],
        correctAnswer: ['b'],
        marks: 10,
      },
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.questionText).toBe('Updated question text?');
  });

  test('teacher can delete a question', async ({ api, request, world }) => {
    const quiz = await createQuiz(api, world.teacherToken, world.pscId, buildQuiz());
    const question = await addQuestion(api, world.teacherToken, quiz.id, buildMcqQuestion(1));

    const response = await request.delete(`/api/quiz-questions/${question.id}`, {
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(204);
  });

  test('teacher can delete a quiz', async ({ api, request, world }) => {
    const quiz = await createQuiz(api, world.teacherToken, world.pscId, buildQuiz());

    const response = await request.delete(`/api/quizzes/${quiz.id}`, {
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(204);
  });
});

// ---------------------------------------------------------------------------
// Quiz Submissions
// ---------------------------------------------------------------------------

test.describe('Assessment — Quiz Submissions', () => {
  /** Creates a quiz with one MCQ question, returns quiz + question ids. */
  async function seedQuizWithQuestion(
    world: import('../../fixtures').WorldContext,
    api: import('../../helpers/api.helper').ApiHelper,
  ) {
    const quiz = await createQuiz(api, world.teacherToken, world.pscId, buildQuiz());
    const question = await addQuestion(
      api, world.teacherToken, quiz.id, buildMcqQuestion(1),
    );
    return { quiz, question };
  }

  test('student can start a quiz and receives a submission id', async ({ api, request, world }) => {
    const { quiz } = await seedQuizWithQuestion(world, api);
    const studentToken = await loginAsWorldStudent(request, world);

    const response = await request.post(`/api/quizzes/${quiz.id}/start`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });

    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.id).toBeTruthy();
    expect(body.quizId).toBe(quiz.id);
    expect(body.startedAt).toBeTruthy();
  });

  test('student can save answers before submitting', async ({ api, request, world }) => {
    const { quiz, question } = await seedQuizWithQuestion(world, api);
    const studentToken = await loginAsWorldStudent(request, world);

    const startRes = await request.post(`/api/quizzes/${quiz.id}/start`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    const { id: submissionId } = await startRes.json();

    const saveRes = await request.put(`/api/quiz-submissions/${submissionId}/answers`, {
      data: { answers: { [question.id]: ['A'] } },
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(saveRes.status()).toBe(200);
  });

  test('student can submit a quiz and receives an auto-graded score', async ({
    api,
    request,
    world,
  }) => {
    const { quiz, question } = await seedQuizWithQuestion(world, api);
    const studentToken = await loginAsWorldStudent(request, world);

    // Start
    const startRes = await request.post(`/api/quizzes/${quiz.id}/start`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    const { id: submissionId } = await startRes.json();

    // Save answers with correct option
    await request.put(`/api/quiz-submissions/${submissionId}/answers`, {
      data: { answers: { [question.id]: ['A'] } },
      headers: { Authorization: `Bearer ${studentToken}` },
    });

    // Submit
    const submitRes = await request.post(`/api/quiz-submissions/${submissionId}/submit`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });

    expect(submitRes.status()).toBe(200);
    const body = await submitRes.json();
    expect(body.submittedAt).toBeTruthy();
    expect(body.autoGraded).toBe(true);
    // Correct answer → should score > 0
    expect(Number(body.score)).toBeGreaterThan(0);
  });

  test('student can retrieve their own quiz submission', async ({ api, request, world }) => {
    const { quiz } = await seedQuizWithQuestion(world, api);
    const studentToken = await loginAsWorldStudent(request, world);

    await request.post(`/api/quizzes/${quiz.id}/start`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });

    const response = await request.get(`/api/me/quizzes/${quiz.id}/submission`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.quizId).toBe(quiz.id);
  });

  test('teacher can view all submissions for a quiz', async ({ api, request, world }) => {
    const { quiz } = await seedQuizWithQuestion(world, api);
    const studentToken = await loginAsWorldStudent(request, world);
    await request.post(`/api/quizzes/${quiz.id}/start`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });

    const subs = await api.get<unknown[]>(`/quizzes/${quiz.id}/submissions`, world.teacherToken);
    expect(Array.isArray(subs)).toBe(true);
    expect(subs.length).toBeGreaterThanOrEqual(1);
  });

  test('teacher cannot start a quiz as student (403)', async ({ api, request, world }) => {
    const { quiz } = await seedQuizWithQuestion(world, api);

    const response = await request.post(`/api/quizzes/${quiz.id}/start`, {
      headers: { Authorization: `Bearer ${world.teacherToken}` },
    });
    expect(response.status()).toBe(403);
  });
});

