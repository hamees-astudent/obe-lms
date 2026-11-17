package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.infrastructure.nlp.TfIdf;
import com.lms.modules.studyai.GenQuestion.Option;
import com.lms.modules.studyai.RetrievalService.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects generated questions a teacher should never be shown, whoever wrote
 * them:
 *
 * <ul>
 *   <li>malformed choice questions, and written questions with no usable model
 *       answer;</li>
 *   <li>answers the source material does not support (the grounding check
 *       that keeps a small local model honest);</li>
 *   <li>questions that lean on something the student cannot see — "the figure
 *       above", "according to the passage", "S2", or a stem that opens with
 *       "It" or "This";</li>
 *   <li>questions about the book or the course rather than the subject (the
 *       preface, the publisher, office hours);</li>
 *   <li>choice questions whose answer can be spotted without knowing the
 *       subject: the only option shaped like a web address, a number or an
 *       acronym among words; an answer twice as long as every distractor; the
 *       only option repeating a word of the stem; "a/an ____" agreeing with the
 *       answer alone; "all of the above";</li>
 *   <li>near-duplicates of a question already accepted, in this paper or in
 *       the ones the caller passes in (earlier practice papers, the course's
 *       quizzes).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class QuestionValidator {

    static final double DUPLICATE_SIMILARITY = 0.72;
    /** Same answer and this similar a question: the same question in other words. */
    static final double SAME_ANSWER_SIMILARITY = 0.4;
    static final double DUPLICATE_OVERLAP = 0.75;
    static final double CHOICE_GROUNDING = 0.5;
    static final double WRITTEN_GROUNDING = 0.4;

    /** Question scaffolding that says nothing about what is asked (stemmed on first use). */
    private static final String SCAFFOLDING = "which following best describe describes described fill blank statement "
            + "statements true false correct incorrect option options select apply applies explain define discuss "
            + "briefly state list identify choose answer meaning means term refers refer given example illustrate "
            + "differentiate compare evaluate assess analyze analyse propose critically detail role importance would "
            + "most accurately";
    private volatile Set<String> stemWords;

    private static final Pattern WEAK_OPENER = Pattern.compile(
            "^(?:it|this|these|those|they|he|she|its|their|such)\\b", Pattern.CASE_INSENSITIVE);
    /** Not "publisher", "acknowledgement", "edition", "copyright": publish/subscribe, TCP and Java SE are topics. */
    private static final Pattern ABOUT_THE_BOOK = Pattern.compile(
            "\\b(?:preface|foreword|ISBN|published\\s+by|\\d+(?:st|nd|rd|th)\\s+edition"
            + "|edition\\s+of\\s+(?:this|the)\\s+(?:book|text)|authors?\\s+of\\s+(?:this|the)\\s+(?:book|text|course)"
            + "|this\\s+(?:book|textbook)|office\\s+hours|syllabus|grading\\s+(?:policy|scheme)"
            + "|course\\s+(?:code|instructor|outline)|page\\s+\\d+|chapter\\s+\\d+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ABOVE_OPTIONS = Pattern.compile(
            "(?i)^(?:all|none|both|neither)\\s+of\\s+the\\s+(?:above|these|options)|^(?:a|b)\\s+and\\s+(?:b|c)\\b");
    private static final Pattern BLANK = Pattern.compile("_{3,}|\\.{5,}");
    private static final Pattern ARTICLE_BEFORE_BLANK = Pattern.compile("(?i)\\b(a|an)\\s+(?:_{3,}|\\.{5,})");

    enum Shape { WEB, NUMBER, ACRONYM, CODE, TEXT }

    /** Lower-case domains only: "System.in", "java.io" and "System.Net" are code, not web addresses. */
    private static final Pattern WEB = Pattern.compile(
            "\\b(?:https?://|www\\.)|[\\w.+-]+@[\\w-]+\\.|\\b[a-z0-9-]+\\.(?:com|org|edu|gov|info|biz|pk|uk)\\b");
    private static final Pattern NUMBER = Pattern.compile("^[\\s$€£%+\\-−.,0-9/×x^()]*\\d[\\s$€£%+\\-−.,0-9/×x^()a-zA-Z]{0,6}$");
    private static final Pattern ACRONYM = Pattern.compile("^[A-Z0-9]{2,7}s?$");
    private static final Pattern CODE = Pattern.compile("[(){};=<>\\[\\]]|\\w+\\.\\w+\\(|`");

    private final TextAnalyzer analyzer;

    /** The question cleaned up (option ids assigned), or why it was rejected. */
    record Result(GenQuestion question, String rejection) {
        boolean ok() { return rejection == null; }
    }

    Result validate(GenQuestion q, List<RetrievedChunk> sources, List<GenQuestion> accepted) {
        String raw = q.getQuestionText() == null ? "" : q.getQuestionText().strip();
        // Checked before markers are stripped: "What does [S1] say…?" is meaningless without them, too.
        if (InstructionalFilter.needsAbsentContext(raw)) {
            return reject(q, "refers to a figure, passage or source the student cannot see");
        }
        String text = Citations.stripMarkers(raw);
        if (text.length() < 10 || text.length() > 1200) return reject(q, "question text length");
        if (InstructionalFilter.needsAbsentContext(text)) {
            return reject(q, "refers to a figure, passage or source the student cannot see");
        }
        if (WEAK_OPENER.matcher(text).find()) return reject(q, "opens with a pronoun that refers to nothing");
        if (ABOUT_THE_BOOK.matcher(text).find()) return reject(q, "about the book or course, not the subject");

        GenQuestion cleaned = q.toBuilder().questionText(text).build();
        Set<String> sourceTerms = new HashSet<>();
        for (RetrievedChunk c : sources) sourceTerms.addAll(analyzer.terms(c.content()));

        if (q.isWritten()) {
            String model = q.getModelAnswer() == null ? "" : Citations.stripMarkers(q.getModelAnswer().strip());
            if (Chunker.wordCount(model) < 5) return reject(q, "model answer too short");
            if (!sourceTerms.isEmpty() && grounding(model, sourceTerms) < WRITTEN_GROUNDING) {
                return reject(q, "model answer not supported by the material");
            }
            cleaned.setOptions(new ArrayList<>());
            cleaned.setCorrectAnswer(new ArrayList<>());
            cleaned.setModelAnswer(model);
        } else {
            List<Option> options = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Map<String, String> idMap = new HashMap<>();
            for (Option o : q.getOptions()) {
                String t = o.text() == null ? "" : Citations.stripMarkers(o.text().strip());
                t = t.replaceFirst("^[(\\[]?[a-fA-F1-6][).\\]]\\s+", "");   // "a) " / "1. " prefixes
                if (t.isEmpty() || t.length() > 300) return reject(q, "empty or overlong option");
                if (!seen.add(t.toLowerCase(Locale.ROOT).replaceAll("\\W+", ""))) return reject(q, "duplicate options");
                if (ABOVE_OPTIONS.matcher(t).find()) return reject(q, "\"all/none of the above\" option");
                if (InstructionalFilter.needsAbsentContext(t)) return reject(q, "option refers to something not shown");
                String id = String.valueOf((char) ('a' + options.size()));
                idMap.put(o.id(), id);
                options.add(new Option(id, t));
            }
            if (options.size() < 3 || options.size() > 6) return reject(q, "needs 3-6 options");
            List<String> correct = q.getCorrectAnswer().stream().map(idMap::get).filter(Objects::nonNull)
                    .distinct().toList();
            if ("MCQ".equals(q.getType()) && correct.size() != 1) return reject(q, "MCQ needs exactly one correct option");
            if ("MSQ".equals(q.getType()) && (correct.size() < 2 || correct.size() >= options.size())) {
                return reject(q, "MSQ needs at least two correct options and one wrong one");
            }
            String giveaway = giveaway(text, options, correct);
            if (giveaway != null) return reject(q, giveaway);
            String blank = blankProblem(text, options, correct);
            if (blank != null) return reject(q, blank);
            if (!sourceTerms.isEmpty()) {
                String correctText = String.join(" ", options.stream()
                        .filter(o -> correct.contains(o.id())).map(Option::text).toList());
                // Short answers like "O(log n)" carry few terms; judge them with the stem.
                if (analyzer.termSet(correctText).size() >= 2
                        && grounding(correctText, sourceTerms) < CHOICE_GROUNDING) {
                    return reject(q, "correct option not supported by the material");
                }
            }
            cleaned.setOptions(options);
            cleaned.setCorrectAnswer(new ArrayList<>(correct));
        }

        if (isDuplicate(cleaned, accepted)) return reject(q, "near-duplicate of another question");
        return new Result(cleaned, null);
    }

    // ── Giveaways ────────────────────────────────────────────────────────────

    /** Why the correct option can be picked without knowing the subject, or null. */
    String giveaway(String stem, List<Option> options, List<String> correct) {
        List<Option> right = options.stream().filter(o -> correct.contains(o.id())).toList();
        List<Option> wrong = options.stream().filter(o -> !correct.contains(o.id())).toList();
        if (right.isEmpty() || wrong.isEmpty()) return null;

        // Form: one web address among words, one number among phrases, ...
        Set<Shape> wrongShapes = EnumSet.noneOf(Shape.class);
        wrong.forEach(o -> wrongShapes.add(shape(o.text())));
        for (Option r : right) {
            Shape s = shape(r.text());
            if (wrongShapes.contains(s)) continue;
            boolean distinctive = s == Shape.WEB || s == Shape.NUMBER || s == Shape.ACRONYM;
            boolean wordAmongForms = s == Shape.TEXT && wrongShapes.size() == 1 && !wrongShapes.contains(Shape.CODE);
            if (distinctive || wordAmongForms) {
                return "the correct option is the only one shaped like " + s.name().toLowerCase(Locale.ROOT);
            }
        }
        if (options.stream().anyMatch(o -> shape(o.text()) == Shape.WEB)) {
            return "options are web addresses, which test trivia rather than the subject";
        }

        // Length: the one carefully qualified answer among short distractors.
        int longestWrong = wrong.stream().mapToInt(o -> Chunker.wordCount(o.text())).max().orElse(0);
        for (Option r : right) {
            int w = Chunker.wordCount(r.text());
            if (w >= 6 && w >= 2 * Math.max(1, longestWrong) && correct.size() == 1) {
                return "the correct option is much longer than every distractor";
            }
        }

        // Echo: only the correct option repeats a word of the question.
        Set<String> stemTerms = new HashSet<>(analyzer.termSet(stem));
        stemTerms.removeAll(stemWords());
        if (!stemTerms.isEmpty() && correct.size() == 1) {
            boolean rightEchoes = right.stream().anyMatch(o -> analyzer.termSet(o.text()).stream().anyMatch(stemTerms::contains));
            boolean wrongEchoes = wrong.stream().anyMatch(o -> analyzer.termSet(o.text()).stream().anyMatch(stemTerms::contains));
            if (rightEchoes && !wrongEchoes && wrong.size() >= 2) {
                return "only the correct option repeats a word of the question";
            }
        }

        // Grammar: "an ____" fits the answer alone.
        Matcher article = ARTICLE_BEFORE_BLANK.matcher(stem);
        if (article.find() && correct.size() == 1) {
            boolean an = article.group(1).equalsIgnoreCase("an");
            long fitting = wrong.stream().filter(o -> startsWithVowel(o.text()) == an).count();
            if (startsWithVowel(right.get(0).text()) == an && fitting == 0) {
                return "\"" + article.group(1) + " ____\" agrees with the correct option only";
            }
        }
        return null;
    }

    /** A fill-in-the-blank that cannot work: the answer already in the stem, or too little left to go on. */
    String blankProblem(String stem, List<Option> options, List<String> correct) {
        if (!BLANK.matcher(stem).find()) return null;
        String withoutBlank = BLANK.matcher(stem).replaceAll(" ").replaceFirst("(?i)^fill\\s+in\\s+the\\s+blanks?\\s*[:.-]?", "");
        Set<String> rest = new HashSet<>(analyzer.termSet(withoutBlank));
        rest.removeAll(stemWords());
        if (rest.size() < 3) return "the blank leaves too little of the sentence to answer from";
        String lower = withoutBlank.toLowerCase(Locale.ROOT);
        for (Option o : options) {
            if (!correct.contains(o.id())) continue;
            String a = o.text().toLowerCase(Locale.ROOT).strip();
            if (a.length() >= 4 && lower.contains(a)) return "the answer appears in the stem";
        }
        return null;
    }

    static Shape shape(String option) {
        String t = option.strip();
        if (WEB.matcher(t).find()) return Shape.WEB;
        if (NUMBER.matcher(t).matches()) return Shape.NUMBER;
        if (ACRONYM.matcher(t).matches() && t.chars().anyMatch(Character::isLetter)) return Shape.ACRONYM;
        if (CODE.matcher(t).find() && !t.contains(" ")) return Shape.CODE;
        return Shape.TEXT;
    }

    private static boolean startsWithVowel(String s) {
        String t = s.strip().toLowerCase(Locale.ROOT);
        return !t.isEmpty() && "aeiou".indexOf(t.charAt(0)) >= 0;
    }

    // ── Grounding and duplicates ─────────────────────────────────────────────

    private double grounding(String text, Set<String> sourceTerms) {
        Set<String> terms = analyzer.termSet(text);
        if (terms.isEmpty()) return 0;
        return (double) terms.stream().filter(sourceTerms::contains).count() / terms.size();
    }

    /** What a question is about: its terms without the scaffolding every question shares. */
    private List<String> subjectTerms(String text) {
        String t = BLANK.matcher(text == null ? "" : text).replaceAll(" ");
        List<String> terms = new ArrayList<>(analyzer.terms(t));
        terms.removeIf(stemWords()::contains);
        return terms;
    }

    private static String answerKey(GenQuestion q) {
        if (q.isWritten() || q.getOptions() == null || q.getCorrectAnswer() == null || q.getCorrectAnswer().isEmpty()) {
            return null;
        }
        List<String> texts = q.getOptions().stream().filter(o -> q.getCorrectAnswer().contains(o.id()))
                .map(o -> o.text().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "")).sorted().toList();
        String key = String.join("|", texts);
        return key.isEmpty() ? null : key;
    }

    /**
     * Whether {@code q} asks what an accepted question already asks: its
     * subject terms are nearly the same (TF-IDF cosine or plain overlap), or it
     * has the same correct answer and a similar stem — "Which describes a
     * deadlock?" and "A deadlock is best described as…".
     */
    boolean isDuplicate(GenQuestion q, List<GenQuestion> accepted) {
        if (accepted.isEmpty()) return false;
        List<List<String>> docs = new ArrayList<>();
        for (GenQuestion a : accepted) docs.add(subjectTerms(a.getQuestionText()));
        List<String> mine = subjectTerms(q.getQuestionText());
        if (mine.isEmpty()) return false;
        docs.add(mine);
        TfIdf model = TfIdf.fit(docs);
        Map<String, Double> v = model.vector(mine);
        Set<String> mineSet = new HashSet<>(mine);
        String key = answerKey(q);
        for (int i = 0; i < accepted.size(); i++) {
            List<String> theirs = docs.get(i);
            if (theirs.isEmpty()) continue;
            double cos = TfIdf.cosine(v, model.vector(theirs));
            if (cos > DUPLICATE_SIMILARITY) return true;
            Set<String> theirSet = new HashSet<>(theirs);
            long shared = mineSet.stream().filter(theirSet::contains).count();
            if (shared >= 2 && (double) shared / Math.min(mineSet.size(), theirSet.size()) >= DUPLICATE_OVERLAP) {
                return true;
            }
            if (key != null && key.equals(answerKey(accepted.get(i))) && cos >= SAME_ANSWER_SIMILARITY) return true;
        }
        return false;
    }

    private Set<String> stemWords() {
        Set<String> w = stemWords;
        if (w == null) stemWords = w = Set.copyOf(analyzer.termSet(SCAFFOLDING));
        return w;
    }

    private static Result reject(GenQuestion q, String why) {
        return new Result(q, why);
    }
}
