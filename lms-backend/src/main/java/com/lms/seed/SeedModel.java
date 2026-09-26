package com.lms.seed;

import com.lms.seed.SeedCatalog.CourseDef;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Rows the seeder has already written and needs to refer back to. */
final class SeedModel {

    record Person(UUID id, String name, String email) {}

    /** {@code ability} (0–1) drives every mark the student gets, so grades stay consistent. */
    record Student(UUID id, String name, String email, String rollNumber, double ability) {}

    record Course(UUID id, CourseDef def, List<UUID> cloIds) {

        int cloCount() {
            return cloIds.size();
        }

        String cloCode(int index) {
            return "CLO-" + (index + 1);
        }
    }

    /**
     * One academic term. Spring runs 8 Jan – 23 Jun and Fall 8 Jul – 31 Dec,
     * with a two-week break between them (last week of June, first of July).
     */
    record Term(int index, String name, LocalDate start, LocalDate end) {

        static final int FIRST_YEAR = 2022;
        static final int LAST_YEAR = 2026;

        static List<Term> all() {
            List<Term> terms = new ArrayList<>();
            for (int year = FIRST_YEAR; year <= LAST_YEAR; year++) {
                terms.add(new Term(terms.size(), "Spring " + year,
                        LocalDate.of(year, 1, 8), LocalDate.of(year, 6, 23)));
                terms.add(new Term(terms.size(), "Fall " + year,
                        LocalDate.of(year, 7, 8), LocalDate.of(year, 12, 31)));
            }
            return terms;
        }

        int year() {
            return start.getYear();
        }

        boolean spring() {
            return start.getMonthValue() == 1;
        }

        /** The day a given fraction of the way through the term. */
        LocalDate at(double fraction) {
            return start.plusDays(Math.round(fraction * ChronoUnit.DAYS.between(start, end)));
        }

        /** Spring closes during the break; Fall on its last day, keeping every date inside the year. */
        LocalDateTime closedAt() {
            return spring() ? end.plusDays(3).atTime(17, 0) : end.atTime(18, 0);
        }

        /** Batches are admitted every Spring; this is the batch's semester number in this term. */
        int semesterNumberOf(int intakeYear) {
            return index - 2 * (intakeYear - FIRST_YEAR) + 1;
        }
    }

    /**
     * A course offering with everything the activity generator needs.
     *
     * @param students enrolled students who did not drop the course
     * @param cutoff   activity dated on or after this day has not happened yet
     * @param schedule the offering's weekly classes from the generated timetable,
     *                 in week order; empty for past terms, which have none
     */
    record Offering(UUID id, Term term, boolean open, LocalDate cutoff, String programName,
                    Course course, Person teacher, Person assistant, List<Student> students,
                    int dayShift, List<ClassMeeting> schedule) {

        Offering withSchedule(List<ClassMeeting> schedule) {
            return new Offering(id, term, open, cutoff, programName, course, teacher, assistant, students,
                    dayShift, List.copyOf(schedule));
        }
    }

    /** One weekly class meeting from the generated timetable. */
    record ClassMeeting(DayOfWeek day, LocalTime start, LocalTime end, String room, boolean lab) {}

    private SeedModel() {
    }
}
