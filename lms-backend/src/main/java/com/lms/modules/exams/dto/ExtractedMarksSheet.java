package com.lms.modules.exams.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * What an extractor read off an exam copy's first page.
 *
 * <p>This is the schema the vision model is constrained to produce, so the
 * field descriptions below are part of the prompt — they are what tells the
 * model to transcribe rather than interpret. Every numeric field is boxed
 * because "could not read this cell" and "this cell says zero" are different
 * facts, and collapsing them would silently turn an unreadable mark into a
 * zero on a student's record.
 */
public record ExtractedMarksSheet(

        @JsonPropertyDescription("""
                The student's roll number / registration number exactly as written on the page, \
                including any hyphens or slashes. Null if absent or unreadable.""")
        String rollNumber,

        @JsonPropertyDescription("""
                The student's full name exactly as written. Null if absent or unreadable.""")
        String studentName,

        @JsonPropertyDescription("""
                The course code as written, e.g. CS-363 or BSCS-501. Null if absent or unreadable.""")
        String courseCode,

        @JsonPropertyDescription("""
                The exam date in ISO format (yyyy-MM-dd). Convert whatever format the page uses, \
                e.g. '12/03/2026' in day-first form becomes '2026-03-12'. If the year is written \
                with two digits assume the 2000s. Null if absent or unreadable.""")
        String examDate,

        @JsonPropertyDescription("""
                One entry per row of the marks table, in the order printed on the page.""")
        List<ExtractedQuestionMark> questionMarks,

        @JsonPropertyDescription("""
                The total written on the page by the marker, if there is one. Report what is \
                written even when it disagrees with the sum of the individual marks — the \
                disagreement is checked later. Null if no total is written.""")
        Double writtenTotal,

        @JsonPropertyDescription("""
                Your overall confidence in this reading: HIGH, MEDIUM or LOW. Use LOW when the \
                handwriting is unclear, the page is blurred or cropped, or the table structure \
                was hard to follow.""")
        String confidence,

        @JsonPropertyDescription("""
                Plain-language notes about anything you could not read or that looked irregular, \
                e.g. 'question 4 mark overwritten twice', 'roll number partly cut off'. \
                Empty list if the page was clean.""")
        List<String> notes
) {

    /** One row of the marks table as read off the page. */
    public record ExtractedQuestionMark(

            @JsonPropertyDescription("""
                    The question label exactly as printed in the table, e.g. '1', '2a', 'Q3'.""")
            String questionNo,

            @JsonPropertyDescription("""
                    The mark awarded. Null if the cell is blank, struck through, or unreadable — \
                    do not guess and do not substitute zero for a blank cell. If a mark was \
                    changed and one value is clearly the final one, report the final value.""")
            Double marksObtained,

            @JsonPropertyDescription("""
                    The maximum mark printed for this question, if the table shows one. \
                    Null if the table does not print maxima.""")
            Double maxMarks
    ) {}
}
