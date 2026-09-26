package com.lms.seed;

import com.lms.seed.SeedCatalog.CourseKind;
import com.lms.seed.SeedCatalog.McqDef;
import com.lms.seed.SeedModel.ClassMeeting;
import com.lms.seed.SeedModel.Course;
import com.lms.seed.SeedModel.Offering;
import com.lms.seed.SeedModel.Student;
import com.lms.seed.SeedModel.Term;
import com.lms.shared.events.AssessmentEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Writes one offering's term: course feed, quizzes, assignments, attendance
 * and exams, with every student's marks.
 *
 * <p>Marks follow each student's ability plus noise, so a student is
 * consistently strong or weak across courses, and the transcript the app
 * later computes from these rows shows a realistic GPA spread.
 *
 * <p>An item dated on or after the offering's cutoff has not happened yet:
 * it may exist (a scheduled quiz, a draft final exam) but has no
 * submissions or results. For closed terms the cutoff is past the term end.
 *
 * <p>Course marks total 100: quizzes 15 (3 × 5), assignments 10 (2 × 5),
 * mid-term 25, final 50. Project courses (FYP, thesis) have no quizzes and
 * two 12.5-mark report assignments instead.
 */
final class ActivityGenerator {

    // When things happen, as a fraction of the term
    private static final double[] QUIZ_AT = {0.12, 0.30, 0.68};
    private static final double[] ASSIGNMENT_DUE_AT = {0.20, 0.55};
    private static final double MIDTERM_AT = 0.42;
    private static final double PRESENTATIONS_AT = 0.80;
    private static final double FINAL_AT = 0.90;

    private static final BigDecimal QUIZ_QUESTION_MARKS = new BigDecimal("2.50");
    private static final int QUESTIONS_PER_QUIZ = 2;
    private static final int[] MIDTERM_QUESTIONS = {5, 5, 5, 10};
    private static final int[] FINAL_QUESTIONS = {10, 10, 10, 10, 10};
    private static final int[] PROJECT_MID_CRITERIA = {10, 10, 5};
    private static final int[] PROJECT_FINAL_CRITERIA = {15, 15, 10, 10};

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    private final SeedTables t;
    private final Random rnd;

    ActivityGenerator(SeedTables tables, Random rnd) {
        this.t = tables;
        this.rnd = rnd;
    }

    void generate(Offering o) {
        boolean project = o.course().def().kind() == CourseKind.PROJECT;
        List<Material> feed = new ArrayList<>();

        if (!project) {
            for (int i = 0; i < QUIZ_AT.length; i++) {
                quiz(o, i, feed);
            }
        }
        for (int i = 0; i < ASSIGNMENT_DUE_AT.length; i++) {
            assignment(o, i, project, feed);
        }
        attendance(o);
        exam(o, false, project);
        exam(o, true, project);

        announcements(o, project, feed);
        resources(o, feed);
        writeFeed(o, feed);
    }

    // ── Quizzes ──────────────────────────────────────────────────────────────

    private void quiz(Offering o, int index, List<Material> feed) {
        LocalDate day = day(o, QUIZ_AT[index]);
        LocalDateTime created = day.minusDays(14).atTime(11, 0);
        if (!happened(o, created.toLocalDate())) return;

        Course course = o.course();
        List<McqDef> bank = SeedCatalog.questionsFor(course.def().code());
        int cloIndex = index % course.cloCount();
        UUID quizId = UUID.randomUUID();
        LocalDateTime opens = day.atTime(9, 0);
        LocalDateTime closes = day.plusDays(2).atTime(23, 59);
        String title = "Quiz " + (index + 1);

        t.quizzes.add(quizId, o.id(), o.teacher().id(), title,
                "Covers " + course.cloCode(cloIndex) + ": " + course.def().clos().get(cloIndex)
                        + ". " + QUESTIONS_PER_QUIZ + " MCQs, 15 minutes, one attempt.",
                15, QUIZ_QUESTION_MARKS.multiply(BigDecimal.valueOf(QUESTIONS_PER_QUIZ)),
                opens, closes, true, true, created);
        t.quizCloMappings.add(quizId, course.cloIds().get(cloIndex), null, created);
        feed.add(new Material("QUIZ", title, "Online quiz on the LMS, open " + opens.toLocalDate() + ".",
                Map.of("quizId", quizId.toString()), created, List.of()));
        t.publishedEvent(SeedTables.ASSESSMENT_LISTENER, AssessmentEvent.builder()
                .occurredAt(instant(created)).action(AssessmentEvent.Action.QUIZ_CREATED)
                .pscId(o.id()).assessmentId(quizId).assessmentTitle(title)
                .courseCode(course.def().code()).courseName(course.def().name()).build(), created);

        List<Question> questions = new ArrayList<>();
        for (int q = 0; q < QUESTIONS_PER_QUIZ; q++) {
            McqDef mcq = bank.get((index * QUESTIONS_PER_QUIZ + q) % bank.size());
            questions.add(question(quizId, mcq, q + 1, created));
        }

        if (!happened(o, closes.toLocalDate().plusDays(1))) return;
        for (Student s : o.students()) {
            if (rnd.nextDouble() < 0.04) continue;                        // missed the quiz
            Map<String, List<String>> answers = new LinkedHashMap<>();
            BigDecimal score = BigDecimal.ZERO;
            for (Question q : questions) {
                boolean right = rnd.nextDouble() < clamp(s.ability() + noise(0.15), 0.05, 0.98);
                String chosen = right ? q.correctId() : q.wrongIds().get(rnd.nextInt(q.wrongIds().size()));
                answers.put(q.id().toString(), List.of(chosen));
                if (right) score = score.add(QUIZ_QUESTION_MARKS);
            }
            LocalDateTime started = opens.plusMinutes(rnd.nextInt(60 * 60));
            LocalDateTime submitted = started.plusMinutes(4 + rnd.nextInt(11)).plusSeconds(rnd.nextInt(60));
            t.quizSubmissions.add(UUID.randomUUID(), quizId, s.id(), t.json(answers), submitted, score, true,
                    started, started);
        }
    }

    private record Question(UUID id, String correctId, List<String> wrongIds) {}

    private Question question(UUID quizId, McqDef mcq, int order, LocalDateTime created) {
        List<String> texts = new ArrayList<>(mcq.wrong());
        texts.add(mcq.correct());
        Collections.shuffle(texts, rnd);

        List<Map<String, String>> options = new ArrayList<>();
        String correctId = null;
        List<String> wrongIds = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String id = String.valueOf((char) ('a' + i));
            options.add(Map.of("id", id, "text", texts.get(i)));
            if (texts.get(i).equals(mcq.correct())) correctId = id;
            else wrongIds.add(id);
        }
        UUID id = UUID.randomUUID();
        t.quizQuestions.add(id, quizId, mcq.question(), "MCQ", t.json(options), t.json(List.of(correctId)),
                QUIZ_QUESTION_MARKS, order, "Correct answer: " + mcq.correct() + ".", created);
        return new Question(id, correctId, wrongIds);
    }

    // ── Assignments ──────────────────────────────────────────────────────────

    private static final String[][] PROJECT_REPORTS = {
            {"Project Proposal Document", "Implementation Progress Report"},
            {"Implementation Report", "Final Project Report"},
            {"Literature Review Chapter", "Research Proposal"},
            {"Results and Analysis Chapter", "Final Thesis Draft"}};

    private void assignment(Offering o, int index, boolean project, List<Material> feed) {
        LocalDate dueDay = day(o, ASSIGNMENT_DUE_AT[index]);
        LocalDateTime due = dueDay.atTime(23, 59);
        LocalDateTime created = dueDay.minusDays(21).atTime(12, 0);
        if (!happened(o, created.toLocalDate())) return;

        Course course = o.course();
        int cloIndex = (index + 1) % course.cloCount();
        BigDecimal total = project ? new BigDecimal("12.50") : new BigDecimal("5.00");
        String title = project ? projectReport(course, index) : "Assignment " + (index + 1);
        String description = project
                ? "Submit the " + title.toLowerCase(Locale.ENGLISH) + " approved by your supervisor. "
                  + "Follow the department template; plagiarism above 19% (Turnitin) is not accepted."
                : "This assignment assesses " + course.cloCode(cloIndex) + ": "
                  + course.def().clos().get(cloIndex) + ". Write your answers in your own words; "
                  + "copied solutions will get zero marks.";
        UUID id = UUID.randomUUID();

        t.assignments.add(id, o.id(), o.teacher().id(), title, description, "TEXT", total, due, true,
                new BigDecimal("10.00"), created);
        t.assignmentCloMappings.add(id, course.cloIds().get(cloIndex), null, created);
        feed.add(new Material("ASSIGNMENT", title, "Due " + dueDay.format(DAY) + " at 11:59 PM.",
                Map.of("assignmentId", id.toString()), created, List.of()));
        t.publishedEvent(SeedTables.ASSESSMENT_LISTENER, AssessmentEvent.builder()
                .occurredAt(instant(created)).action(AssessmentEvent.Action.ASSIGNMENT_CREATED)
                .pscId(o.id()).assessmentId(id).assessmentTitle(title)
                .courseCode(course.def().code()).courseName(course.def().name()).build(), created);

        for (Student s : o.students()) {
            if (rnd.nextDouble() < 0.05) continue;                        // did not submit
            boolean late = rnd.nextDouble() < 0.06;
            LocalDateTime submitted = late
                    ? due.plusHours(2 + rnd.nextInt(40))
                    : due.minusHours(1 + rnd.nextInt(rnd.nextDouble() < 0.3 ? 240 : 72)).minusMinutes(rnd.nextInt(60));
            if (!happened(o, submitted.toLocalDate().plusDays(1))) continue;

            String text = "Submitted by " + s.name() + " (" + s.rollNumber() + ").\n\n"
                    + (project ? "The " + title.toLowerCase(Locale.ENGLISH)
                                 + " is attached as per the department template, covering the objectives, "
                                 + "methodology and progress agreed with the supervisor."
                               : ANSWERS.get(rnd.nextInt(ANSWERS.size())));
            LocalDateTime gradedAt = due.plusDays(3 + rnd.nextInt(8)).withHour(15);
            boolean graded = happened(o, gradedAt.toLocalDate().plusDays(1));
            if (graded) {
                double fraction = clamp(s.ability() + noise(0.12), 0, 1) * (late ? 0.9 : 1.0);
                BigDecimal marks = halves(total.doubleValue() * fraction);
                t.assignmentSubmissions.add(UUID.randomUUID(), id, s.id(), "GRADED", text, submitted, marks,
                        feedback(marks.doubleValue() / total.doubleValue(), late),
                        rnd.nextBoolean() ? o.teacher().id() : o.assistant().id(), gradedAt, submitted);
            } else {
                t.assignmentSubmissions.add(UUID.randomUUID(), id, s.id(), late ? "LATE" : "SUBMITTED", text,
                        submitted, null, null, null, null, submitted);
            }
        }
    }

    private static String projectReport(Course course, int index) {
        return switch (course.def().code()) {
            case "CS-491" -> PROJECT_REPORTS[0][index];
            case "CS-492" -> PROJECT_REPORTS[1][index];
            case "CS-701" -> PROJECT_REPORTS[2][index];
            default -> PROJECT_REPORTS[3][index];
        };
    }

    private static final List<String> ANSWERS = List.of(
            "I have solved all parts of the assignment. For each question I first explain the approach, "
            + "then show the working step by step and finally discuss the result.",
            "Please find my answers below. Question 1 is solved using the method discussed in the lecture; "
            + "for question 2 I compared two approaches and justified my choice.",
            "My solution is organised question-wise. I have included examples to support each answer and "
            + "listed the references I consulted at the end.",
            "Answers to all questions are given below. I was not fully sure about the last part, so I have "
            + "stated my assumptions clearly.");

    private String feedback(double fraction, boolean late) {
        String base = fraction >= 0.85 ? "Excellent work, well explained."
                : fraction >= 0.7 ? "Good effort. Some answers need more detail."
                : fraction >= 0.5 ? "Satisfactory. Revise the concepts discussed in class."
                : "Needs improvement. Please meet me during office hours.";
        return late ? base + " Late submission penalty applied." : base;
    }

    // ── Attendance ───────────────────────────────────────────────────────────

    private void attendance(Offering o) {
        if (!o.schedule().isEmpty()) {
            timetabledAttendance(o);
            return;
        }
        LocalDate lastClass = day(o, FINAL_AT).minusDays(5);
        int week = 1;
        for (LocalDate day = o.term().start().plusDays(o.dayShift()); !day.isAfter(lastClass);
             day = day.plusWeeks(1), week++) {
            if (!happened(o, day.plusDays(1))) break;
            UUID sessionId = UUID.randomUUID();
            LocalDateTime opened = day.atTime(8 + o.dayShift() * 2, 30);
            LocalDateTime closed = opened.plusMinutes(90);
            int clo = Math.min((week - 1) * o.course().cloCount() / 16, o.course().cloCount() - 1);
            t.attendanceSessions.add(sessionId, o.id(), o.teacher().id(), day,
                    "Week " + week + ": " + truncate(o.course().def().clos().get(clo), 200),
                    opened, closed, opened);
            for (Student s : o.students()) {
                double r = rnd.nextDouble();
                double present = 0.62 + 0.30 * s.ability();
                String status = r < present ? "PRESENT"
                        : r < present + 0.05 ? "LATE"
                        : r < present + 0.07 ? "EXCUSED"
                        : "ABSENT";
                String remarks = switch (status) {
                    case "LATE" -> "Arrived " + (5 + rnd.nextInt(20)) + " minutes late";
                    case "EXCUSED" -> rnd.nextBoolean() ? "Medical leave" : "University sports event";
                    default -> null;
                };
                t.attendanceRecords.add(UUID.randomUUID(), sessionId, s.id(), status, o.teacher().id(), remarks,
                        closed);
            }
        }
    }

    /**
     * One session for every timetabled class meeting, each week of the term:
     * held on the meeting's day, opened at its start and closed at its end.
     */
    private void timetabledAttendance(Offering o) {
        LocalDate lastClass = day(o, FINAL_AT).minusDays(5);
        LocalDate monday = o.term().start().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int clos = o.course().cloCount();
        for (int week = 1; !monday.isAfter(lastClass); week++, monday = monday.plusWeeks(1)) {
            int lecture = 0;
            for (ClassMeeting m : o.schedule()) {
                LocalDate day = monday.with(TemporalAdjusters.nextOrSame(m.day()));
                if (day.isBefore(o.term().start()) || day.isAfter(lastClass)) continue;
                // The meeting must be over before the cutoff to have been marked.
                if (!happened(o, day.plusDays(1))) return;
                int clo = Math.min((week - 1) * clos / 16, clos - 1);
                String title = "Week " + week + (m.lab() ? " lab" : ", lecture " + ++lecture) + ": "
                        + o.course().def().clos().get(clo);
                LocalDateTime opened = day.atTime(m.start());
                LocalDateTime closed = day.atTime(m.end());
                UUID sessionId = UUID.randomUUID();
                t.attendanceSessions.add(sessionId, o.id(), o.teacher().id(), day, truncate(title, 255),
                        opened, closed, opened);
                for (Student s : o.students()) {
                    // Labs are better attended than lectures, and early lectures least of all.
                    double present = 0.62 + 0.30 * s.ability() + (m.lab() ? 0.04 : 0)
                            - (m.start().getHour() < 9 ? 0.04 : 0);
                    double r = rnd.nextDouble();
                    String status = r < present ? "PRESENT"
                            : r < present + 0.05 ? "LATE"
                            : r < present + 0.07 ? "EXCUSED"
                            : "ABSENT";
                    String remarks = switch (status) {
                        case "LATE" -> "Arrived " + (5 + rnd.nextInt(20)) + " minutes late";
                        case "EXCUSED" -> rnd.nextBoolean() ? "Medical leave" : "University sports event";
                        default -> null;
                    };
                    t.attendanceRecords.add(UUID.randomUUID(), sessionId, s.id(), status, o.teacher().id(),
                            remarks, closed);
                }
            }
        }
    }

    // ── Exams ────────────────────────────────────────────────────────────────

    private void exam(Offering o, boolean finalExam, boolean project) {
        LocalDate midDay = day(o, MIDTERM_AT);
        LocalDate examDay = finalExam ? day(o, FINAL_AT) : midDay;
        // Both papers are set up before the mid-term, so the final shows as a draft during the term
        LocalDateTime created = midDay.minusDays(finalExam ? 7 : 21).atTime(14, 0);
        if (!happened(o, created.toLocalDate())) return;

        Course course = o.course();
        int[] marks = project
                ? (finalExam ? PROJECT_FINAL_CRITERIA : PROJECT_MID_CRITERIA)
                : (finalExam ? FINAL_QUESTIONS : MIDTERM_QUESTIONS);
        int total = 0;
        for (int m : marks) total += m;
        String title = project
                ? (finalExam ? (thesis(course) ? "Final Thesis Defence" : "Final Project Defence")
                             : (thesis(course) ? "Research Proposal Defence" : "Proposal Defence"))
                : (finalExam ? "Final Examination" : "Mid-term Examination") + " " + o.term().name();
        String type = project ? "SESSIONAL" : finalExam ? "FINAL" : "MIDTERM";

        // Mid-term questions cover the first half of the course, the final covers all of it
        int cloSpan = finalExam ? course.cloCount() : Math.max(1, (course.cloCount() + 1) / 2);
        List<UUID> questionIds = new ArrayList<>();
        UUID examId = UUID.randomUUID();
        for (int q = 0; q < marks.length; q++) {
            UUID qid = UUID.randomUUID();
            questionIds.add(qid);
            t.examQuestions.add(qid, examId, "Q" + (q + 1), BigDecimal.valueOf(marks[q]), q + 1, created);
            t.examQuestionCloMappings.add(qid, course.cloIds().get(q % cloSpan), null, created);
        }

        boolean held = happened(o, examDay.plusDays(1));
        List<Object[]> results = new ArrayList<>();
        LocalDateTime lastRecorded = null;
        if (held) {
            for (Student s : o.students()) {
                if (rnd.nextDouble() < 0.01) continue;                    // absent from the exam
                LocalDateTime recorded = examDay.plusDays(2 + rnd.nextInt(6)).atTime(10 + rnd.nextInt(7), rnd.nextInt(60));
                if (!happened(o, recorded.toLocalDate().plusDays(1))) continue;
                UUID resultId = UUID.randomUUID();
                BigDecimal sum = BigDecimal.ZERO;
                for (int q = 0; q < marks.length; q++) {
                    BigDecimal m = halves(marks[q] * clamp(s.ability() + noise(0.15), 0, 1));
                    sum = sum.add(m);
                    t.examQuestionMarks.add(UUID.randomUUID(), resultId, questionIds.get(q), m, recorded);
                }
                results.add(new Object[]{resultId, examId, s.id(), sum, "MANUAL", o.teacher().id(), recorded,
                        null, recorded});
                if (lastRecorded == null || recorded.isAfter(lastRecorded)) lastRecorded = recorded;
            }
        }

        String status = !o.open() ? "LOCKED"
                : !held ? "DRAFT"
                : lastRecorded != null && happened(o, lastRecorded.toLocalDate().plusDays(7)) ? "LOCKED"
                : "OPEN";
        t.exams.add(examId, o.id(), o.teacher().id(), title, type, examDay, BigDecimal.valueOf(total), status,
                created);
        for (Object[] r : results) t.examResults.add(r);
    }

    private static boolean thesis(Course course) {
        return course.def().code().startsWith("CS-70");
    }

    // ── Course feed ──────────────────────────────────────────────────────────

    private record Material(String type, String title, String description, Map<String, Object> content,
                            LocalDateTime created, List<UUID> cloIds) {}

    private void announcements(Offering o, boolean project, List<Material> feed) {
        Course c = o.course();
        String code = c.def().code();
        String name = c.def().name();
        LocalDate start = o.term().start();

        announce(feed, "Welcome to " + code + " " + name,
                "Assalam-o-Alaikum and welcome to " + name + " (" + o.term().name() + "). "
                + classTimes(o) + " Please go through the "
                + "course outline and the recommended resources posted here.\n\n"
                + (project ? "Evaluation: reports 25%, mid-term evaluation 25%, final evaluation 50%."
                           : "Grading: quizzes 15%, assignments 10%, mid-term 25%, final exam 50%.")
                + "\nOffice hours: " + weekday(start.plusDays(o.dayShift() + 1)) + ", 11:00 AM – 1:00 PM.\n\n— "
                + o.teacher().name(), start.minusDays(3).atTime(10, 0));

        LocalDate mid = day(o, MIDTERM_AT);
        LocalDate fin = day(o, FINAL_AT);
        if (project) {
            String what = thesis(c) ? "research proposal defence" : "FYP proposal defence";
            announce(feed, capitalise(what) + " schedule",
                    "The " + what + " will be held on " + mid.format(DAY) + " in the Seminar Hall, starting at "
                    + "10:00 AM. Each group gets 15 minutes to present followed by questions from the evaluation "
                    + "panel. Upload your slides on the LMS a day before and bring three printed copies of your "
                    + "document.", mid.minusDays(10).atTime(9, 30));
            String finalWhat = thesis(c) ? "final thesis defence" : "final project defence and demonstration";
            announce(feed, capitalise(finalWhat),
                    "The " + finalWhat + " is scheduled for " + fin.format(DAY) + ". The external examiner will "
                    + "evaluate your work, so make sure the complete report is signed by your supervisor and "
                    + "submitted to the department office one week before.", fin.minusDays(12).atTime(9, 30));
        } else {
            announce(feed, "Mid-term examination schedule",
                    "The mid-term exam of " + code + " " + name + " will be held on " + mid.format(DAY)
                    + " at " + (9 + o.dayShift()) + ":00 in Examination Hall " + (char) ('A' + o.dayShift() % 3)
                    + ". It covers everything taught up to the exam week. Bring your university ID card; "
                    + "mobile phones are not allowed in the examination hall.", mid.minusDays(7).atTime(10, 0));
            LocalDate presentations = day(o, PRESENTATIONS_AT);
            announce(feed, "Semester project presentations",
                    "Semester project presentations will be held on " + presentations.format(DAY)
                    + " during class time. Each group has 10 minutes to present and 5 minutes for questions. "
                    + "Upload your slides on the LMS one day before your presentation.",
                    presentations.minusDays(10).atTime(10, 0));
            announce(feed, "Final examination",
                    "The final exam of " + code + " is scheduled on " + fin.format(DAY) + " at "
                    + (9 + o.dayShift()) + ":00. The paper is comprehensive and covers all CLOs. As per "
                    + "university policy, students with attendance below 75% will not be allowed to sit the exam.",
                    fin.minusDays(10).atTime(10, 0));
        }

        List<Material> events = universityEvents(o.term());
        events.removeIf(e -> {
            LocalDate posted = e.created().toLocalDate();
            return posted.isBefore(start) || posted.isAfter(o.term().end()) || !happened(o, posted);
        });
        if (!events.isEmpty()) feed.add(events.get(rnd.nextInt(events.size())));
    }

    private List<Material> universityEvents(Term term) {
        List<Material> events = new ArrayList<>();
        int y = term.year();
        if (term.spring()) {
            holiday(events, term, MonthDay.of(2, 5), "Kashmir Solidarity Day",
                    "The university will remain closed on 5 February on account of Kashmir Solidarity Day. "
                    + "The missed class will be rescheduled; the make-up class will be announced here.");
            holiday(events, term, MonthDay.of(3, 23), "Pakistan Day holiday",
                    "23 March is a public holiday on account of Pakistan Day. There will be no class; "
                    + "the Pakistan Day ceremony will be held in the main lawn on 22 March at 11:00 AM.");
            holiday(events, term, MonthDay.of(5, 1), "Labour Day holiday",
                    "The university will remain closed on 1 May (Labour Day). Classes will resume as usual "
                    + "on the next working day.");
            eid(events, term, EID_UL_FITR.get(y), "Eid ul Fitr");
            eid(events, term, EID_UL_ADHA.get(y), "Eid ul Adha");
            event(events, term.at(0.15), "Convocation " + y,
                    "The annual convocation for graduating batches will be held on "
                    + term.at(0.15).plusDays(10).format(DAY) + " in the Main Auditorium. Classes will remain "
                    + "suspended on that day. Students are welcome to attend.");
        } else {
            holiday(events, term, MonthDay.of(8, 14), "Independence Day celebrations",
                    "Pakistan Zindabad! The flag hoisting ceremony for Independence Day will be held on "
                    + "14 August at 9:00 AM in the main lawn. The university will remain closed for the rest "
                    + "of the day.");
            holiday(events, term, MonthDay.of(11, 9), "Iqbal Day holiday",
                    "The university will remain closed on 9 November on account of Iqbal Day. A seminar on "
                    + "the poetry and philosophy of Allama Iqbal will be held on 10 November in the auditorium.");
            eid(events, term, EID_UL_ADHA.get(y), "Eid ul Adha");
            event(events, term.at(0.18), "HEC need-based scholarship applications",
                    "Applications for the HEC Need-Based Scholarship are now open. Eligible students should "
                    + "collect the form from the Financial Aid Office and submit it with the required documents "
                    + "within two weeks.");
        }
        event(events, term.at(0.35), "Annual Sports Gala",
                "The Annual Sports Gala starts next week. Cricket, football, badminton and table tennis "
                + "registrations are open at the sports office. Classes on the opening day will end at 12:00 PM.");
        event(events, term.at(0.50), "Blood donation drive",
                "The Student Welfare Society, in collaboration with the Fatimid Foundation, is organising a "
                + "blood donation drive on campus this Thursday from 10:00 AM to 3:00 PM. Please participate.");
        event(events, term.at(0.60), "Career Fair " + term.name(),
                "The Career Development Centre is holding the Career Fair next week. Leading software houses "
                + "will be conducting on-the-spot interviews for internships and jobs; bring updated CVs.");
        event(events, term.at(0.45), "Guest talk on industry trends",
                "A guest talk on current industry trends by a senior engineer from a leading software company "
                + "will be held in the Seminar Hall on Wednesday at 2:00 PM. Attendance is encouraged.");
        return events;
    }

    /** Eid dates (Pakistan), for holiday announcements. */
    private static final Map<Integer, LocalDate> EID_UL_FITR = Map.of(
            2022, LocalDate.of(2022, 5, 3), 2023, LocalDate.of(2023, 4, 22), 2024, LocalDate.of(2024, 4, 10),
            2025, LocalDate.of(2025, 3, 31), 2026, LocalDate.of(2026, 3, 20));
    private static final Map<Integer, LocalDate> EID_UL_ADHA = Map.of(
            2022, LocalDate.of(2022, 7, 10), 2023, LocalDate.of(2023, 6, 29), 2024, LocalDate.of(2024, 6, 17),
            2025, LocalDate.of(2025, 6, 7), 2026, LocalDate.of(2026, 5, 27));

    private void holiday(List<Material> events, Term term, MonthDay day, String title, String body) {
        event(events, day.atYear(term.year()).minusDays(3), title, body);
    }

    private void eid(List<Material> events, Term term, LocalDate eid, String name) {
        if (eid == null) return;
        event(events, eid.minusDays(5), name + " holidays",
                "The university will remain closed from " + eid.minusDays(1).format(DAY) + " to "
                + eid.plusDays(3).format(DAY) + " for " + name + ". Classes will resume on "
                + eid.plusDays(4).format(DAY) + ". Eid Mubarak!");
    }

    private void event(List<Material> events, LocalDate posted, String title, String body) {
        events.add(new Material("ANNOUNCEMENT", title, null, Map.of("body", body), posted.atTime(9, 0), List.of()));
    }

    private void announce(List<Material> feed, String title, String body, LocalDateTime posted) {
        feed.add(new Material("ANNOUNCEMENT", title, null, Map.of("body", body), posted, List.of()));
    }

    private void resources(Offering o, List<Material> feed) {
        Course c = o.course();
        String name = c.def().name();
        LocalDateTime posted = o.term().start().minusDays(2).atTime(11, 0);
        feed.add(new Material("URL", "Reference: " + name, "Background reading for the course.",
                Map.of("url", "https://en.wikipedia.org/w/index.php?search=" + encode(name),
                        "linkText", name + " on Wikipedia"),
                posted, List.of(c.cloIds().get(0))));
        feed.add(new Material("URL", "Recommended research articles", "Articles for further reading.",
                Map.of("url", "https://scholar.google.com/scholar?q=" + encode(name),
                        "linkText", "Google Scholar: " + name),
                posted.plusDays(20), List.of(c.cloIds().get(c.cloCount() - 1))));
        feed.add(new Material("VIDEO_LINK", "Recorded lectures: " + name,
                "Video lectures that follow the course outline.",
                Map.of("url", "https://www.youtube.com/results?search_query=" + encode(name + " lectures"),
                        "platform", "YOUTUBE", "durationSeconds", 2700 + 60 * rnd.nextInt(30)),
                posted.plusDays(7), c.cloIds()));
    }

    private void writeFeed(Offering o, List<Material> feed) {
        feed.removeIf(m -> !happened(o, m.created().toLocalDate()));
        feed.sort(Comparator.comparing(Material::created));
        int order = 1;
        for (Material m : feed) {
            UUID id = UUID.randomUUID();
            t.courseMaterials.add(id, o.id(), o.teacher().id(), m.type(), m.title(), m.description(),
                    t.json(m.content()), true, order++, m.created());
            for (UUID clo : m.cloIds()) {
                t.materialCloMappings.add(id, clo, null, m.created());
            }
        }
    }

    /** When and where the course meets, from the timetable when the term has one. */
    private static String classTimes(Offering o) {
        LocalDate start = o.term().start();
        if (o.schedule().isEmpty()) {
            String room = "Room " + roomPrefix(o.course().def().code()) + "-"
                    + (101 + (Math.abs(o.id().hashCode()) % 210));
            return "Classes begin on " + start.plusDays(o.dayShift()).format(DAY) + " in " + room + ".";
        }
        StringBuilder text = new StringBuilder("Our weekly classes:");
        LocalDate first = null;
        for (ClassMeeting m : o.schedule()) {
            text.append("\n• ").append(m.lab() ? "Lab" : "Lecture").append(": ")
                    .append(m.day().getDisplayName(TextStyle.FULL, Locale.ENGLISH)).append(' ')
                    .append(m.start()).append('–').append(m.end()).append(", ").append(m.room());
            LocalDate day = start.with(TemporalAdjusters.nextOrSame(m.day()));
            if (first == null || day.isBefore(first)) first = day;
        }
        return text.append("\nThe first class is on ").append(first.format(DAY)).append('.').toString();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** The day of an activity; offerings are spread over the week so not everything lands on one day. */
    private static LocalDate day(Offering o, double fraction) {
        return o.term().at(fraction).plusDays(o.dayShift());
    }

    private static boolean happened(Offering o, LocalDate day) {
        return day.isBefore(o.cutoff());
    }

    private double noise(double sd) {
        return rnd.nextGaussian() * sd;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static BigDecimal halves(double v) {
        return BigDecimal.valueOf(Math.round(v * 2) / 2.0).setScale(2, RoundingMode.HALF_UP);
    }

    private static java.time.Instant instant(LocalDateTime when) {
        return when.atZone(java.time.ZoneId.systemDefault()).toInstant();
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String weekday(LocalDate day) {
        return day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static String capitalise(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String roomPrefix(String courseCode) {
        return switch (courseCode.substring(0, 2)) {
            case "MT", "HU", "NS" -> "A";
            default -> courseCode.substring(0, 2);
        };
    }
}
