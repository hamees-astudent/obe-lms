package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.modules.studyai.DocumentTextExtractor.Segment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a document's pages/slides/sections into passages of about
 * {@link #TARGET_WORDS} words — small enough that a retrieved passage is about
 * one thing, large enough to carry the context a question needs.
 *
 * <ul>
 *   <li>A passage never spans two pages or slides, so its citation is exact —
 *       except that consecutive short slides (a title slide, a one-line
 *       bullet) are merged into one passage citing the range "Slides 4–6".</li>
 *   <li>Splits fall between sentences (OpenNLP), never inside one.</li>
 *   <li>Code keeps its line breaks and indentation: a run of lines that look
 *       like code is kept as one unit, never sentence-split or flattened, so
 *       the assistant can quote it and the student can read it.</li>
 *   <li>Consecutive passages of one long page share about
 *       {@link #OVERLAP_WORDS} words, so a statement that straddles the split
 *       is whole in at least one of them.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class Chunker {

    static final int TARGET_WORDS = 350;
    static final int MAX_WORDS = 500;
    static final int OVERLAP_WORDS = 50;
    static final int SHORT_SEGMENT_WORDS = 40;

    private final TextAnalyzer analyzer;

    record Chunk(int index, String locationType, int start, int end, String heading, String content,
                 List<String> keyPhrases, int tokenEstimate) {}

    List<Chunk> chunk(List<Segment> segments) {
        List<Chunk> out = new ArrayList<>();
        Pending pending = null;
        for (Segment seg : segments) {
            int words = wordCount(seg.text());
            if (words <= SHORT_SEGMENT_WORDS) {
                // Merge short slides/pages into one passage until it is big enough.
                if (pending != null && (pending.words + words > TARGET_WORDS
                        || !pending.type.equals(seg.locationType()))) {
                    flush(pending, out);
                    pending = null;
                }
                if (pending == null) pending = new Pending(seg.locationType(), seg.number(), seg.heading());
                pending.add(seg, words);
                continue;
            }
            if (pending != null) {
                flush(pending, out);
                pending = null;
            }
            splitLong(seg, out);
        }
        if (pending != null) flush(pending, out);
        return out;
    }

    private static final java.util.regex.Pattern CODE_START = java.util.regex.Pattern.compile(
            "^(?:def|class|public|private|protected|static|final|int|long|double|float|char|bool|boolean|void|String"
            + "|for|while|if|else|elif|switch|case|try|catch|return|import|from|package|#include|#define|using"
            + "|namespace|function|const|let|var|print|printf|cout|cin|System\\.out|SELECT|INSERT|UPDATE|DELETE"
            + "|CREATE|ALTER|DROP|FROM|WHERE|GROUP|ORDER|JOIN|BEGIN|END|lambda|async|await|struct|template|typedef)\\b.*");

    /** A line of source code rather than prose. */
    static boolean codeLine(String line) {
        String t = line.strip();
        if (t.isEmpty() || t.length() > 160) return false;
        if (t.endsWith(";") || t.endsWith("{") || t.equals("}") || t.equals("};") || t.startsWith("//")) return true;
        if (CODE_START.matcher(t).matches()
                && (t.contains("(") || t.contains("=") || t.endsWith(":") || t.contains("{") || t.contains("<<"))) {
            return true;
        }
        boolean indented = line.startsWith("    ") || line.startsWith("\t");
        boolean assignment = t.matches("^[A-Za-z_][\\w.\\[\\]]*\\s*(?:[+\\-*/]?=)\\s*\\S.*") && !t.contains(". ");
        boolean symbols = t.matches(".*[=(){}\\[\\];<>#].*");
        return assignment || (indented && symbols && !t.matches(".*[.!?]$"));
    }

    /**
     * The statements of a text: prose split into sentences, and each run of two
     * or more code lines kept whole, line breaks and indentation included.
     */
    List<String> units(String text) {
        List<String> out = new ArrayList<>();
        List<String> prose = new ArrayList<>();
        List<String> code = new ArrayList<>();
        for (String line : (text + "\n").split("\n", -1)) {
            if (codeLine(line) || (!code.isEmpty() && !line.isBlank() && line.startsWith(" "))) {
                code.add(line.stripTrailing());
                continue;
            }
            if (code.size() >= 2) {
                if (!prose.isEmpty()) out.addAll(analyzer.sentences(String.join("\n", prose)));
                prose.clear();
                out.add(String.join("\n", code));
            } else {
                prose.addAll(code);
            }
            code.clear();
            prose.add(line);
        }
        if (!prose.isEmpty()) out.addAll(analyzer.sentences(String.join("\n", prose)));
        return out;
    }

    /** Sentences joined by spaces; a code block gets lines of its own. */
    static String join(List<String> units) {
        StringBuilder sb = new StringBuilder();
        for (String u : units) {
            boolean block = u.indexOf('\n') >= 0;
            if (sb.length() > 0) sb.append(block || sb.charAt(sb.length() - 1) == '\n' ? "\n" : " ");
            sb.append(u);
            if (block) sb.append('\n');
        }
        return sb.toString().strip();
    }

    /** One long page or section, cut between sentences with a small overlap. */
    private void splitLong(Segment seg, List<Chunk> out) {
        List<String> sentences = units(seg.text());
        List<String> current = new ArrayList<>();
        int currentWords = 0;
        for (String sentence : sentences) {
            int w = wordCount(sentence);
            // A single run-on "sentence" (a table, a code listing) is hard-cut.
            if (w > MAX_WORDS) {
                if (!current.isEmpty()) {
                    emit(seg, current, out);
                    current = new ArrayList<>();
                    currentWords = 0;
                }
                String[] words = sentence.split("\\s+");
                for (int i = 0; i < words.length; i += TARGET_WORDS) {
                    emit(seg, List.of(String.join(" ", java.util.Arrays.copyOfRange(words, i,
                            Math.min(words.length, i + TARGET_WORDS)))), out);
                }
                continue;
            }
            if (currentWords + w > TARGET_WORDS && currentWords > 0) {
                emit(seg, current, out);
                current = overlapTail(current);
                currentWords = current.stream().mapToInt(Chunker::wordCount).sum();
            }
            current.add(sentence);
            currentWords += w;
        }
        if (!current.isEmpty() && (out.isEmpty() || !sameAsOverlapOnly(current, seg, out))) {
            emit(seg, current, out);
        }
    }

    /** The trailing sentences of a passage, about {@link #OVERLAP_WORDS} words, to start the next. */
    private static List<String> overlapTail(List<String> sentences) {
        List<String> tail = new ArrayList<>();
        int words = 0;
        for (int i = sentences.size() - 1; i >= 0 && words < OVERLAP_WORDS; i--) {
            int w = wordCount(sentences.get(i));
            if (words + w > OVERLAP_WORDS * 2) break;
            tail.add(0, sentences.get(i));
            words += w;
        }
        return new ArrayList<>(tail);
    }

    /** Whether what is left is nothing but the overlap already in the last passage. */
    private static boolean sameAsOverlapOnly(List<String> rest, Segment seg, List<Chunk> out) {
        Chunk last = out.get(out.size() - 1);
        return last.start() == seg.number() && last.content().endsWith(join(rest));
    }

    private void emit(Segment seg, List<String> sentences, List<Chunk> out) {
        String content = join(sentences);
        if (content.isEmpty()) return;
        out.add(build(out.size(), seg.locationType(), seg.number(), seg.number(), seg.heading(), content));
    }

    private void flush(Pending p, List<Chunk> out) {
        String content = p.text.toString().strip();
        if (content.isEmpty()) return;
        out.add(build(out.size(), p.type, p.start, p.end, p.heading, content));
    }

    private Chunk build(int index, String type, int start, int end, String heading, String content) {
        String forPhrases = heading == null ? content : heading + ". " + content;
        List<String> phrases = analyzer.keyPhrases(forPhrases, 8).stream()
                .map(TextAnalyzer.KeyPhrase::text).toList();
        return new Chunk(index, type, start, end, heading, content, phrases, TextAnalyzer.estimateTokens(content));
    }

    static int wordCount(String text) {
        String t = text == null ? "" : text.strip();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    private static final class Pending {
        final String type;
        final int start;
        int end;
        String heading;
        int words;
        final StringBuilder text = new StringBuilder();

        Pending(String type, int start, String heading) {
            this.type = type;
            this.start = start;
            this.end = start;
            this.heading = heading;
        }

        void add(Segment seg, int w) {
            if (heading == null) heading = seg.heading();
            end = seg.number();
            words += w;
            // Keep each slide's title with its text so the merged passage reads in order.
            if (seg.heading() != null && !seg.text().startsWith(seg.heading())) {
                text.append(seg.heading()).append(": ");
            }
            // Prose lines are layout and are joined; code keeps its lines.
            boolean code = seg.text().lines().filter(Chunker::codeLine).count() >= 2;
            text.append(code ? seg.text() : seg.text().replace('\n', ' ')).append("\n\n");
        }
    }
}
