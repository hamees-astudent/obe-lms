package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.modules.studyai.RetrievalService.RetrievedChunk;
import com.lms.modules.studyai.dto.CitationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source tagging for prompts, and checking the answer that comes back.
 *
 * <p>Retrieved passages are given to the model as {@code <source id="S1">}
 * blocks. Anything resembling those tags inside a passage is escaped first, so
 * text in an uploaded file cannot close its block and pose as instructions.
 *
 * <p>After the answer is written its {@code [S#]} markers are mapped back to
 * passages (markers naming no passage are removed), and each sentence is
 * checked for support: enough of its content terms must appear in the
 * passages. An answer with an unsupported sentence, or with no citation at
 * all, is flagged "unverified" for the student rather than presented as fact.
 */
@Component
@RequiredArgsConstructor
class Citations {

    private static final Pattern MARKER = Pattern.compile("\\[(S\\d+(?:\\s*[,;]\\s*S?\\d+)*)]");
    private static final Pattern ID = Pattern.compile("S?(\\d+)");
    static final double SUPPORT_THRESHOLD = 0.5;

    private final TextAnalyzer analyzer;

    String sourceBlocks(List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sb.append("<source id=\"S").append(i + 1).append("\" title=\"").append(escapeAttr(c.materialTitle()))
              .append("\" location=\"").append(c.locationLabel()).append("\">\n");
            if (c.heading() != null) sb.append(escapeBody(c.heading())).append("\n");
            sb.append(escapeBody(c.content())).append("\n</source>\n\n");
        }
        return sb.toString();
    }

    static String escapeBody(String text) {
        return text.replaceAll("(?i)</?\\s*source", "[source").replace("<|", "< |");
    }

    private static String escapeAttr(String text) {
        return text == null ? "" : text.replace("\"", "'").replace("<", "(").replace(">", ")");
    }

    record Checked(String text, List<CitationResponse> citations, boolean unverified) {}

    /** Maps markers to passages, drops unknown ones, and checks every sentence's support. */
    Checked check(String answer, List<RetrievedChunk> chunks) {
        answer = normaliseMarkers(answer);
        Set<Integer> used = new LinkedHashSet<>();
        Matcher m = MARKER.matcher(answer);
        StringBuilder cleaned = new StringBuilder();
        while (m.find()) {
            List<String> kept = new ArrayList<>();
            Matcher ids = ID.matcher(m.group(1));
            while (ids.find()) {
                int n = Integer.parseInt(ids.group(1));
                if (n >= 1 && n <= chunks.size()) {
                    used.add(n);
                    kept.add("S" + n);
                }
            }
            m.appendReplacement(cleaned, kept.isEmpty() ? "" : Matcher.quoteReplacement("[" + String.join(", ", kept) + "]"));
        }
        m.appendTail(cleaned);
        String text = cleaned.toString().replaceAll(" +([.,;:])", "$1").strip();

        List<CitationResponse> citations = new ArrayList<>();
        for (int n : used) citations.add(citation(n, chunks.get(n - 1)));
        return new Checked(text, citations, used.isEmpty() || !supported(text, chunks));
    }

    /** The citation for passage {@code n} (1-based); {@code n <= 0} gives one with no marker. */
    /** The text without source markers — for text shown where the passages are not listed. */
    static String stripMarkers(String text) {
        return MARKER.matcher(normaliseMarkers(text)).replaceAll("").replaceAll(" +([.,;:])", "$1").strip();
    }

    /** "(S1)", "[Source 2]", "[S1][S2]" → "[S1]", "[S2]", "[S1, S2]": one form for the checker and the UI. */
    static String normaliseMarkers(String text) {
        return text
                .replaceAll("\\[\\s*[Ss]ource\\s*(\\d+)\\s*]", "[S$1]")
                .replaceAll("\\(\\s*(S\\d+(?:\\s*[,;]\\s*S?\\d+)*)\\s*\\)", "[$1]")
                .replaceAll("\\[\\s*(S\\d+)\\s*]\\s*\\[\\s*(S\\d+)\\s*]", "[$1, $2]");
    }

    static CitationResponse citation(int n, RetrievedChunk c) {
        return new CitationResponse(n > 0 ? "S" + n : null, c.id(), c.materialId(), c.materialTitle(), c.locationType(),
                c.locationStart(), c.locationEnd(), c.locationLabel(), c.heading(),
                CitationResponse.excerptOf(c.content()));
    }

    /** Every substantial sentence shares at least half its content terms with the passages. */
    boolean supported(String answer, List<RetrievedChunk> chunks) {
        Set<String> sourceTerms = new HashSet<>();
        for (RetrievedChunk c : chunks) {
            sourceTerms.addAll(analyzer.terms(c.content()));
            if (c.heading() != null) sourceTerms.addAll(analyzer.terms(c.heading()));
        }
        // Code and formulas are checked as written, not as prose: their tokens ("frac", "def") are not terms.
        String prose = MARKER.matcher(answer).replaceAll("")
                .replaceAll("(?s)```.*?(```|$)", " ")
                .replaceAll("(?s)\\$\\$.*?\\$\\$", " ")
                .replaceAll("\\$[^$\n]+\\$", " ")
                .replaceAll("`[^`\n]+`", " ")
                .replaceAll("(?m)^\\s*#{1,6}\\s+", "");
        for (String sentence : analyzer.sentences(prose)) {
            Set<String> terms = analyzer.termSet(sentence);
            if (terms.size() < 4) continue;      // "Here is a summary:" and the like
            long hit = terms.stream().filter(sourceTerms::contains).count();
            if ((double) hit / terms.size() < SUPPORT_THRESHOLD) return false;
        }
        return true;
    }
}
