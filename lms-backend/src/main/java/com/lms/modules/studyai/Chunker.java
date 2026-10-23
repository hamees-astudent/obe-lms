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

    /** One long page or section, cut between sentences with a small overlap. */
    private void splitLong(Segment seg, List<Chunk> out) {
        List<String> sentences = analyzer.sentences(seg.text());
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
        return last.start() == seg.number() && last.content().endsWith(String.join(" ", rest));
    }

    private void emit(Segment seg, List<String> sentences, List<Chunk> out) {
        String content = String.join(" ", sentences).strip();
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
            text.append(seg.text().replace('\n', ' ')).append("\n\n");
        }
    }
}
