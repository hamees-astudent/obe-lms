package com.lms.modules.exams;

import com.lms.modules.exams.dto.ExtractedMarksSheet;

import java.util.List;

/**
 * Reads a photographed exam copy's first page into structured marks.
 *
 * <p>An implementation is a <em>reader</em>, not an authority: everything it
 * returns is treated as a proposal that a teacher reviews before it becomes an
 * {@link ExamResult}. Implementations should therefore prefer reporting a value
 * as unreadable over guessing at it.
 */
public interface MarksSheetExtractor {

    /**
     * Extract the marks table and identifying details from one page.
     *
     * @param image        the captured page
     * @param contentType  MIME type of the image
     * @param expectations the exam's known question labels and maxima, used to
     *                     steer the read; never used to invent a missing value
     * @return what the page appears to say
     * @throws MarksExtractionException when the page could not be read at all
     */
    ExtractedMarksSheet extract(byte[] image, String contentType, List<QuestionExpectation> expectations);

    /** Identifier of the reader (e.g. the model id), recorded on the scan. */
    String identifier();

    /** One row the extractor should expect to find in the marks table. */
    record QuestionExpectation(String questionNo, String maxMarks) {}

    /** Thrown when a page cannot be read; the scan is recorded as FAILED. */
    class MarksExtractionException extends RuntimeException {
        public MarksExtractionException(String message, Throwable cause) {
            super(message, cause);
        }

        public MarksExtractionException(String message) {
            super(message);
        }
    }
}
