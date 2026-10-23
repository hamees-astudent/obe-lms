package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.modules.studyai.GenQuestion.Option;
import com.lms.modules.studyai.GenQuestion.RubricItem;
import com.lms.modules.studyai.RetrievalService.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes questions from the material by rule — the generator's classical path,
 * used when the local model is unavailable or produced too few valid
 * questions.
 *
 * <ul>
 *   <li><b>MCQ</b>: a definition ("A deadlock is a state in which…") becomes
 *       "Which of the following best describes a deadlock?", with other
 *       definitions from the course as distractors; otherwise a key sentence
 *       becomes a fill-in-the-blank on one of its key phrases, the distractors
 *       being key phrases of similar length from elsewhere in the course.</li>
 *   <li><b>MSQ</b>: "Which of the following are discussed under <i>heading</i>?"
 *       — the section's own key phrases against phrases from other
 *       sections.</li>
 *   <li><b>Short/long answer</b>: the instruction verb follows the requested
 *       Bloom level (Define, Explain, Differentiate, Evaluate, Propose…); the
 *       model answer is the passage's most informative sentences and the rubric
 *       rewards the key phrases they contain.</li>
 * </ul>
 *
 * <p>Every question is built from sentences of the material itself, so its
 * answer is grounded by construction.
 */
@Component
@RequiredArgsConstructor
class TemplateQuestionGenerator {

    /**
     * "X is a/an/the …", "X refers to …", "X is defined as …" — a definition,
     * not just any sentence with "is" ("Creating a thread is cheaper…").
     */
    private static final Pattern DEFINITION = Pattern.compile(
            "^(?:(?:An?|The)\\s+)?([A-Za-z][\\w\\-()/' ]{1,60}?)\\s+"
            + "(?:(?:is|are)\\s+(?=(?:a|an|the|one|any|known as|called|defined as)\\s)|refers to\\s+|means\\s+"
            + "|is defined as\\s+|can be defined as\\s+)(.{12,})$");

    /** Single words too generic to ask a question about on their own. */
    private static final Set<String> GENERIC_SUBJECTS = Set.of("state", "states", "condition", "conditions",
            "system", "systems", "number", "time", "value", "values", "data", "result", "results", "way",
            "process", "processes", "thread", "threads", "level", "order", "information", "problem", "method");
    private static final Set<String> WEAK_OPENERS = Set.of("it", "this", "these", "they", "that", "those",
            "such", "he", "she", "we", "you", "here", "there", "also", "so", "and", "but", "or", "then");

    private final TextAnalyzer analyzer;

    record Sentence(RetrievedChunk chunk, String text, int position) {}

    record Definition(String term, String definition, Sentence sentence) {}

    /** Shared state for one generation run, so questions do not reuse sentences. */
    final class Run {
        final List<RetrievedChunk> chunks;
        final List<String> pool;
        final Random random;
        final Set<String> usedSentences = new HashSet<>();
        final Set<String> usedSubjects = new HashSet<>();
        final List<Sentence> sentences = new ArrayList<>();
        final List<Definition> definitions = new ArrayList<>();
        int cursor;

        Run(List<RetrievedChunk> chunks, List<String> phrasePool, long seed) {
            this.chunks = chunks;
            this.pool = phrasePool;
            this.random = new Random(seed);
            for (RetrievedChunk c : chunks) {
                List<String> ss = analyzer.sentences(c.content());
                for (int i = 0; i < ss.size(); i++) {
                    String s = ss.get(i).strip();
                    int words = Chunker.wordCount(s);
                    if (words < 7 || words > 45 || s.endsWith("?") || s.contains("|")) continue;
                    String first = s.split("\\s+")[0].toLowerCase(Locale.ROOT).replaceAll("\\W", "");
                    if (WEAK_OPENERS.contains(first)) continue;
                    Sentence sentence = new Sentence(c, s, i);
                    sentences.add(sentence);
                    Matcher m = DEFINITION.matcher(s.replaceAll("^[•\\-*]\\s*", ""));
                    if (m.matches() && Chunker.wordCount(m.group(1)) <= 5) {
                        String def = trimDefinition(m.group(2));
                        if (Chunker.wordCount(def) >= 4) definitions.add(new Definition(m.group(1).strip(), def, sentence));
                    }
                }
            }
            Collections.shuffle(sentences, random);
            Collections.shuffle(definitions, random);
        }
    }

    Run start(List<RetrievedChunk> chunks, List<String> phrasePool, long seed) {
        return new Run(chunks, phrasePool, seed);
    }

    /** Up to {@code count} questions of {@code type}; fewer if the material runs out. */
    List<GenQuestion> generate(Run run, String type, int count, String difficulty, List<String> bloomLevels) {
        List<GenQuestion> out = new ArrayList<>();
        List<String> levels = bloomLevels == null || bloomLevels.isEmpty()
                ? List.of(MarksPolicy.defaultLevel(type)) : bloomLevels;
        int attempts = 0;
        while (out.size() < count && attempts++ < count * 6) {
            String level = levels.get((out.size() + attempts) % levels.size());
            Optional<GenQuestion> q = switch (type) {
                case "MCQ" -> mcq(run, difficulty);
                case "MSQ" -> msq(run);
                case "SHORT_ANSWER" -> shortAnswer(run, level);
                case "LONG_ANSWER" -> longAnswer(run, level);
                default -> Optional.empty();
            };
            q.ifPresent(g -> {
                g.setDifficulty(difficulty);
                g.setSource("TEMPLATE");
                out.add(g);
            });
        }
        return out;
    }

    // ── Choice questions ─────────────────────────────────────────────────────

    private Optional<GenQuestion> mcq(Run run, String difficulty) {
        // Definitions first: "best describes" questions read better than blanks.
        for (Definition d : run.definitions) {
            if (run.usedSentences.contains(d.sentence().text()) || run.usedSubjects.contains(key(d.term()))) continue;
            List<String> distractors = run.definitions.stream()
                    .filter(o -> !key(o.term()).equals(key(d.term())))
                    .map(Definition::definition)
                    .filter(o -> sharedStems(o, d.definition()) < 0.5)
                    .distinct().limit(3).toList();
            if (distractors.size() < 3) break;
            run.usedSentences.add(d.sentence().text());
            run.usedSubjects.add(key(d.term()));
            return Optional.of(choice("MCQ", "Which of the following best describes " + article(d.term()) + "?",
                    d.definition(), distractors, run.random, d.sentence()));
        }
        for (int i = 0; i < run.sentences.size(); i++) {
            Sentence s = run.sentences.get((run.cursor + i) % run.sentences.size());
            if (run.usedSentences.contains(s.text())) continue;
            Optional<String> blank = blankPhrase(s);
            if (blank.isEmpty() || run.usedSubjects.contains(key(blank.get()))) continue;
            List<String> distractors = distractors(run, blank.get(), s, 3, "HARD".equals(difficulty));
            if (distractors.size() < 3) continue;
            run.cursor = (run.cursor + i + 1) % run.sentences.size();
            run.usedSentences.add(s.text());
            run.usedSubjects.add(key(blank.get()));
            String stem = s.text().replaceFirst("(?i)" + Pattern.quote(blank.get()), "________");
            return Optional.of(choice("MCQ", "Fill in the blank: " + stem.replaceAll("^[•\\-*]\\s*", ""),
                    blank.get(), distractors, run.random, s));
        }
        return Optional.empty();
    }

    private Optional<GenQuestion> msq(Run run) {
        for (RetrievedChunk c : shuffled(run.chunks, run.random)) {
            String subject = c.heading() != null && c.heading().length() <= 80 ? c.heading()
                    : c.keyPhrases().isEmpty() ? null : c.keyPhrases().get(0);
            if (subject == null || run.usedSubjects.contains(key("msq:" + subject))) continue;
            String content = c.content().toLowerCase(Locale.ROOT);
            List<String> correct = c.keyPhrases().stream()
                    .filter(p -> !key(p).equals(key(subject)) && content.contains(p.toLowerCase(Locale.ROOT)))
                    .limit(3).toList();
            if (correct.size() < 2) continue;
            List<String> wrong = new ArrayList<>();
            for (RetrievedChunk other : shuffled(run.chunks, run.random)) {
                if (other.id().equals(c.id()) || Objects.equals(other.heading(), c.heading())) continue;
                for (String p : other.keyPhrases()) {
                    if (wrong.size() >= 2) break;
                    if (!content.contains(p.toLowerCase(Locale.ROOT)) && !wrong.contains(p)
                            && correct.stream().noneMatch(x -> sharedStems(x, p) > 0)) wrong.add(p);
                }
                if (wrong.size() >= 2) break;
            }
            for (String p : run.pool) {
                if (wrong.size() >= 2) break;
                if (!content.contains(p.toLowerCase(Locale.ROOT)) && !wrong.contains(p)) wrong.add(p);
            }
            if (wrong.size() < 2) continue;
            run.usedSubjects.add(key("msq:" + subject));
            List<Option> options = new ArrayList<>();
            List<String> all = new ArrayList<>(correct);
            all.addAll(wrong);
            Collections.shuffle(all, run.random);
            List<String> correctIds = new ArrayList<>();
            for (String t : all) {
                String id = String.valueOf((char) ('a' + options.size()));
                options.add(new Option(id, capitalise(t)));
                if (correct.contains(t)) correctIds.add(id);
            }
            return Optional.of(GenQuestion.builder().type("MSQ")
                    .questionText("Which of the following are discussed under \"" + subject
                            + "\" in the course material? (Select all that apply)")
                    .options(options).correctAnswer(correctIds)
                    .explanation("Covered in " + c.materialTitle() + ", " + c.locationLabel() + ".")
                    .sourceChunkIds(new ArrayList<>(List.of(c.id())))
                    .build());
        }
        return Optional.empty();
    }

    private GenQuestion choice(String type, String text, String correct, List<String> distractors,
                               Random random, Sentence source) {
        List<String> all = new ArrayList<>(distractors);
        all.add(correct);
        Collections.shuffle(all, random);
        List<Option> options = new ArrayList<>();
        String correctId = null;
        for (String t : all) {
            String id = String.valueOf((char) ('a' + options.size()));
            options.add(new Option(id, capitalise(t)));
            if (t.equals(correct)) correctId = id;
        }
        return GenQuestion.builder().type(type).questionText(text).options(options)
                .correctAnswer(new ArrayList<>(List.of(correctId)))
                .explanation("From " + source.chunk().materialTitle() + ", " + source.chunk().locationLabel()
                        + ": \"" + source.text() + "\"")
                .sourceChunkIds(new ArrayList<>(List.of(source.chunk().id())))
                .build();
    }

    /** The key phrase of a sentence worth blanking out: multi-word and specific first. */
    private Optional<String> blankPhrase(Sentence s) {
        String lower = s.text().toLowerCase(Locale.ROOT);
        List<String> inSentence = new ArrayList<>();
        for (String p : s.chunk().keyPhrases()) if (lower.contains(p.toLowerCase(Locale.ROOT))) inSentence.add(p);
        if (inSentence.isEmpty()) inSentence.addAll(analyzer.candidatePhrases(s.text()));
        return inSentence.stream()
                .filter(p -> p.length() >= 4 && Chunker.wordCount(p) <= 4)
                // Never blank the sentence's first word: the stem would start with a gap.
                .filter(p -> !lower.startsWith(p.toLowerCase(Locale.ROOT)) || Chunker.wordCount(p) > 1)
                .max(Comparator.comparingInt((String p) -> Math.min(Chunker.wordCount(p), 3))
                        .thenComparingInt(String::length));
    }

    /** Key phrases that could plausibly fill the blank but do not. */
    private List<String> distractors(Run run, String correct, Sentence s, int n, boolean sameMaterialFirst) {
        int words = Chunker.wordCount(correct);
        String sentence = s.text().toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        List<String> near = new ArrayList<>();
        for (RetrievedChunk c : run.chunks) {
            if (c.materialId().equals(s.chunk().materialId())) near.addAll(c.keyPhrases());
        }
        Collections.shuffle(near, run.random);
        List<String> far = new ArrayList<>(run.pool);
        Collections.shuffle(far, run.random);
        if (sameMaterialFirst) { candidates.addAll(near); candidates.addAll(far); }
        else { candidates.addAll(far); candidates.addAll(near); }

        List<String> out = new ArrayList<>();
        for (String p : candidates) {
            if (out.size() >= n) break;
            if (Math.abs(Chunker.wordCount(p) - words) > 1) continue;
            if (sentence.contains(p.toLowerCase(Locale.ROOT))) continue;
            if (sharedStems(p, correct) > 0) continue;
            if (out.stream().anyMatch(o -> sharedStems(o, p) > 0)) continue;
            out.add(p);
        }
        return out;
    }

    // ── Written answers ──────────────────────────────────────────────────────

    private Optional<GenQuestion> shortAnswer(Run run, String level) {
        if (level.equals("REMEMBER")) {
            for (Definition d : run.definitions) {
                if (run.usedSubjects.contains(key(d.term()))) continue;
                run.usedSubjects.add(key(d.term()));
                run.usedSentences.add(d.sentence().text());
                String model = d.sentence().text() + follow(d.sentence());
                return Optional.of(written("SHORT_ANSWER", "Define " + article(d.term()) + ".", model,
                        d.sentence().chunk(), 2));
            }
        }
        if (level.equals("ANALYZE")) {
            for (RetrievedChunk c : shuffled(run.chunks, run.random)) {
                List<String> phrases = subjects(c);
                if (phrases.size() < 2) continue;
                String a = phrases.get(0), b = phrases.get(1);
                if (run.usedSubjects.contains(key(a + "|" + b)) || sharedStems(a, b) > 0) continue;
                String model = sentencesAbout(c, List.of(a, b), 4);
                if (Chunker.wordCount(model) < 10) continue;
                run.usedSubjects.add(key(a + "|" + b));
                return Optional.of(written("SHORT_ANSWER", "Differentiate between " + a + " and " + b + ".",
                        model, c, 2));
            }
        }
        for (RetrievedChunk c : shuffled(run.chunks, run.random)) {
            for (String p : subjects(c)) {
                if (run.usedSubjects.contains(key(p))) continue;
                String model = sentencesAbout(c, List.of(p), 2);
                if (Chunker.wordCount(model) < 10) continue;
                run.usedSubjects.add(key(p));
                String text = switch (level) {
                    case "REMEMBER" -> "State what is meant by " + p + ".";
                    case "APPLY" -> "Illustrate " + p + " with an example.";
                    case "EVALUATE" -> "Assess the importance of " + p + ".";
                    case "CREATE" -> "Propose how " + p + " could be used to solve a problem.";
                    default -> "Briefly explain " + p + ".";
                };
                return Optional.of(written("SHORT_ANSWER", text, model, c, 2));
            }
        }
        return Optional.empty();
    }

    private Optional<GenQuestion> longAnswer(Run run, String level) {
        for (RetrievedChunk c : shuffled(run.chunks, run.random)) {
            String subject = c.heading() != null && c.heading().length() <= 80 ? c.heading()
                    : c.keyPhrases().isEmpty() ? null : c.keyPhrases().get(0);
            if (subject == null || run.usedSubjects.contains(key("long:" + subject))) continue;
            List<String> points = subjects(c).stream()
                    .filter(p -> sharedStems(p, subject) == 0).limit(3).toList();
            if (points.size() < 2) continue;
            String model = keySentences(c, 7);
            if (Chunker.wordCount(model) < 40) continue;
            run.usedSubjects.add(key("long:" + subject));
            String cover = String.join(", ", points.subList(0, points.size() - 1)) + " and " + points.get(points.size() - 1);
            String text = switch (level) {
                case "APPLY" -> "Explain how " + subject + " is applied in practice. Use " + cover + " in your answer.";
                case "ANALYZE" -> "Analyze " + subject + ", examining the role of " + cover + ".";
                case "EVALUATE" -> "Critically evaluate " + subject + ". Your answer should weigh " + cover + ".";
                case "CREATE" -> "Propose a design that applies " + subject + ", making use of " + cover + ".";
                case "REMEMBER" -> "Describe " + subject + ", listing " + cover + ".";
                default -> "Discuss " + subject + " in detail. Your answer should cover " + cover + ".";
            };
            GenQuestion q = written("LONG_ANSWER", text, model, c, 0);
            List<RubricItem> rubric = new ArrayList<>();
            rubric.add(new RubricItem("Introduces " + subject + " accurately", BigDecimal.ONE,
                    new ArrayList<>(analyzer.termSet(subject).isEmpty() ? List.of() : List.of(subject))));
            for (String p : points) rubric.add(new RubricItem("Explains " + p, BigDecimal.ONE, List.of(p)));
            rubric.add(new RubricItem("Clear structure, examples and conclusion", BigDecimal.ONE, List.of()));
            q.setRubric(rubric);
            return Optional.of(q);
        }
        return Optional.empty();
    }

    private GenQuestion written(String type, String text, String model, RetrievedChunk chunk, int criteria) {
        GenQuestion q = GenQuestion.builder().type(type).questionText(text).modelAnswer(model)
                .explanation("Based on " + chunk.materialTitle() + ", " + chunk.locationLabel() + ".")
                .sourceChunkIds(new ArrayList<>(List.of(chunk.id())))
                .build();
        if (criteria > 0) q.setRubric(rubricFrom(model, chunk, criteria));
        return q;
    }

    /** One criterion per sentence group of the model answer, rewarding its key phrases. */
    private List<RubricItem> rubricFrom(String model, RetrievedChunk chunk, int criteria) {
        List<String> sentences = analyzer.sentences(model);
        List<RubricItem> out = new ArrayList<>();
        int per = Math.max(1, (int) Math.ceil(sentences.size() / (double) criteria));
        for (int i = 0; i < sentences.size() && out.size() < criteria; i += per) {
            String part = String.join(" ", sentences.subList(i, Math.min(sentences.size(), i + per)));
            List<String> kws = analyzer.keyPhrases(part, 3).stream().map(TextAnalyzer.KeyPhrase::text).toList();
            String label = kws.isEmpty() ? "Covers the main point accurately" : "Explains " + kws.get(0);
            out.add(new RubricItem(label, BigDecimal.ONE, kws));
        }
        if (out.isEmpty()) out.add(new RubricItem("Covers the main point accurately", BigDecimal.ONE, List.of()));
        return out;
    }

    private String sentencesAbout(RetrievedChunk c, List<String> phrases, int max) {
        List<String> out = new ArrayList<>();
        for (String s : analyzer.sentences(c.content())) {
            String lower = s.toLowerCase(Locale.ROOT);
            if (phrases.stream().anyMatch(p -> lower.contains(p.toLowerCase(Locale.ROOT)))) out.add(s.strip());
            if (out.size() >= max) break;
        }
        return String.join(" ", out);
    }

    /** The passage's most informative sentences (by key-phrase density), in their original order. */
    private String keySentences(RetrievedChunk c, int max) {
        List<String> sentences = analyzer.sentences(c.content());
        record Scored(int i, String s, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i);
            String lower = s.toLowerCase(Locale.ROOT);
            long hits = c.keyPhrases().stream().filter(p -> lower.contains(p.toLowerCase(Locale.ROOT))).count();
            int words = Chunker.wordCount(s);
            if (words < 5) continue;
            scored.add(new Scored(i, s.strip(), hits + Math.min(words, 30) / 30.0));
        }
        return scored.stream().sorted(Comparator.comparingDouble(Scored::score).reversed()).limit(max)
                .sorted(Comparator.comparingInt(Scored::i)).map(Scored::s)
                .reduce((a, b) -> a + " " + b).orElse("");
    }

    private String follow(Sentence s) {
        List<String> all = analyzer.sentences(s.chunk().content());
        int next = s.position() + 1;
        if (next < all.size() && Chunker.wordCount(all.get(next)) <= 35) return " " + all.get(next).strip();
        return "";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * A passage's key phrases worth asking about: multi-word terms first
     * ("circular wait", "page table"), then specific single words; never a
     * generic word such as "state" alone.
     */
    private List<String> subjects(RetrievedChunk c) {
        return c.keyPhrases().stream()
                .filter(p -> Chunker.wordCount(p) <= 4 && p.length() >= 4)
                .filter(p -> Chunker.wordCount(p) > 1 || (!GENERIC_SUBJECTS.contains(p.toLowerCase(Locale.ROOT)) && p.length() >= 6))
                .filter(p -> Character.isLetter(p.charAt(0)))
                .sorted(Comparator.comparingInt((String p) -> Chunker.wordCount(p) > 1 ? 0 : 1))
                .toList();
    }

    private double sharedStems(String a, String b) {
        Set<String> sa = analyzer.termSet(a);
        Set<String> sb = analyzer.termSet(b);
        if (sa.isEmpty() || sb.isEmpty()) return 0;
        long shared = sa.stream().filter(sb::contains).count();
        return (double) shared / Math.min(sa.size(), sb.size());
    }

    private static String trimDefinition(String d) {
        String t = d.strip().replaceAll("[.;:]+$", "");
        String[] words = t.split("\\s+");
        if (words.length <= 22) return t;
        // Cut at a clause boundary inside the first 22 words where possible.
        String head = String.join(" ", Arrays.copyOfRange(words, 0, 22));
        int comma = head.lastIndexOf(',');
        return comma > 30 ? head.substring(0, comma) : head;
    }

    private static String article(String term) {
        String t = term.strip();
        if (t.isEmpty()) return t;
        boolean plural = t.endsWith("s") && !t.endsWith("ss");
        boolean proper = Character.isUpperCase(t.charAt(0)) && !t.equals(t.toUpperCase(Locale.ROOT));
        boolean acronym = t.equals(t.toUpperCase(Locale.ROOT));
        if (plural || proper || acronym) return t;
        return ("aeiou".indexOf(Character.toLowerCase(t.charAt(0))) >= 0 ? "an " : "a ") + t;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String key(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}|:]", "");
    }

    private static <T> List<T> shuffled(List<T> list, Random random) {
        List<T> copy = new ArrayList<>(list);
        Collections.shuffle(copy, random);
        return copy;
    }
}
