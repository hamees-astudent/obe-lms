import { ApiHelper } from '../../helpers/api.helper';

// ---------------------------------------------------------------------------
// Quiz
// ---------------------------------------------------------------------------

export interface CreateQuizPayload {
  title: string;
  description?: string;
  durationMinutes?: number;
  /** ISO-8601 datetime — "YYYY-MM-DDTHH:mm:ss" */
  availableFrom?: string;
  availableUntil?: string;
  shuffleQuestions?: boolean;
  shuffleOptions?: boolean;
}

export interface QuizResponse {
  id: string;
  pscId: string;
  title: string;
  description?: string;
  durationMinutes?: number;
  availableFrom?: string;
  availableUntil?: string;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  totalMarks: number;
  questionCount: number;
  createdAt: string;
}

/**
 * Creates a quiz for a given offering (pscId).
 * Requires a TEACHER token for that offering.
 */
export async function createQuiz(
  api: ApiHelper,
  teacherToken: string,
  pscId: string,
  payload: CreateQuizPayload,
): Promise<QuizResponse> {
  return api.post<QuizResponse>(
    `/offerings/${pscId}/quizzes`,
    { pscId, ...payload },   // pscId required by @NotNull validation; controller also sets it from path
    teacherToken,
  );
}

export function buildQuiz(
  overrides: Partial<CreateQuizPayload> = {},
): CreateQuizPayload {
  const uid = Math.floor(Math.random() * 9000) + 1000;
  return {
    title: `Test Quiz ${uid}`,
    durationMinutes: 30,
    shuffleQuestions: false,
    shuffleOptions: false,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Quiz questions
// ---------------------------------------------------------------------------

export type QuestionType = 'MCQ' | 'MSQ';

export interface QuizOptionPayload {
  id: string;
  text: string;
}

export interface CreateQuestionPayload {
  questionText: string;
  type: QuestionType;
  options: QuizOptionPayload[];
  /** Option id(s) that are correct */
  correctAnswer: string[];
  marks: number;
  orderIndex?: number;
  explanation?: string;
}

export interface QuizQuestionResponse {
  id: string;
  quizId: string;
  questionText: string;
  type: QuestionType;
  options: QuizOptionPayload[];
  marks: number;
  orderIndex: number;
  createdAt: string;
}

/**
 * Adds a question to an existing quiz.
 * Requires a TEACHER token.
 */
export async function addQuestion(
  api: ApiHelper,
  teacherToken: string,
  quizId: string,
  payload: CreateQuestionPayload,
): Promise<QuizQuestionResponse> {
  return api.post<QuizQuestionResponse>(`/quizzes/${quizId}/questions`, payload, teacherToken);
}

export function buildMcqQuestion(
  orderIndex = 1,
  overrides: Partial<CreateQuestionPayload> = {},
): CreateQuestionPayload {
  return {
    questionText: `Sample MCQ question ${orderIndex}?`,
    type: 'MCQ',
    options: [
      { id: 'A', text: 'Option A' },
      { id: 'B', text: 'Option B' },
      { id: 'C', text: 'Option C' },
      { id: 'D', text: 'Option D' },
    ],
    correctAnswer: ['A'],
    marks: 5,
    orderIndex,
    ...overrides,
  };
}
