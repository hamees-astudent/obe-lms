package com.lms.infrastructure.nlp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Assigns a Bloom's taxonomy level to a question or a learning outcome from
 * its action verbs — the standard way OBE reviewers classify them.
 *
 * <p>Each level has a list of action verbs ("define" → Remember, "compare" →
 * Analyze, "design" → Create). The verb that opens the question decides,
 * because that is the instruction to the student; otherwise the highest level
 * any verb in the text reaches. Questions that start with an interrogative
 * ("What is…", "Which of the following…") ask for recall. Failing all that,
 * the question type gives a default: choice questions test recall, short
 * answers understanding, long answers analysis.
 *
 * <p>Verbs are compared by Porter stem, so "explains", "explaining" and
 * "explain" all match.
 */
@Component
@RequiredArgsConstructor
public class BloomClassifier {

    private static final Map<BloomLevel, List<String>> VERBS = new EnumMap<>(Map.of(
            BloomLevel.REMEMBER, List.of("define", "list", "name", "state", "recall", "recognize",
                    "recognise", "label", "match", "memorize", "repeat", "record", "identify", "outline",
                    "select", "reproduce", "locate", "enumerate", "mention", "tell"),
            BloomLevel.UNDERSTAND, List.of("explain", "describe", "summarize", "summarise", "classify",
                    "discuss", "interpret", "paraphrase", "illustrate", "clarify", "restate", "translate",
                    "distinguish", "express", "report", "review", "infer", "generalize", "contrast",
                    "understand", "convert", "exemplify"),
            BloomLevel.APPLY, List.of("apply", "calculate", "compute", "solve", "demonstrate", "use",
                    "implement", "execute", "modify", "operate", "show", "sketch", "construct", "determine",
                    "practice", "predict", "prepare", "produce", "relate", "employ", "simulate", "trace",
                    "write", "draw", "find", "convert", "estimate"),
            BloomLevel.ANALYZE, List.of("analyze", "analyse", "compare", "differentiate", "examine",
                    "investigate", "categorize", "categorise", "deconstruct", "diagram", "dissect",
                    "organize", "organise", "attribute", "break", "inspect", "test",
                    "correlate", "deduce", "debug", "derive"),
            BloomLevel.EVALUATE, List.of("evaluate", "assess", "justify", "critique", "criticize",
                    "criticise", "judge", "defend", "argue", "appraise", "recommend", "prioritize",
                    "prioritise", "rank", "rate", "support", "validate", "verify", "weigh", "conclude",
                    "measure", "select best", "choose", "decide"),
            BloomLevel.CREATE, List.of("design", "create", "develop", "formulate", "compose", "construct new",
                    "devise", "invent", "plan", "propose", "generate", "build", "author", "combine",
                    "hypothesize", "hypothesise", "integrate", "originate", "produce new", "synthesize",
                    "synthesise", "model", "architect")));

    /** Openers that ask for a fact: "What is…", "Which of the following…". */
    private static final Set<String> RECALL_OPENERS = Set.of("what", "which", "who", "when", "where");

    /** "1.", "Q3:", "(a)" before the question itself. */
    private static final java.util.regex.Pattern NUMBERING =
            java.util.regex.Pattern.compile("^(?:Q?\\d+[.):]|\\([a-z]\\)|[a-z]\\))\\s*", java.util.regex.Pattern.CASE_INSENSITIVE);

    private final TextAnalyzer analyzer;

    /** Stem → level, highest level wins for a verb listed twice. */
    private Map<String, BloomLevel> stemIndex;

    public record Classification(BloomLevel level, String matchedVerb, String source) {}

    public Classification classify(String text, String questionType) {
        Map<String, BloomLevel> index = index();
        String clean = text == null ? "" : NUMBERING.matcher(text.trim()).replaceFirst("");
        String[] tokens = analyzer.tokens(clean);
        String[] tags = tokens.length == 0 ? null : analyzer.posTags(tokens);
        String lower = clean.toLowerCase(Locale.ROOT);

        // The opening word is the instruction to the student: "Explain…",
        // "Briefly explain…". Imperatives are often mis-tagged as nouns, so the
        // opener is trusted whatever its tag; later words only count as verbs.
        int opener = tokens.length > 1 && isAdverb(tokens[0], index) ? 1 : 0;
        if (tokens.length > opener) {
            String word = tokens[opener].toLowerCase(Locale.ROOT);
            BloomLevel level = index.get(analyzer.stem(word));
            if (level != null) return new Classification(level, word, "opening verb \"" + word + "\"");
            if (RECALL_OPENERS.contains(word)) {
                // "What is the difference between…" still asks for analysis.
                Optional<Map.Entry<String, BloomLevel>> inner = highestVerb(tokens, tags, index);
                if (inner.isPresent() && inner.get().getValue().rank() >= BloomLevel.ANALYZE.rank()) {
                    return new Classification(inner.get().getValue(), inner.get().getKey(),
                            "verb \"" + inner.get().getKey() + "\"");
                }
                if (lower.contains("difference between") || lower.contains("differ from")) {
                    return new Classification(BloomLevel.ANALYZE, "difference", "asks for a difference");
                }
                if (lower.contains("best describes") || lower.contains("most likely")) {
                    return new Classification(BloomLevel.UNDERSTAND, word, "asks to interpret");
                }
                return new Classification(BloomLevel.REMEMBER, word, "factual question (\"" + word + "\")");
            }
            if (word.equals("why") || word.equals("how")) {
                return new Classification(BloomLevel.UNDERSTAND, word, "asks " + word);
            }
        }

        Optional<Map.Entry<String, BloomLevel>> any = highestVerb(tokens, tags, index);
        if (any.isPresent()) {
            return new Classification(any.get().getValue(), any.get().getKey(),
                    "verb \"" + any.get().getKey() + "\"");
        }

        BloomLevel fallback = switch (questionType == null ? "" : questionType) {
            case "LONG_ANSWER"  -> BloomLevel.ANALYZE;
            case "SHORT_ANSWER" -> BloomLevel.UNDERSTAND;
            default             -> BloomLevel.REMEMBER;
        };
        return new Classification(fallback, null, "default for " + (questionType == null ? "question" : questionType));
    }

    /** "Briefly", "Clearly" — but not "Apply", which also ends in -ly. */
    private boolean isAdverb(String token, Map<String, BloomLevel> index) {
        String w = token.toLowerCase(Locale.ROOT);
        return w.endsWith("ly") && !index.containsKey(analyzer.stem(w));
    }

    /** The level a CLO statement is written at (its opening action verb). */
    public Optional<BloomLevel> classifyOutcome(String outcome) {
        if (outcome == null || outcome.isBlank()) return Optional.empty();
        Classification c = classify(outcome, null);
        return c.matchedVerb() == null ? Optional.empty() : Optional.of(c.level());
    }

    /**
     * The highest-level action verb in the text. With a tagger only words
     * tagged as verbs count, so "the design of" or "the data model" are not
     * read as Create.
     */
    private Optional<Map.Entry<String, BloomLevel>> highestVerb(String[] tokens, String[] tags,
                                                                Map<String, BloomLevel> index) {
        Map.Entry<String, BloomLevel> best = null;
        for (int i = 0; i < tokens.length; i++) {
            if (tags != null && !(tags[i].equals("VERB") || tags[i].startsWith("VB"))) continue;
            String word = tokens[i].toLowerCase(Locale.ROOT);
            BloomLevel level = index.get(analyzer.stem(word));
            if (level != null && (best == null || level.rank() > best.getValue().rank())) {
                best = Map.entry(word, level);
            }
        }
        return Optional.ofNullable(best);
    }

    private Map<String, BloomLevel> index() {
        if (stemIndex == null) {
            Map<String, BloomLevel> idx = new HashMap<>();
            VERBS.forEach((level, verbs) -> {
                for (String v : verbs) {
                    if (v.contains(" ")) continue;        // phrases are documentation only
                    // A verb listed under two levels is filed under the lower
                    // one: "identify" alone is recall, not analysis.
                    idx.merge(analyzer.stem(v), level, (a, b) -> a.rank() <= b.rank() ? a : b);
                }
            });
            stemIndex = idx;
        }
        return stemIndex;
    }
}
