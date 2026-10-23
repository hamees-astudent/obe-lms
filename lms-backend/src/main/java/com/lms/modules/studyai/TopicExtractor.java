package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.modules.studyai.Chunker.Chunk;
import com.lms.modules.studyai.IngestionRepository.Topic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Works out which topics a material covers, and where.
 *
 * <p>Headings first: slide titles and section headings are what the teacher
 * called each part, and consecutive passages under one heading form one topic.
 * Generic headings ("Outline", "Summary", "Questions?") are skipped. When a
 * file has too few headings — a plain PDF of notes — the most distinctive key
 * phrases fill in: phrases frequent in this material but not spread evenly
 * across it (TF-IDF over its passages), each spanning the pages it appears on.
 */
@Component
@RequiredArgsConstructor
class TopicExtractor {

    static final int MIN_TOPICS = 5;
    static final int MAX_TOPICS = 25;

    private static final Set<String> GENERIC = Set.of("outline", "agenda", "contents", "table of contents",
            "summary", "conclusion", "conclusions", "questions", "questions?", "any questions", "thank you",
            "thanks", "references", "bibliography", "introduction", "overview", "objectives",
            "learning objectives", "learning outcomes", "recap", "review", "example", "examples", "exercise",
            "exercises", "quiz", "today", "announcements", "acknowledgements", "appendix", "contd", "cont",
            "continued", "practice", "the end");

    private final TextAnalyzer analyzer;

    List<Topic> extract(List<Chunk> chunks, String materialTitle) {
        List<Topic> topics = new ArrayList<>(fromHeadings(chunks));
        if (topics.size() < MIN_TOPICS) {
            Set<String> taken = new HashSet<>();
            topics.forEach(t -> taken.add(norm(t.name())));
            for (Topic t : fromKeyPhrases(chunks, MIN_TOPICS * 2)) {
                if (topics.size() >= Math.max(MIN_TOPICS, Math.min(chunks.size(), 10))) break;
                if (taken.add(norm(t.name()))) topics.add(t);
            }
        }
        if (topics.isEmpty() && !chunks.isEmpty()) {
            Chunk first = chunks.get(0);
            Chunk last = chunks.get(chunks.size() - 1);
            topics.add(new Topic(materialTitle, summary(first.content()), first.start(), last.end()));
        }
        if (topics.size() > MAX_TOPICS) {
            // Keep the topics that cover the most ground, in document order.
            List<Topic> widest = topics.stream()
                    .sorted(Comparator.comparingInt((Topic t) -> t.end() - t.start()).reversed())
                    .limit(MAX_TOPICS).toList();
            topics = topics.stream().filter(widest::contains).toList();
        }
        return topics.stream().sorted(Comparator.comparingInt(Topic::start)).toList();
    }

    private List<Topic> fromHeadings(List<Chunk> chunks) {
        Map<String, int[]> ranges = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        Map<String, String> firstText = new HashMap<>();
        for (Chunk c : chunks) {
            String h = cleanHeading(c.heading());
            if (h == null) continue;
            String key = norm(h);
            int[] r = ranges.computeIfAbsent(key, k -> new int[]{c.start(), c.end()});
            r[0] = Math.min(r[0], c.start());
            r[1] = Math.max(r[1], c.end());
            names.putIfAbsent(key, h);
            firstText.putIfAbsent(key, c.content());
        }
        List<Topic> out = new ArrayList<>();
        ranges.forEach((key, r) -> out.add(new Topic(names.get(key), summary(firstText.get(key)), r[0], r[1])));
        return out;
    }

    private List<Topic> fromKeyPhrases(List<Chunk> chunks, int limit) {
        // Document frequency of each phrase across the material's passages.
        Map<String, Integer> df = new HashMap<>();
        Map<String, Integer> tf = new HashMap<>();
        Map<String, String> display = new HashMap<>();
        Map<String, int[]> ranges = new HashMap<>();
        Map<String, String> context = new HashMap<>();
        for (Chunk c : chunks) {
            Set<String> seen = new HashSet<>();
            for (String p : c.keyPhrases()) {
                String key = norm(p);
                if (GENERIC.contains(key) || key.length() < 4) continue;
                tf.merge(key, 1, Integer::sum);
                if (seen.add(key)) df.merge(key, 1, Integer::sum);
                display.putIfAbsent(key, p);
                int[] r = ranges.computeIfAbsent(key, k -> new int[]{c.start(), c.end()});
                r[0] = Math.min(r[0], c.start());
                r[1] = Math.max(r[1], c.end());
                context.putIfAbsent(key, sentenceWith(c.content(), p));
            }
        }
        int n = Math.max(1, chunks.size());
        return tf.keySet().stream()
                .sorted(Comparator.comparingDouble((String k) ->
                        // Frequent, multi-word, and concentrated in part of the material.
                        -(tf.get(k) * (1 + 0.5 * (k.split(" ").length - 1))
                                * Math.log(1.0 + (double) n / df.get(k)))))
                .limit(limit)
                .map(k -> new Topic(capitalise(display.get(k)), context.get(k), ranges.get(k)[0], ranges.get(k)[1]))
                .toList();
    }

    private String sentenceWith(String content, String phrase) {
        String lower = phrase.toLowerCase(Locale.ROOT);
        for (String s : analyzer.sentences(content)) {
            if (s.toLowerCase(Locale.ROOT).contains(lower)) return summary(s);
        }
        return summary(content);
    }

    private String cleanHeading(String heading) {
        if (heading == null) return null;
        String h = heading.replaceAll("\\s+", " ")
                .replaceAll("(?i)\\s*\\((cont(d|inued)?\\.?)\\)\\s*$", "")
                .replaceAll("(?i)\\s+(cont(d|inued)?\\.?)$", "")
                .replaceAll("^[\\d.]+\\s+", "")          // "2.3 Normal forms" → "Normal forms"
                .strip();
        if (h.length() < 3 || h.length() > 120) return null;
        if (GENERIC.contains(norm(h))) return null;
        if (!h.matches(".*\\p{L}{3,}.*")) return null;
        return h;
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", "").replaceAll("\\s+", " ").strip();
    }

    private static String summary(String text) {
        if (text == null) return null;
        String t = text.replaceAll("\\s+", " ").strip();
        return t.length() <= 200 ? t : t.substring(0, 197).replaceAll("\\s+\\S*$", "") + "…";
    }

    private static String capitalise(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
