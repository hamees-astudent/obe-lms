// ---------------------------------------------------------------------------
// Shared primitives
// ---------------------------------------------------------------------------
export type UUID = string;
export type Role = 'ADMIN' | 'TEACHER' | 'ASSISTANT' | 'STUDENT';

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------
export interface LoginRequest {
  email: string;
  password: string;
}

/** Matches com.lms.modules.auth.dto.LoginResponse */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  /** Access-token lifetime in milliseconds */
  expiresIn: number;
  user: UserInfo;
}

/** Matches com.lms.modules.auth.dto.LoginResponse.UserInfo */
export interface UserInfo {
  id: UUID;
  email: string;
  /** Full name (firstName + " " + lastName assembled on the server) */
  name: string;
  role: Role;
}

export interface PasswordResetRequestBody {
  email: string;
}

export interface PasswordResetConfirmBody {
  token: string;
  newPassword: string;
}

// ---------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------
export interface UserResponse {
  id: UUID;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  password: string;
}

// ---------------------------------------------------------------------------
// Programs
// ---------------------------------------------------------------------------
export interface ProgramSummaryResponse {
  id: UUID;
  code: string;
  name: string;
  durationYears: number;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
}

export interface ProgramResponse {
  id: UUID;
  code: string;
  name: string;
  description?: string;
  durationYears: number;
  active: boolean;
}

export interface SemesterResponse {
  id: UUID;
  programId: UUID;
  programName: string;
  name: string;
  startDate: string;
  endDate: string;
  status: 'UPCOMING' | 'ACTIVE' | 'COMPLETED';
  createdAt: string;
}

export interface OfferingSummaryResponse {
  id: UUID;
  semesterId: UUID;
  courseId: UUID;
  courseCode: string;
  courseName: string;
  creditHours: number;
  teacherId: UUID;
  maxCapacity: number;
  createdAt: string;
}

export interface UserSummaryResponse {
  id: UUID;
  name: string;
  email: string;
  role: Role;
  status: string;
  createdAt: string;
}

export interface UserDetailResponse {
  id: UUID;
  name: string;
  email: string;
  role: Role;
  status: string;
  createdAt: string;
}

export interface EnrollmentResponse {
  id: UUID;
  pscId: UUID;
  studentId: UUID;
  status: 'ENROLLED' | 'DROPPED' | 'COMPLETED';
  enrolledAt: string;
  droppedAt?: string;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// Courses, CLOs, Materials
// ---------------------------------------------------------------------------
export interface CourseSummaryResponse {
  id: UUID;
  code: string;
  name: string;
  creditHours: number;
  status: string;
  programId: UUID;
  programCode: string;
}

export interface CourseMaterialResponse {
  id: UUID;
  pscId: UUID;
  uploadedBy: UUID;
  type: string;
  title: string;
  description?: string;
  content: Record<string, unknown>;
  visible: boolean;
  orderIndex: number;
  createdAt: string;
}

export interface CourseResponse {
  id: UUID;
  code: string;
  name: string;
  description?: string;
  creditHours: number;
  theoryHours: number;
  labHours: number;
  programId: UUID;
  programName: string;
}

export interface CloResponse {
  id: UUID;
  code: string;
  description: string;
  bloomLevel: number;
  ploMappings: PloMappingResponse[];
}

export interface PloMappingResponse {
  ploId: UUID;
  ploCode: string;
  correlationWeight: number;
}

// ---------------------------------------------------------------------------
// Semesters (deprecated aliases kept for compatibility)
// ---------------------------------------------------------------------------
// SemesterResponse is now defined above with full programId/programName fields.

export interface SectionResponse {
  id: UUID;
  name: string;
  courseId: UUID;
  courseName: string;
  courseCode: string;
  semesterId: UUID;
  semesterName: string;
  teacherId: UUID;
  teacherName: string;
  enrollmentCount: number;
}

// ---------------------------------------------------------------------------
// Attendance
// ---------------------------------------------------------------------------
export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED';

export interface SessionResponse {
  id: UUID;
  pscId: UUID;
  createdBy: UUID;
  sessionDate: string; // ISO date "YYYY-MM-DD" from LocalDate
  topic?: string;
  openedAt: string;
  closedAt?: string;
  open: boolean;
  createdAt: string;
}

export interface AttendanceRecordResponse {
  id: UUID;
  sessionId: UUID;
  studentId: UUID;
  status: AttendanceStatus;
  markedBy: UUID;
  remarks?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface AttendanceSummaryResponse {
  pscId: UUID;
  studentId: UUID;
  attended: number;
  total: number;
  percentage: number;
}

export interface CreateSessionBody {
  pscId: UUID;
  sessionDate: string; // "YYYY-MM-DD"
  topic?: string;
}

export interface MarkAttendanceBody {
  status: string;
  remarks?: string;
}

export interface BulkMarkEntry {
  studentId: UUID;
  status: string;
  remarks?: string;
}

export interface BulkMarkBody {
  records: BulkMarkEntry[];
}

// ---------------------------------------------------------------------------
// Assessment — Assignments
// ---------------------------------------------------------------------------
export type SubmissionType = 'FILE' | 'TEXT' | 'BOTH';

export interface AssignmentResponse {
  id: UUID;
  pscId: UUID;
  createdBy: UUID;
  title: string;
  description?: string;
  submissionType: SubmissionType;
  totalMarks: number;
  dueDate: string; // ISO datetime string
  allowLateSubmission: boolean;
  latePenaltyPercent?: number;
  createdAt: string;
  updatedAt: string;
}

export interface AssignmentSubmissionResponse {
  id: UUID;
  assignmentId: UUID;
  studentId: UUID;
  status: string; // 'SUBMITTED' | 'GRADED'
  textContent?: string;
  fileKey?: string;
  fileName?: string;
  fileSize?: number;
  submittedAt?: string;
  marksObtained?: number;
  feedback?: string;
  gradedBy?: UUID;
  gradedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAssignmentBody {
  title: string;
  description?: string;
  submissionType: SubmissionType;
  totalMarks: number;
  dueDate: string; // ISO datetime
  allowLateSubmission?: boolean;
  latePenaltyPercent?: number;
}

export interface UpdateAssignmentBody {
  title: string;
  description?: string;
  totalMarks: number;
  dueDate: string;
  allowLateSubmission?: boolean;
  latePenaltyPercent?: number;
}

export interface SubmitAssignmentBody {
  textContent?: string;
  fileKey?: string;
  fileName?: string;
  fileSize?: number;
}

export interface GradeSubmissionBody {
  marksObtained: number;
  feedback?: string;
}

// ---------------------------------------------------------------------------
// Assessment — Quizzes
// ---------------------------------------------------------------------------
export interface QuizResponse {
  id: UUID;
  pscId: UUID;
  createdBy: UUID;
  title: string;
  description?: string;
  durationMinutes?: number;
  availableFrom?: string;
  availableUntil?: string;
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface QuizQuestionResponse {
  id: UUID;
  quizId: UUID;
  questionText: string;
  type: 'MCQ' | 'MSQ';
  options: Array<{ id: string; text: string }>;
  correctAnswer: string[] | null; // null for students before submit
  marks: number;
  orderIndex: number;
  explanation?: string;
  createdAt: string;
  updatedAt: string;
}

export interface QuizSubmissionResponse {
  id: UUID;
  quizId: UUID;
  studentId: UUID;
  answers: Record<string, string[]>; // questionId → selected option ids
  startedAt: string;
  submittedAt?: string;
  score?: number;
  autoGraded: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateQuizBody {
  title: string;
  description?: string;
  durationMinutes?: number;
  availableFrom?: string;
  availableUntil?: string;
  shuffleQuestions?: boolean;
  shuffleOptions?: boolean;
}

export interface UpdateQuizBody {
  title: string;
  description?: string;
  durationMinutes?: number;
  availableFrom?: string;
  availableUntil?: string;
  shuffleQuestions?: boolean;
  shuffleOptions?: boolean;
}

export interface CreateQuizQuestionBody {
  questionText: string;
  type: 'MCQ' | 'MSQ';
  options: Array<{ id: string; text: string }>;
  correctAnswer: string[];
  marks: number;
  orderIndex?: number;
  explanation?: string;
}

export interface UpdateQuizQuestionBody {
  questionText: string;
  type: 'MCQ' | 'MSQ';
  options: Array<{ id: string; text: string }>;
  correctAnswer: string[];
  marks: number;
  orderIndex: number;
  explanation?: string;
}

export interface SubmitQuizAnswersBody {
  answers: Record<string, string[]>;
}

export interface CloMappingResponse {
  assessmentId: UUID;
  cloId: UUID;
  weight: number;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// Grading Scale
// ---------------------------------------------------------------------------
export interface GradingScaleResponse {
  id: UUID;
  name: string;
  programId?: UUID;
  programName?: string;
  active: boolean;
  entries: GradingScaleEntryResponse[];
}

export interface GradingScaleEntryResponse {
  id: UUID;
  letterGrade: string;
  minPercentage: number;
  maxPercentage: number;
  gradePoints: number;
}

// ---------------------------------------------------------------------------
// Transcripts
// ---------------------------------------------------------------------------
export interface TranscriptSummaryResponse {
  id: UUID;
  studentId: UUID;
  studentName: string;
  studentEmail: string;
  semesterId: UUID;
  semesterName: string;
  programId: UUID;
  programName: string;
  cgpa: number;
  sgpa: number;
  totalCreditHours: number;
  generatedAt: string;
}

export interface CloAttainmentDetail {
  cloId: UUID;
  cloCode: string;
  description: string;
  attainmentPercentage: number;
}

export interface PloAttainmentDetail {
  ploId: UUID;
  ploCode: string;
  description: string;
  attainmentPercentage: number;
}

export interface CourseTranscriptDetail {
  courseId: UUID;
  courseCode: string;
  courseName: string;
  creditHours: number;
  obtainedMarks: number;
  totalMarks: number;
  percentage: number;
  letterGrade: string;
  gradePoints: number;
  cloAttainments: CloAttainmentDetail[];
}

export interface TranscriptSnapshotData {
  courses: CourseTranscriptDetail[];
  ploAttainments: PloAttainmentDetail[];
  sgpa: number;
  cgpa: number;
}

export interface TranscriptResponse {
  id: UUID;
  studentId: UUID;
  studentName: string;
  studentEmail: string;
  semesterId: UUID;
  semesterName: string;
  programId: UUID;
  programName: string;
  snapshotData: TranscriptSnapshotData;
  generatedAt: string;
}

// ---------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------
export type NotificationEventType =
  | 'ENROLLMENT_CONFIRMED'
  | 'ATTENDANCE_ALERT'
  | 'ASSIGNMENT_CREATED'
  | 'QUIZ_CREATED'
  | 'RESULTS_PUBLISHED'
  | 'SEMESTER_COMPLETED';

export interface NotificationResponse {
  id: UUID;
  title: string;
  body: string;
  eventType: NotificationEventType;
  referenceType?: string;
  referenceId?: UUID;
  isRead: boolean;
  readAt?: string;
  createdAt: string;
}

export interface NotificationPage {
  content: NotificationResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ---------------------------------------------------------------------------
// Files
// ---------------------------------------------------------------------------
export interface UploadedFileResponse {
  id: UUID;
  objectKey: string;
  originalName: string;
  contentType: string;
  fileSize: number;
  context?: string;
  contextId?: UUID;
  uploadedBy: UUID;
  createdAt: string;
}

export interface PresignedUrlResponse {
  url: string;
  expiresAt: string;
}

export interface CreateMaterialBody {
  type: string;
  title: string;
  description?: string;
  content: Record<string, unknown>;
  visible: boolean;
  orderIndex: number;
}

export interface UpdateMaterialBody {
  title: string;
  description?: string;
  content: Record<string, unknown>;
  visible: boolean;
  orderIndex: number;
}

// ---------------------------------------------------------------------------
// Pagination
// ---------------------------------------------------------------------------
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
