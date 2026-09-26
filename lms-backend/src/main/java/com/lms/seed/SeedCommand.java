package com.lms.seed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.SpringProperties;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code seed} command: fills the database with demo data and exits.
 *
 * <pre>
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments="seed --clean"
 *   java -jar target/lms-backend-*.jar seed [--clean]
 * </pre>
 *
 * <p>Boots the normal application context without the web server and Kafka
 * consumers, runs {@link DatabaseSeeder}, prints a summary and exits.
 * Any other {@code --spring.*} style argument is passed to Spring, e.g. a
 * different {@code --spring.datasource.url}.
 */
public final class SeedCommand {

    public static final String NAME = "seed";

    private static final String USAGE = """
            Usage: seed [--clean]

              Fills the database with demo data: programs, courses, teachers, 600 students,
              Spring 2022 – Fall 2026 semesters with quizzes, assignments, attendance, exams
              and transcripts. Every user's password is "password".

              --clean   DELETE ALL DATA in every application table first, then seed.
                        Without it, the command refuses to run if demo data already exists.
            """;

    /** Overrides applied as command-line properties so they win over application.yml. */
    private static final List<String> SPRING_OVERRIDES = List.of(
            "--spring.main.web-application-type=none",
            "--spring.main.banner-mode=off",
            // No Kafka consumers: the seeder must not react to (or cause) events
            "--spring.cloud.function.definition=",
            "--spring.cloud.stream.function.autodetect=false",
            "--spring.modulith.republish-outstanding-events-on-restart=false",
            "--spring.datasource.hikari.data-source-properties.reWriteBatchedInserts=true",
            "--logging.level.com.lms=INFO");

    /**
     * @param primarySource the application class; passed in so this package
     *                      does not depend back on the root package
     */
    public static void run(Class<?> primarySource, String[] args) {
        boolean clean = false;
        List<String> springArgs = new ArrayList<>(SPRING_OVERRIDES);
        for (String arg : args) {
            switch (arg) {
                case "--clean" -> clean = true;
                case "--help", "-h" -> {
                    System.out.println(USAGE);
                    return;
                }
                default -> {
                    if (!arg.startsWith("--")) {
                        System.err.println("Unknown argument: " + arg + "\n\n" + USAGE);
                        System.exit(2);
                    }
                    springArgs.add(arg);
                }
            }
        }

        // Batch inserts carry many NULLs; without this Spring asks the driver for
        // each NULL parameter's SQL type, one round-trip at a time.
        SpringProperties.setFlag("spring.jdbc.getParameterType.ignore");

        SpringApplication app = new SpringApplication(primarySource);
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(springArgs.toArray(String[]::new));

        int exitCode;
        try {
            long started = System.currentTimeMillis();
            DatabaseSeeder.Result result = context.getBean(DatabaseSeeder.class).seed(clean, LocalDate.now());
            printSummary(result, (System.currentTimeMillis() - started) / 1000);
            exitCode = 0;
        } catch (DatabaseSeeder.SeedRefusedException e) {
            System.err.println("\nSeeding refused: " + e.getMessage());
            exitCode = 1;
        } catch (RuntimeException e) {
            System.err.println("\nSeeding failed; the database was left unchanged.");
            e.printStackTrace();
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }

    private static void printSummary(DatabaseSeeder.Result result, long seconds) {
        StringBuilder out = new StringBuilder("\nDemo data seeded in ").append(seconds).append(" s")
                .append(" (current term activity up to ").append(result.asOf()).append(").\n\n");
        for (Map.Entry<String, Long> e : result.counts().entrySet()) {
            out.append(String.format("  %-28s %,9d%n", e.getKey(), e.getValue()));
        }
        out.append("\nSample logins (password \"").append(DatabaseSeeder.PASSWORD).append("\"):\n");
        result.sampleLogins().forEach((who, email) -> out.append(String.format("  %-45s %s%n", who, email)));
        out.append("  Students sign in as <roll number>@").append(DatabaseSeeder.STUDENT_DOMAIN)
                .append(", e.g. 241100001001@").append(DatabaseSeeder.STUDENT_DOMAIN).append('\n');
        System.out.println(out);
    }

    private SeedCommand() {
    }
}
