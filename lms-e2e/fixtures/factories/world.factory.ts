/**
 * world.factory.ts
 *
 * Seeds a minimal but complete LMS world in a single call.
 * Returns all created entity IDs so tests can reference them.
 *
 * Creation order:
 *   program → semester → course → offering → teacher user → student user → enrollment
 */

import { ApiHelper } from '../../helpers/api.helper';
import { createProgram, buildProgram } from './program.factory';
import { createSemester, buildSemester } from './semester.factory';
import { createCourse, buildCourse } from './course.factory';
import { createOffering } from './offering.factory';
import { createUser, buildTeacher, buildStudent } from './user.factory';
import { enrollStudent } from './enrollment.factory';

export interface WorldContext {
  programId: string;
  semesterId: string;
  courseId: string;
  /** Offering / PSC id — used in most API calls */
  pscId: string;
  teacherId: string;
  /** Access token for the teacher user created in this world */
  teacherToken: string;
  studentId: string;
  studentEmail: string;
  studentPassword: string;
  enrollmentId: string;
}

const DEFAULT_STUDENT_PASSWORD = 'Student@12345';
const DEFAULT_TEACHER_PASSWORD = 'Teacher@12345';

/**
 * Seeds a complete test world and returns stable IDs.
 *
 * @param api          - Playwright APIRequestContext wrapper
 * @param adminToken   - Admin JWT (from auth storage or fixture)
 * @param loginFn      - Callable that exchanges email+password for a JWT
 */
export async function seedWorld(
  api: ApiHelper,
  adminToken: string,
  loginFn: (email: string, password: string) => Promise<string>,
): Promise<WorldContext> {
  // 1. Program
  const program = await createProgram(api, adminToken, buildProgram());

  // 2. Semester
  const semester = await createSemester(api, adminToken, program.id, buildSemester());

  // 3. Course
  const course = await createCourse(api, adminToken, buildCourse());

  // 4. Teacher user
  const teacherPayload = buildTeacher({ password: DEFAULT_TEACHER_PASSWORD });
  const teacher = await createUser(api, adminToken, teacherPayload);
  const teacherToken = await loginFn(teacherPayload.email, DEFAULT_TEACHER_PASSWORD);

  // 5. Offering
  const offering = await createOffering(api, adminToken, {
    semesterId: semester.id,
    courseId: course.id,
    teacherId: teacher.id,
    maxCapacity: 40,
  });

  // 6. Student user + enrollment
  const studentPayload = buildStudent({ password: DEFAULT_STUDENT_PASSWORD });
  const student = await createUser(api, adminToken, studentPayload);
  const enrollment = await enrollStudent(api, adminToken, offering.id, student.id);

  return {
    programId: program.id,
    semesterId: semester.id,
    courseId: course.id,
    pscId: offering.id,
    teacherId: teacher.id,
    teacherToken,
    studentId: student.id,
    studentEmail: studentPayload.email,
    studentPassword: DEFAULT_STUDENT_PASSWORD,
    enrollmentId: enrollment.id,
  };
}
