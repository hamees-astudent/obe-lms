package com.lms.modules.studyai;

import com.lms.modules.studyai.RetrievalService.RetrievedChunk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells course content apart from the text around it: a book's preface,
 * acknowledgements, copyright page, table of contents, index and reference
 * list, or a lecture deck's "Course policies", "Office hours" and
 * "Any questions?" slides.
 *
 * <p>Such passages are indexed like any other, but questions must never be
 * written from them ("Who is thanked in the acknowledgements?"), and they
 * should not outrank real content when the assistant searches. Detection is
 * rule-based and runs on the passage at query time, so material indexed
 * before this check existed is covered without re-indexing:
 *
 * <ul>
 *   <li>the heading names a front- or back-matter section;</li>
 *   <li>the text carries publishing boilerplate (ISBN, "All rights
 *       reserved", "Printed in");</li>
 *   <li>the text is mostly a listing rather than prose: dot leaders and page
 *       numbers (a contents page), "term, 12, 45" lines (an index), or
 *       numbered citations with years (a bibliography);</li>
 *   <li>the text is dominated by URLs and e-mail addresses.</li>
 * </ul>
 */
final class InstructionalFilter {

    private InstructionalFilter() {}

    /** Headings of sections that are about the book or the course, not the subject. */
    private static final Pattern NON_INSTRUCTIONAL_HEADING = Pattern.compile(
            "^(?:\\d+(?:\\.\\d+)*\\s+)?(?:"
            + "preface(?:\\s+to.*)?|foreword|prologue|acknowledg(?:e)?ments?|dedication|about\\s+(?:the\\s+)?(?:authors?|editors?|this\\s+book|the\\s+book|the\\s+instructor)"
            + "|copyright(?:\\s+page)?|colophon|errata|disclaimer|imprint"
            + "|(?:table\\s+of\\s+)?contents|brief\\s+contents|detailed\\s+contents|list\\s+of\\s+(?:figures|tables|illustrations|abbreviations|symbols)"
            // Not bare "index", "references" or "permissions": those are topics in databases, C++ and OS courses.
            + "|subject\\s+index|author\\s+index|bibliography|works\\s+cited|further\\s+reading|suggested\\s+reading|recommended\\s+reading"
            + "|notes\\s+on\\s+contributors|contributors|how\\s+to\\s+use\\s+this\\s+book|to\\s+the\\s+(?:student|instructor|reader)"
            + "|course\\s+(?:outline|policies|policy|information|logistics|schedule|overview|details)|grading(?:\\s+policy)?|marks?\\s+distribution"
            + "|assessment\\s+(?:scheme|breakdown)|office\\s+hours|contact(?:\\s+(?:details|information))?|attendance(?:\\s+policy)?"
            + "|academic\\s+(?:integrity|honesty)|plagiarism(?:\\s+policy)?|textbooks?|reading\\s+list|agenda|today'?s\\s+(?:agenda|outline)"
            + "|outline|lecture\\s+outline|thank\\s+you!?|thanks!?|any\\s+questions\\??|questions\\??|q\\s*&\\s*a|the\\s+end"
            + ")\\s*[:.!?]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BOILERPLATE = Pattern.compile(
            "\\bISBN(?:-1[03])?\\b|all\\s+rights\\s+reserved|printed\\s+in\\s+(?:the\\s+)?[A-Z]|library\\s+of\\s+congress"
            + "|cataloging[- ]in[- ]publication|no\\s+part\\s+of\\s+this\\s+(?:publication|book)\\s+may\\s+be\\s+reproduced"
            + "|©\\s*\\d{4}|\\(c\\)\\s*\\d{4}|copyright\\s+©?\\s*\\d{4}|first\\s+(?:published|edition)\\s+\\d{4}",
            Pattern.CASE_INSENSITIVE);

    /** "2.3 Paging ........ 45" — a contents entry. */
    private static final Pattern DOT_LEADER = Pattern.compile("(?:\\.\\s?){4,}\\s*\\d{1,4}\\b");
    /** "paging, 45, 112–118" — an index entry. */
    private static final Pattern INDEX_ENTRY = Pattern.compile("^[\\p{L}][^\\n,]{0,60},\\s*\\d{1,4}(?:[–-]\\d{1,4})?(?:,\\s*\\d{1,4}(?:[–-]\\d{1,4})?)*\\s*$",
            Pattern.MULTILINE);
    /** "[12] A. Author, ... (2019)." or "Author, A. (2019). Title." — a citation. */
    private static final Pattern CITATION = Pattern.compile(
            "(?:^\\s*\\[\\d{1,3}]\\s+.*?(?:19|20)\\d{2})|(?:\\b[A-Z][a-z]+,\\s+(?:[A-Z]\\.\\s*){1,3}(?:,|&|and|\\().*?\\((?:19|20)\\d{2}\\))",
            Pattern.MULTILINE);
    private static final Pattern URL_OR_EMAIL = Pattern.compile(
            "(?i)\\bhttps?://\\S+|\\bwww\\.\\S+|\\b[\\w.+-]+@[\\w-]+\\.[\\w.]+\\b");

    /** Whether the passage is front/back matter or course administration rather than subject content. */
    static boolean isNonInstructional(RetrievedChunk c) {
        return isNonInstructional(c.heading(), c.content());
    }

    static boolean isNonInstructional(String heading, String content) {
        if (heading != null && NON_INSTRUCTIONAL_HEADING.matcher(heading.strip()).matches()) return true;
        String text = content == null ? "" : content;
        String firstLine = text.strip().split("\n", 2)[0].strip();
        if (firstLine.length() <= 60 && NON_INSTRUCTIONAL_HEADING.matcher(firstLine).matches()) return true;

        int words = Math.max(1, Chunker.wordCount(text));
        if (count(BOILERPLATE, text) >= 2 || (count(BOILERPLATE, text) >= 1 && words < 250)) return true;

        int lines = Math.max(1, (int) text.lines().filter(l -> !l.isBlank()).count());
        if (count(DOT_LEADER, text) >= 3) return true;
        if (lines >= 5 && count(INDEX_ENTRY, text) >= Math.max(5, lines * 0.5)) return true;
        if (count(CITATION, text) >= 4) return true;

        int links = count(URL_OR_EMAIL, text);
        return links >= 3 && links * 12 >= words;
    }

    /** Sentences that point at something the reader of a question cannot see. */
    private static final Pattern DEICTIC = Pattern.compile(
            "\\b(?:(?:above|below|following|preceding|previous|adjacent|accompanying|attached)\\s+"
            + "(?:figure|fig\\.?|diagram|image|picture|photo|table|chart|graph|map|code|listing|example|passage|text"
            + "|paragraph|excerpt|slide|equation|screenshot|program|snippet)"
            + "|(?:figure|fig\\.|table|listing|exhibit|equation|eq\\.|example|slide)\\s*\\(?\\d+(?:\\.\\d+)*\\)?"
            + "|(?:this|that)\\s+(?:figure|diagram|image|picture|photo|chart|graph|screenshot|illustration|table)"
            + "|the\\s+(?:figure|picture|photo|screenshot|illustration)"
            + "|(?:shown|illustrated|depicted|given|listed|mentioned|described|discussed)\\s+(?:above|below|earlier|previously)"
            + "|(?:as|see)\\s+(?:above|below))\\b",
            Pattern.CASE_INSENSITIVE);

    /** References to the generator's own source labels or to "the passage", meaningless to a student. */
    private static final Pattern SOURCE_REFERENCE = Pattern.compile(
            "\\[\\s*S\\d{1,2}(?:\\s*[,;]\\s*S?\\d{1,2})*\\s*]|\\(\\s*S\\d{1,2}\\s*\\)"
            + "|\\b(?:source|excerpt|passage)\\s+S?\\d{1,2}\\b|^\\s*(?:in|from)\\s+S\\d{1,2}\\s*,"
            + "|\\b(?:according\\s+to|as\\s+(?:described|stated|mentioned|explained|shown|given)\\s+in|see|per)\\s+S\\d{1,2}\\b"
            + "|<\\s*/?\\s*source"
            + "|\\b(?:according\\s+to|based\\s+on|as\\s+(?:stated|described|mentioned|explained)\\s+in)\\s+(?:the\\s+)?"
            + "(?:given\\s+|provided\\s+|above\\s+)?(?:passage|excerpt|text|source|material|reading|article|extract"
            + "|document|course\\s+material|notes|lecture|slides?)\\b"
            + "|\\b(?:in|from)\\s+the\\s+(?:given\\s+|provided\\s+|above\\s+)?(?:passage|excerpt|extract|reading)\\b"
            + "|\\bthe\\s+(?:passage|excerpt)\\b|\\bthis\\s+(?:passage|excerpt|chapter|lecture|slide)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Whether the text refers to a figure, table, "the passage" or something else absent from the question. */
    static boolean needsAbsentContext(String text) {
        if (text == null) return false;
        if (SOURCE_REFERENCE.matcher(text).find()) return true;
        // "What does the following code print?" is fine when the code is part of the question.
        boolean carriesItsOwn = text.contains("```") || text.contains("\n") || text.contains("$$")
                || text.chars().filter(ch -> ch == '`').count() >= 2;
        Matcher m = DEICTIC.matcher(text);
        while (m.find()) {
            String hit = m.group().toLowerCase(java.util.Locale.ROOT);
            if (carriesItsOwn && hit.startsWith("following")) continue;
            return true;
        }
        return false;
    }

    /** Whether the text points at a figure/table/"above"/"below" — used for choosing template sentences. */
    static boolean refersToFigureOrPosition(String text) {
        return text != null && DEICTIC.matcher(text).find();
    }

    private static int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }
}
