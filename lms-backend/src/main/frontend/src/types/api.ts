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
  name: string;
  email: string;
  password: string;
  role: Role;
}

export interface UpdateUserRequest {
  name: string;
}

export interface ChangeRoleRequest {
  role: Role;
}

export interface ChangeStatusRequest {
  status: 'ACTIVE' | 'INACTIVE';
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

export interface ProgramDetailResponse {
  id: UUID;
  code: string;
  name: string;
  description?: string;
  durationYears: number;
  status: string;
  createdAt: string;
  plos: PloResponse[];
}

export interface CreateProgramRequest {
  name: string;
  code: string;
  description?: string;
  durationYears: number;
}

export interface PloResponse {
  id: UUID;
  programId: UUID;
  code: string;
  title: string;
  description?: string;
  orderIndex: number;
  createdAt: string;
}

export interface CreatePloRequest {
  code: string;
  title: string;
  description?: string;
  orderIndex: number;
}

export interface CreateSemesterRequest {
  name: string;
  startDate: string; // YYYY-MM-DD
  endDate: string;   // YYYY-MM-DD
}

export interface CreateOfferingBody {
  semesterId: UUID;
  courseId: UUID;
  teacherId: UUID;
  maxCapacity: number;
}

export interface CreateEnrollmentBody {
  pscId: UUID;
  studentId: UUID;
  courseRole: Role;
}

export interface SemesterResponse {
  id: UUID;
  programId: UUID;
  programName: string;
  name: string;
  startDate: string;
  endDate: string;
  status: 'OPEN' | 'CLOSED';
  closedAt?: string;
  closedById?: UUID;
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
  /** Institutional roll number; null for non-students and unprofiled students. */
  studentNumber?: string;
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

/**
 * Mirrors the backend `enrollments.status` values (see `Enrollment.java`).
 * Use {@link ENROLLMENT_STATUS} when building query strings so a typo is a
 * compile error rather than a silently empty result set.
 */
export type EnrollmentStatus = 'ACTIVE' | 'DROPPED' | 'COMPLETED';

export const ENROLLMENT_STATUS = {
  ACTIVE: 'ACTIVE',
  DROPPED: 'DROPPED',
  COMPLETED: 'COMPLETED',
} as const satisfies Record<Uppercase<string>, EnrollmentStatus>;

/** An offering the current user teaches or assists, from `/me/teaching-offerings`. */
export interface TeachingOfferingResponse {
  id: UUID;
  semesterId: UUID;
  semesterName: string;
  programName: string;
  courseId: UUID;
  courseCode: string;
  courseName: string;
  creditHours: number;
  teacherId: UUID;
}

export interface EnrollmentResponse {
  id: UUID;
  pscId: UUID;
  studentId: UUID;
  studentName?: string;
  /** Institutional roll number; only resolved on offering-roster endpoints. */
  studentNumber?: string;
  courseRole: Role;
  status: EnrollmentStatus;
  enrolledAt: string;
  droppedAt?: string;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// Cohorts — named groups of students enrolled in an offering in one step
// ---------------------------------------------------------------------------
export interface CohortSummaryResponse {
  id: UUID;
  name: string;
  description?: string;
  memberCount: number;
  createdAt: string;
  updatedAt?: string;
}

export interface CohortMemberResponse {
  studentId: UUID;
  name: string;
  email: string;
  studentNumber?: string;
  /** Account status; members who are not ACTIVE are skipped when the cohort is enrolled. */
  status: string;
}

export interface CohortDetailResponse {
  id: UUID;
  name: string;
  description?: string;
  members: CohortMemberResponse[];
  createdAt: string;
  updatedAt?: string;
}

export interface AddCohortMembersResponse {
  added: number;
  alreadyMembers: number;
  /** Ids or roll numbers that matched no user. */
  notFound: string[];
  /** Matched users who are not students, as "name (ROLE)". */
  notStudents: string[];
  cohort: CohortDetailResponse;
}

export interface CohortEnrollmentResult {
  studentId: UUID;
  studentName: string;
  studentNumber?: string;
  outcome: 'ENROLLED' | 'SKIPPED';
  /** Why the student was skipped; absent when enrolled. */
  reason?: string;
}

export interface CohortEnrollmentResponse {
  cohortId: UUID;
  pscId: UUID;
  enrolled: number;
  skipped: number;
  results: CohortEnrollmentResult[];
}

// ---------------------------------------------------------------------------
// Timetable — rooms and the current term's weekly classes
// ---------------------------------------------------------------------------
export type SessionKind = 'LECTURE' | 'LAB';

export interface RoomResponse {
  id: UUID;
  name: string;
  building?: string;
  capacity: number;
  kind: SessionKind;
  active: boolean;
  /** Weekly class meetings the timetable has put in this room. */
  weeklyClasses: number;
}

export interface RoomRequest {
  name: string;
  building?: string;
  capacity: number;
  kind: SessionKind;
  active: boolean;
}

export interface TimetableGridResponse {
  /** `dayOfWeek` is ISO: 1 = Monday. */
  days: { dayOfWeek: number; name: string }[];
  /** Times as "HH:mm:ss". */
  slots: { index: number; start: string; end: string }[];
  labSlots: number;
}

export interface TimetableEntryResponse {
  id: UUID;
  pscId: UUID;
  courseCode: string;
  courseName: string;
  programId: UUID;
  programName: string;
  semesterName: string;
  teacherId: UUID;
  teacherName: string;
  roomId: UUID;
  roomName: string;
  kind: SessionKind;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  students: number;
  /** Cohorts with members in this class; filled in the admin view only. */
  cohortIds: UUID[];
}

export interface UnscheduledOfferingResponse {
  pscId: UUID;
  courseCode: string;
  courseName: string;
  programName: string;
  teacherName: string;
  students: number;
  /** Weekly meetings not yet placed. */
  missing: SessionKind[];
  /** Why the generator could not place them; absent if it has not run since. */
  reason?: string;
}

export interface TimetableResponse {
  grid: TimetableGridResponse;
  entries: TimetableEntryResponse[];
  unscheduled: UnscheduledOfferingResponse[];
  offerings: number;
  sessionsRequired: number;
  sessionsPlaced: number;
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
  status: AttendanceStatus;
  remarks?: string;
}

export interface BulkMarkEntry {
  studentId: UUID;
  status: AttendanceStatus;
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
  /** Submitter identity; present on staff-facing responses only. */
  studentName?: string;
  studentEmail?: string;
  studentNumber?: string;
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
  /** Sum of the marks of every question; 0 until questions are added. */
  totalMarks: number;
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
  /** Submitter identity; present on staff-facing responses only. */
  studentName?: string;
  studentEmail?: string;
  studentNumber?: string;
  answers: Record<string, string[]>; // questionId → selected option ids
  startedAt: string;
  /** Seconds left on a timed attempt in progress, computed by the server. */
  remainingSeconds?: number;
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
  isDefault: boolean;
  entries: GradingScaleEntryResponse[];
  createdAt: string;
}

export interface GradingScaleEntryResponse {
  id: UUID;
  scaleId: UUID;
  gradeLetter: string;
  minPercentage: number;
  maxPercentage: number;
  gradePoints: number;
  orderIndex: number;
}

export interface CreateGradingScaleRequest {
  name: string;
  programId?: UUID;
  isDefault?: boolean;
}

export interface AddScaleEntryRequest {
  gradeLetter: string;
  minPercentage: number;
  maxPercentage: number;
  gradePoints: number;
  orderIndex: number;
}

// ---------------------------------------------------------------------------
// Transcripts
// ---------------------------------------------------------------------------
export interface TranscriptSummaryResponse {
  id: UUID;
  studentId: UUID;
  studentName: string;
  semesterId: UUID;
  semesterName: string;
  programId: UUID;
  programName: string;
  cgpa: number | null;
  sgpa: number | null;
  totalCreditHours: number;
  generatedAt: string;
}

// Transcript detail — mirrors the backend TranscriptResponse and the
// TranscriptSnapshotData stored with it. Numbers the backend holds as a
// nullable Double (marks before grading, attainment with no data) are
// `number | null` here and must not be formatted unguarded.
export interface CloAttainmentDetail {
  cloCode: string;
  cloTitle: string;
  attainmentPercentage: number | null;
}

export interface PloAttainmentDetail {
  ploCode: string;
  ploTitle: string;
  attainmentPercentage: number | null;
}

export interface CourseTranscriptDetail {
  pscId: UUID;
  courseCode: string;
  courseName: string;
  creditHours: number;
  attendancePercentage: number | null;
  totalMarks: number | null;
  marksObtained: number | null;
  percentage: number | null;
  gradeLetter: string | null;
  gradePoints: number | null;
  cloAttainment: CloAttainmentDetail[];
}

export interface TranscriptSnapshotData {
  studentName: string;
  studentNumber: string | null;
  programName: string;
  semesterName: string;
  gradingScaleName: string | null;
  semesterGpa: number | null;
  cumulativeGpa: number | null;
  totalCreditHours: number;
  earnedCreditHours: number;
  courses: CourseTranscriptDetail[];
  ploAttainment: PloAttainmentDetail[];
}

export interface TranscriptResponse {
  id: UUID;
  studentId: UUID;
  semesterId: UUID;
  programId: UUID;
  gradingScaleId: UUID;
  semesterGpa: number | null;
  cumulativeGpa: number | null;
  totalCreditHours: number;
  earnedCreditHours: number;
  generatedAt: string;
  generatedBy: UUID | null;
  createdAt: string;
  snapshot: TranscriptSnapshotData;
}

// ---------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------
/**
 * Every `eventType` the backend writes (see `NotificationService`). The union
 * previously listed only a subset, so real notifications fell through to the
 * generic bell icon with no label.
 */
export type NotificationEventType =
  | 'ENROLLMENT_CONFIRMED'
  | 'ENROLLMENT_DROPPED'
  | 'ATTENDANCE_ALERT'
  | 'ASSIGNMENT_CREATED'
  | 'QUIZ_CREATED'
  | 'MATERIAL_ADDED'
  | 'ASSIGNMENT_SUBMITTED'
  | 'ASSIGNMENT_GRADED'
  | 'QUIZ_SUBMITTED'
  | 'SEMESTER_CLOSED'
  | 'SEMESTER_REOPENED';

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
// Exams
// ---------------------------------------------------------------------------
export type ExamType = 'MIDTERM' | 'FINAL' | 'SESSIONAL' | 'MAKEUP';
export type ExamStatus = 'DRAFT' | 'OPEN' | 'LOCKED';
export type ExamScanStatus =
  | 'PENDING'
  | 'EXTRACTED'
  | 'CONFIRMED'
  | 'FAILED'
  | 'DISCARDED';
export type ExamResultSource = 'SCAN' | 'MANUAL';

export interface ExamCloMappingResponse {
  cloId: UUID;
  cloCode?: string;
  cloTitle?: string;
  weight?: number;
}

export interface ExamQuestionResponse {
  id: UUID;
  questionNo: string;
  maxMarks: number;
  orderIndex: number;
  cloMappings: ExamCloMappingResponse[];
}

export interface ExamResponse {
  id: UUID;
  pscId: UUID;
  courseCode?: string;
  courseName?: string;
  createdBy: UUID;
  title: string;
  examType: ExamType;
  examDate: string;
  totalMarks: number;
  status: ExamStatus;
  questions: ExamQuestionResponse[];
  /** Sum of the question maxima — compare against totalMarks. */
  questionMarksTotal: number;
  resultsRecorded: number;
  rosterSize: number;
  createdAt: string;
  updatedAt?: string;
}

/**
 * A scanned copy awaiting review.
 *
 * Nothing here is recorded yet: `proposedMarks` is what the reader saw, and it
 * becomes a result only when the teacher confirms it.
 */
export interface ScanReviewResponse {
  id: UUID;
  examId?: UUID;
  examTitle?: string;
  status: ExamScanStatus;
  imageKey: string;

  readRollNumber?: string;
  readStudentName?: string;
  readCourseCode?: string;
  readExamDate?: string;
  readWrittenTotal?: number;
  confidence?: 'HIGH' | 'MEDIUM' | 'LOW';
  extractorNotes: string[];

  matchedStudentId?: UUID;
  matchedStudentName?: string;
  matchedStudentNumber?: string;

  proposedMarks: ProposedMark[];
  proposedTotal: number;

  warnings: string[];
  errorMessage?: string;
  createdAt: string;
}

export interface ProposedMark {
  questionId: UUID;
  questionNo: string;
  maxMarks: number;
  /** Null when the reader could not read the cell — not a zero. */
  marksObtained: number | null;
  unread: boolean;
}

export interface ScanSummaryResponse {
  id: UUID;
  examId?: UUID;
  status: ExamScanStatus;
  imageKey: string;
  readRollNumber?: string;
  readStudentName?: string;
  matchedStudentId?: UUID;
  matchedStudentName?: string;
  warningCount: number;
  errorMessage?: string;
  createdAt: string;
  confirmedAt?: string;
}

export interface ExamQuestionMarkResponse {
  questionId: UUID;
  questionNo?: string;
  maxMarks?: number;
  marksObtained: number | null;
}

export interface ExamResultResponse {
  id: UUID;
  examId: UUID;
  studentId: UUID;
  studentName?: string;
  studentNumber?: string;
  totalObtained: number;
  totalMarks: number;
  percentage?: number;
  source: ExamResultSource;
  scanId?: UUID;
  recordedBy: UUID;
  recordedAt: string;
  remarks?: string;
  marks: ExamQuestionMarkResponse[];
}

export interface ExamRosterEntryResponse {
  studentId: UUID;
  name: string;
  studentNumber?: string;
  hasResult: boolean;
}

/** One question's mark, as sent back on confirm or manual entry. */
export interface QuestionMarkEntryBody {
  questionId: UUID;
  /** Null records the question as not attempted. */
  marksObtained: number | null;
}

export interface ExamQuestionBody {
  questionNo: string;
  maxMarks: number;
  cloMappings: { cloId: UUID; weight?: number }[];
}

export interface CreateExamBody {
  title: string;
  examType: ExamType;
  examDate: string;
  totalMarks: number;
  questions: ExamQuestionBody[];
}

export interface CreateScanBody {
  imageKey: string;
  imageName?: string;
  imageSize?: number;
  examId?: UUID;
}

export interface ConfirmScanBody {
  studentId: UUID;
  marks: QuestionMarkEntryBody[];
  remarks?: string;
}

export const EXAM_TYPE_LABELS: Record<ExamType, string> = {
  MIDTERM: 'Midterm',
  FINAL: 'Final',
  SESSIONAL: 'Sessional',
  MAKEUP: 'Makeup',
};

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
