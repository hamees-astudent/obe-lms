package com.lms.seed;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static reference data for the demo seeder: programs, PLOs, curricula,
 * courses, question bank, names and grading scales.
 *
 * <p>Courses and quiz questions live in {@code seed/courses.tsv} and
 * {@code seed/quiz-bank.tsv}; everything with structure lives here.
 */
final class SeedCatalog {

    // ── Records ──────────────────────────────────────────────────────────────

    enum CourseKind { REGULAR, PROJECT }

    record CourseDef(String code, String name, int creditHours, CourseKind kind,
                     String description, List<String> clos) {

        String department() {
            return switch (code.substring(0, 2)) {
                case "SE" -> DEPT_SE;
                case "AI" -> DEPT_AI;
                case "CS" -> DEPT_CS;
                default   -> DEPT_HS;
            };
        }
    }

    record McqDef(String question, String correct, List<String> wrong) {}

    record PloDef(String title, String description) {}

    /**
     * @param cohortNo the C digit of roll numbers ({@code YY110000CNNN}); one
     *                 cohort per program per intake year
     * @param semesters course codes per semester, index 0 = semester 1
     */
    record ProgramDef(String code, String name, String description, int durationYears,
                      int cohortNo, int maxCapacity, List<PloDef> plos,
                      List<List<String>> semesters) {}

    record GradeBand(String letter, double min, double max, double points) {}

    record GradingScaleDef(String name, String programCode, List<GradeBand> bands) {}

    // ── Departments ──────────────────────────────────────────────────────────

    static final String DEPT_CS = "Computer Science";
    static final String DEPT_SE = "Software Engineering";
    static final String DEPT_AI = "Artificial Intelligence";
    static final String DEPT_HS = "Humanities and Sciences";

    // ── Programs ─────────────────────────────────────────────────────────────

    private static final List<String> BS_SEM_1 = List.of("CS-101", "CS-103", "MT-101", "HU-101", "HU-103");
    private static final List<String> BS_SEM_2 = List.of("CS-102", "CS-104", "CS-106", "HU-102", "NS-101");

    private static List<PloDef> bsPlos(String discipline) {
        return List.of(
                new PloDef("Academic Education",
                        "Complete an accredited programme of study designed to prepare graduates as "
                        + discipline + " professionals."),
                new PloDef("Knowledge for Solving Computing Problems",
                        "Apply knowledge of computing fundamentals, a computing specialisation, mathematics, "
                        + "science and domain knowledge to the abstraction and conceptualisation of computing models."),
                new PloDef("Problem Analysis",
                        "Identify, formulate, research literature and solve complex computing problems, reaching "
                        + "substantiated conclusions using first principles of mathematics and computing."),
                new PloDef("Design/Development of Solutions",
                        "Design and evaluate solutions for complex computing problems, and design and evaluate systems, "
                        + "components or processes that meet specified needs."),
                new PloDef("Modern Tool Usage",
                        "Create, select or adapt and apply appropriate techniques, resources and modern computing tools "
                        + "to complex computing activities, with an understanding of their limitations."),
                new PloDef("Individual and Teamwork",
                        "Function effectively as an individual and as a member or leader of diverse teams "
                        + "in multi-disciplinary settings."),
                new PloDef("Communication",
                        "Communicate effectively with the computing community and society at large about complex "
                        + "computing activities through reports, documentation and presentations."),
                new PloDef("Computing Professionalism and Society",
                        "Understand and assess societal, health, safety, legal and cultural issues within local and "
                        + "global contexts, and the consequent responsibilities relevant to professional practice."),
                new PloDef("Ethics",
                        "Understand and commit to professional ethics, responsibilities and norms of professional "
                        + discipline + " practice."),
                new PloDef("Life-long Learning",
                        "Recognise the need for, and have the ability to engage in, independent learning for continual "
                        + "development as a " + discipline + " professional."));
    }

    static final List<ProgramDef> PROGRAMS = List.of(
            new ProgramDef("BSCS", "BS Computer Science",
                    "Four-year undergraduate programme covering the theory, design and development of software "
                    + "and computing systems, following the HEC/NCEAC curriculum.",
                    4, 1, 120, bsPlos("computer science"),
                    List.of(BS_SEM_1, BS_SEM_2,
                            List.of("CS-201", "CS-203", "MT-201", "SE-201", "HU-201"),
                            List.of("CS-202", "CS-204", "CS-206", "MT-202", "HU-202"),
                            List.of("CS-301", "CS-303", "AI-301", "CS-305", "MT-301"),
                            List.of("CS-302", "CS-304", "CS-306", "CS-308", "HU-302"),
                            List.of("CS-491", "AI-302", "CS-401", "CS-403", "HU-401"),
                            List.of("CS-492", "CS-402", "CS-404", "CS-406", "HU-402"))),
            new ProgramDef("BSSE", "BS Software Engineering",
                    "Four-year undergraduate programme focused on the systematic engineering of large software "
                    + "systems: requirements, design, construction, quality and project management.",
                    4, 2, 120, bsPlos("software engineering"),
                    List.of(BS_SEM_1, BS_SEM_2,
                            List.of("CS-201", "CS-203", "SE-201", "MT-201", "HU-201"),
                            List.of("CS-202", "CS-204", "SE-202", "MT-202", "HU-202"),
                            List.of("SE-301", "SE-303", "CS-301", "CS-305", "CS-308"),
                            List.of("SE-302", "SE-304", "CS-306", "AI-301", "HU-302"),
                            List.of("CS-491", "SE-401", "SE-403", "CS-403", "HU-401"),
                            List.of("CS-492", "SE-402", "SE-404", "CS-401", "HU-402"))),
            new ProgramDef("BSAI", "BS Artificial Intelligence",
                    "Four-year undergraduate programme in artificial intelligence: machine learning, deep learning, "
                    + "computer vision, natural language processing and robotics.",
                    4, 3, 120, bsPlos("artificial intelligence"),
                    List.of(BS_SEM_1, BS_SEM_2,
                            List.of("CS-201", "AI-201", "MT-201", "CS-203", "HU-201"),
                            List.of("CS-202", "CS-204", "AI-301", "MT-202", "HU-202"),
                            List.of("AI-302", "AI-303", "CS-301", "CS-206", "MT-301"),
                            List.of("AI-304", "AI-306", "AI-308", "CS-306", "HU-302"),
                            List.of("CS-491", "AI-401", "AI-403", "CS-401", "HU-401"),
                            List.of("CS-492", "AI-402", "CS-404", "AI-404", "HU-402"))),
            new ProgramDef("MSCS", "MS Computer Science",
                    "Two-year graduate programme with advanced coursework and a research thesis in computer science.",
                    2, 4, 40,
                    List.of(
                            new PloDef("Advanced Knowledge",
                                    "Apply advanced knowledge of computer science theory and systems to complex problems."),
                            new PloDef("Research Skills",
                                    "Conduct independent research using appropriate methodology and critical review of literature."),
                            new PloDef("Critical Analysis",
                                    "Critically analyse and evaluate computing solutions and research findings."),
                            new PloDef("Communication",
                                    "Communicate research effectively through publications, theses and presentations."),
                            new PloDef("Research Ethics",
                                    "Adhere to ethical principles and academic integrity in research and professional practice."),
                            new PloDef("Life-long Learning",
                                    "Engage in continued learning and keep abreast of developments in computing research.")),
                    List.of(
                            List.of("CS-601", "CS-603", "CS-605", "CS-607"),
                            List.of("CS-602", "CS-604", "CS-606", "AI-651"),
                            List.of("CS-701", "CS-703", "CS-705", "AI-751"),
                            List.of("CS-702", "CS-704", "CS-706", "AI-752"))));

    // ── Grading scales ───────────────────────────────────────────────────────

    static final GradingScaleDef HEC_SCALE = new GradingScaleDef("HEC Standard Grading Scale", null, List.of(
            new GradeBand("A",  85, 100, 4.00),
            new GradeBand("A-", 80, 85,  3.66),
            new GradeBand("B+", 75, 80,  3.33),
            new GradeBand("B",  71, 75,  3.00),
            new GradeBand("B-", 68, 71,  2.66),
            new GradeBand("C+", 64, 68,  2.33),
            new GradeBand("C",  61, 64,  2.00),
            new GradeBand("C-", 58, 61,  1.66),
            new GradeBand("D+", 54, 58,  1.30),
            new GradeBand("D",  50, 54,  1.00),
            new GradeBand("F",  0,  50,  0.00)));

    static final GradingScaleDef MS_SCALE = new GradingScaleDef("MS Graduate Grading Scale", "MSCS", List.of(
            new GradeBand("A",  85, 100, 4.00),
            new GradeBand("A-", 80, 85,  3.66),
            new GradeBand("B+", 75, 80,  3.33),
            new GradeBand("B",  70, 75,  3.00),
            new GradeBand("B-", 65, 70,  2.66),
            new GradeBand("C+", 60, 65,  2.33),
            new GradeBand("C",  55, 60,  2.00),
            new GradeBand("F",  0,  55,  0.00)));

    static final List<GradingScaleDef> GRADING_SCALES = List.of(HEC_SCALE, MS_SCALE);

    // ── People ───────────────────────────────────────────────────────────────

    static final List<String> MALE_FIRST_NAMES = List.of(
            "Muhammad", "Ahmed", "Ali", "Hassan", "Hussain", "Usman", "Bilal", "Hamza", "Umar", "Abdullah",
            "Zain", "Saad", "Faisal", "Imran", "Kamran", "Asad", "Talha", "Haris", "Danish", "Fahad",
            "Waqas", "Shahzaib", "Arslan", "Owais", "Junaid", "Adeel", "Rizwan", "Salman", "Hamid", "Taimoor",
            "Shoaib", "Noman", "Farhan", "Zeeshan", "Moiz", "Anas", "Huzaifa", "Rehan", "Sohail", "Waleed",
            "Ammar", "Areeb", "Ibrahim", "Musa", "Yasir", "Nabeel", "Jawad", "Sameer", "Tariq", "Babar");

    static final List<String> FEMALE_FIRST_NAMES = List.of(
            "Ayesha", "Fatima", "Zainab", "Maryam", "Hira", "Sana", "Amna", "Iqra", "Mahnoor", "Areeba",
            "Laiba", "Maham", "Hafsa", "Rabia", "Sidra", "Kinza", "Anum", "Mehwish", "Nimra", "Alishba",
            "Eman", "Khadija", "Noor", "Aiman", "Mariam", "Bushra", "Saba", "Javeria", "Aqsa", "Tayyaba",
            "Rida", "Minahil", "Zoha", "Hania", "Emaan", "Samina", "Nida", "Shiza", "Momina", "Yusra");

    static final List<String> SURNAMES = List.of(
            "Khan", "Ahmed", "Ali", "Siddiqui", "Qureshi", "Malik", "Chaudhry", "Butt", "Raza", "Hussain",
            "Shah", "Baig", "Mirza", "Abbasi", "Memon", "Baloch", "Awan", "Niazi", "Rana", "Sheikh",
            "Javed", "Iqbal", "Anwar", "Aslam", "Rashid", "Farooq", "Hashmi", "Rizvi", "Jafri", "Bukhari",
            "Gillani", "Kazmi", "Naqvi", "Zaidi", "Cheema", "Bajwa", "Warraich", "Khattak", "Yousafzai", "Afridi",
            "Durrani", "Soomro", "Jatoi", "Laghari", "Mengal", "Tariq", "Nawaz", "Saleem", "Akhtar", "Mughal");

    static final List<String> CITIES = List.of(
            "Karachi", "Lahore", "Islamabad", "Rawalpindi", "Faisalabad", "Multan", "Peshawar", "Quetta",
            "Hyderabad", "Sialkot", "Gujranwala", "Bahawalpur", "Sukkur", "Abbottabad", "Mardan", "Sargodha");

    static final List<String> AREAS = List.of(
            "Gulshan-e-Iqbal", "DHA Phase 5", "Model Town", "Johar Town", "G-11/2", "Satellite Town",
            "Clifton Block 4", "Gulberg III", "Hayatabad Phase 3", "Cantt", "Bahria Town", "North Nazimabad",
            "Wapda Town", "Samanabad", "University Town", "Garden Town");

    /** Teachers per department, with the designation ladder cycled through. */
    static final Map<String, Integer> TEACHERS_PER_DEPARTMENT = orderedMap(
            DEPT_CS, 12, DEPT_SE, 5, DEPT_AI, 5, DEPT_HS, 6);

    static final List<String> DESIGNATIONS = List.of(
            "Lecturer", "Assistant Professor", "Lecturer", "Associate Professor", "Assistant Professor", "Professor");

    static final int ASSISTANT_COUNT = 12;

    // ── Loaded resources ─────────────────────────────────────────────────────

    private static final Map<String, CourseDef> COURSES = loadCourses();
    private static final Map<String, List<McqDef>> QUIZ_BANK = loadQuizBank();

    static Map<String, CourseDef> courses() {
        return COURSES;
    }

    static CourseDef course(String code) {
        CourseDef c = COURSES.get(code);
        if (c == null) {
            throw new IllegalStateException("Course " + code + " is used in a curriculum but missing from courses.tsv");
        }
        return c;
    }

    static List<McqDef> questionsFor(String courseCode) {
        return QUIZ_BANK.getOrDefault(courseCode, List.of());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Roll number {@code YY110000CNNN}: intake year, fixed infix, cohort digit, serial. */
    static String rollNumber(int intakeYear, int cohortNo, int serial) {
        return String.format("%02d110000%d%03d", intakeYear % 100, cohortNo, serial);
    }

    private static Map<String, CourseDef> loadCourses() {
        Map<String, CourseDef> map = new LinkedHashMap<>();
        for (String[] f : readTable("seed/courses.tsv", 6)) {
            map.put(f[0], new CourseDef(f[0], f[1], Integer.parseInt(f[2]), CourseKind.valueOf(f[3]),
                    f[4], Arrays.stream(f[5].split(";")).map(String::trim).toList()));
        }
        return map;
    }

    private static Map<String, List<McqDef>> loadQuizBank() {
        Map<String, List<McqDef>> map = new LinkedHashMap<>();
        for (String[] f : readTable("seed/quiz-bank.tsv", 6)) {
            map.computeIfAbsent(f[0], k -> new ArrayList<>())
                    .add(new McqDef(f[1], f[2], List.of(f[3], f[4], f[5])));
        }
        return map;
    }

    private static List<String[]> readTable(String path, int columns) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\|", -1);
                if (fields.length != columns) {
                    throw new IllegalStateException(path + ": expected " + columns + " fields in: " + line);
                }
                rows.add(fields);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
        return rows;
    }

    private static Map<String, Integer> orderedMap(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    private SeedCatalog() {
    }
}
