package com.lms.infrastructure.nlp;

import lombok.extern.slf4j.Slf4j;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Classical NLP over English text: sentence splitting, tokenising, part-of-
 * speech tagging (Apache OpenNLP, UD English models bundled as jars), Porter
 * stemming, and key-phrase extraction.
 *
 * <p>Key phrases are noun phrases found by a part-of-speech pattern — runs of
 * adjectives and nouns ending in a noun ("binary search tree", "TCP handshake")
 * — scored by frequency and length. They name what a passage is about, and the
 * rest of the pipeline uses them for topics, cloze questions, MCQ distractors
 * and grounding checks.
 *
 * <p>OpenNLP's tools are not thread-safe but its models are, so each thread
 * gets its own tool instances over the shared models. If a model fails to load
 * the analyser falls back to regex splitting and stopword-delimited phrases
 * (RAKE), so it degrades rather than failing the application.
 */
@Slf4j
@Component
public class TextAnalyzer {

    static final String SENTENCE_MODEL = "/opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin";
    static final String TOKEN_MODEL    = "/opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin";
    static final String POS_MODEL      = "/opennlp-en-ud-ewt-pos-1.3-2.5.4.bin";

    private static final Pattern SENTENCE_FALLBACK = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z0-9\"'(])");
    private static final Pattern TOKEN_FALLBACK = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'+#.-]*[\\p{L}\\p{N}+#]|[\\p{L}\\p{N}]");
    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}\\p{N}+#'-]*");
    /** For term matching hyphens split words ("user-level" → user, level), as the tokenizer does. */
    private static final Pattern TERM = Pattern.compile("[\\p{L}][\\p{L}\\p{N}+#']*");

    private final SentenceModel sentenceModel;
    private final TokenizerModel tokenModel;
    private final POSModel posModel;

    private final ThreadLocal<SentenceDetectorME> sentenceDetector;
    private final ThreadLocal<TokenizerME> tokenizer;
    private final ThreadLocal<POSTaggerME> tagger;
    private final ThreadLocal<PorterStemmer> stemmer = ThreadLocal.withInitial(PorterStemmer::new);

    public TextAnalyzer() {
        this.sentenceModel = load(SENTENCE_MODEL, SentenceModel::new);
        this.tokenModel = load(TOKEN_MODEL, TokenizerModel::new);
        this.posModel = load(POS_MODEL, POSModel::new);
        this.sentenceDetector = ThreadLocal.withInitial(() ->
                sentenceModel == null ? null : new SentenceDetectorME(sentenceModel));
        this.tokenizer = ThreadLocal.withInitial(() ->
                tokenModel == null ? null : new TokenizerME(tokenModel));
        this.tagger = ThreadLocal.withInitial(() ->
                posModel == null ? null : new POSTaggerME(posModel));
    }

    private interface ModelLoader<M> { M load(InputStream in) throws Exception; }

    private static <M> M load(String resource, ModelLoader<M> loader) {
        try (InputStream in = TextAnalyzer.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("OpenNLP model {} not on the classpath; using rule-based fallback", resource);
                return null;
            }
            return loader.load(in);
        } catch (Exception e) {
            log.warn("Could not load OpenNLP model {}: {}; using rule-based fallback", resource, e.toString());
            return null;
        }
    }

    /** Whether the statistical models loaded (false means rule-based fallback). */
    public boolean modelsLoaded() {
        return sentenceModel != null && tokenModel != null && posModel != null;
    }

    // ── Sentences and tokens ─────────────────────────────────────────────────

    public List<String> sentences(String text) {
        if (text == null || text.isBlank()) return List.of();
        String normalised = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
        List<String> out = new ArrayList<>();
        // Line breaks inside extracted PDF text are mostly layout, but a blank
        // line or a bullet starts a new statement.
        for (String block : normalised.split("\\n\\s*\\n|\\n(?=\\s*[•\\-*–▪●◦]\\s)")) {
            String b = block.replace('\n', ' ').replaceAll("^\\s*[•\\-*–▪●◦]\\s*", "").trim();
            if (b.isEmpty()) continue;
            SentenceDetectorME detector = sentenceDetector.get();
            String[] parts = detector != null ? detector.sentDetect(b) : SENTENCE_FALLBACK.split(b);
            for (String s : parts) {
                String t = s.trim();
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    public String[] tokens(String sentence) {
        TokenizerME tk = tokenizer.get();
        if (tk != null) return tk.tokenize(sentence);
        List<String> toks = new ArrayList<>();
        var m = TOKEN_FALLBACK.matcher(sentence);
        while (m.find()) toks.add(m.group());
        return toks.toArray(String[]::new);
    }

    /** Universal POS tags (NOUN, VERB, ADJ, …), or null without the model. */
    public String[] posTags(String[] tokens) {
        POSTaggerME t = tagger.get();
        return t == null ? null : t.tag(tokens);
    }

    public String stem(String word) {
        return stemmer.get().stem(word.toLowerCase(Locale.ROOT));
    }

    /**
     * Content terms of a text: lower-cased, stopwords removed, stemmed. The
     * unit TF-IDF and keyword overlap are computed over.
     */
    public List<String> terms(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        var m = TERM.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String w = m.group();
            if (w.length() < 2 || Stopwords.contains(w)) continue;
            out.add(stem(w));
        }
        return out;
    }

    public Set<String> termSet(String text) {
        return new HashSet<>(terms(text));
    }

    // ── Key phrases ──────────────────────────────────────────────────────────

    public record KeyPhrase(String text, double score, int frequency) {}

    /** The {@code limit} best noun phrases in a text. */
    public List<KeyPhrase> keyPhrases(String text, int limit) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        Map<String, String> display = new HashMap<>();
        for (String sentence : sentences(text)) {
            for (String phrase : candidatePhrases(sentence)) {
                String key = phrase.toLowerCase(Locale.ROOT);
                freq.merge(key, 1, Integer::sum);
                display.putIfAbsent(key, phrase);
            }
        }
        return freq.entrySet().stream()
                .map(e -> {
                    int words = e.getKey().split(" ").length;
                    double score = e.getValue() * (1 + 0.6 * (words - 1));
                    return new KeyPhrase(display.get(e.getKey()), score, e.getValue());
                })
                .sorted(Comparator.comparingDouble(KeyPhrase::score).reversed()
                        .thenComparing(KeyPhrase::text))
                .limit(limit)
                .toList();
    }

    /** Noun phrases of one sentence, as they appear in it. */
    public List<String> candidatePhrases(String sentence) {
        String[] toks = tokens(sentence);
        String[] tags = posTags(toks);
        return tags != null ? posPhrases(toks, tags) : rakePhrases(toks);
    }

    private List<String> posPhrases(String[] toks, String[] tags) {
        List<String> phrases = new ArrayList<>();
        int i = 0;
        while (i < toks.length) {
            if (!isNominal(tags[i]) && !isAdjective(tags[i])) { i++; continue; }
            // "Paging divides memory": the verb after a gerund subject would open a phrase.
            if (i > 0 && isNominal(tags[i]) && verbBase(toks[i]) != null
                    && (tags[i - 1].equals("VERB") || toks[i - 1].toLowerCase(Locale.ROOT).endsWith("ing"))) {
                i++;
                continue;
            }
            int start = i;
            while (i < toks.length && (isNominal(tags[i]) || isAdjective(tags[i]))) {
                if (i > start && isMistaggedVerb(toks, tags, i)) break;
                i++;
            }
            if (i < toks.length && i > start && isMistaggedVerb(toks, tags, i)) {
                // Close the phrase before the verb; the verb itself starts nothing.
                int end = i;
                while (end > start && !isNominal(tags[end - 1])) end--;
                if (end - start >= 1 && end - start <= 4) add(phrases, toks, start, end);
                i++;
                continue;
            }
            int end = i;                                     // exclusive
            while (end > start && !isNominal(tags[end - 1])) end--;  // must end on a noun
            while (start < end && (Stopwords.contains(toks[start].toLowerCase(Locale.ROOT))
                    || !WORD.matcher(toks[start]).matches())) start++;
            if (end - start >= 1 && end - start <= 4) add(phrases, toks, start, end);
            else if (end - start > 4) add(phrases, toks, end - 4, end);
        }
        return phrases;
    }

    private List<String> rakePhrases(String[] toks) {
        List<String> phrases = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= toks.length; i++) {
            boolean breaks = i == toks.length || Stopwords.contains(toks[i].toLowerCase(Locale.ROOT))
                    || !WORD.matcher(toks[i]).matches();
            if (!breaks && start < 0) start = i;
            if (breaks && start >= 0) {
                if (i - start <= 4) add(phrases, toks, start, i);
                start = -1;
            }
        }
        return phrases;
    }

    private static void add(List<String> phrases, String[] toks, int start, int end) {
        String p = String.join(" ", Arrays.copyOfRange(toks, start, end)).trim();
        // Single short words ("use", "way") carry no topic.
        if (p.length() < 3 || (end - start == 1 && p.length() < 4)) return;
        if (Stopwords.contains(p.toLowerCase(Locale.ROOT))) return;
        phrases.add(p);
    }

    /**
     * The bundled tagger often reads a present-tense verb after its subject
     * as a noun ("the algorithm/NOUN avoids/NOUN deadlock"), which would run
     * subject, verb and object into one "phrase". A common verb in -s form,
     * straight after a singular noun, with the clause carrying on after it,
     * is taken as the verb. Compounds that end a noun group ("system calls
     * are…") are left alone.
     */
    private boolean isMistaggedVerb(String[] toks, String[] tags, int i) {
        String w = toks[i].toLowerCase(Locale.ROOT);
        if (!isNominal(tags[i])) return false;
        // "threads of a process share its address space": a base-form verb
        // after a noun, followed by what it acts on.
        if (COMMON_VERBS.contains(w) && isNominal(tags[i - 1]) && i + 1 < toks.length
                && (tags[i + 1].equals("DET") || tags[i + 1].equals("PRON") || tags[i + 1].equals("ADP"))) {
            return true;
        }
        if (verbBase(w) == null) return false;
        String prev = toks[i - 1].toLowerCase(Locale.ROOT);
        if (!isNominal(tags[i - 1]) || isPlural(prev)) return false;
        if (i + 1 >= toks.length) return false;
        String next = tags[i + 1];
        return !(next.equals("AUX") || next.equals("VERB") || next.equals("PUNCT") || next.equals("CCONJ"));
    }

    /** The base form of a common verb in -s form ("avoids" → "avoid"), else null. */
    private static String verbBase(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (!w.endsWith("s") || w.endsWith("ss") || w.length() < 4) return null;
        if (w.endsWith("ies") && COMMON_VERBS.contains(w.substring(0, w.length() - 3) + "y")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("es") && COMMON_VERBS.contains(w.substring(0, w.length() - 2))) return w.substring(0, w.length() - 2);
        String base = w.substring(0, w.length() - 1);
        return COMMON_VERBS.contains(base) ? base : null;
    }

    /** "threads", not "process", "status" or "analysis". */
    private static boolean isPlural(String w) {
        return w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is");
    }

    /** Verbs common in course material, in base form. */
    private static final Set<String> COMMON_VERBS = Set.of(
            "allow", "apply", "avoid", "behave", "build", "call", "carry", "cause", "change", "check", "choose",
            "consist", "contain", "control", "convert", "create", "decide", "define", "depend", "describe",
            "detect", "determine", "divide", "enable", "ensure", "execute", "exist", "follow", "generate", "give",
            "grant", "handle", "help", "hold", "identify", "improve", "include", "increase", "keep", "lead",
            "let", "limit", "load", "maintain", "make", "manage", "map", "mean", "measure", "move", "need",
            "occur", "perform", "prevent", "process", "produce", "protect", "provide", "reduce", "refer",
            "remain", "remove", "represent", "require", "return", "run", "save", "schedule", "send", "share",
            "show", "solve", "specify", "speed", "start", "stay", "store", "support", "take", "tell", "track",
            "transfer", "use", "vary", "wait", "work", "write");

    private static boolean isNominal(String tag) {
        return tag.equals("NOUN") || tag.equals("PROPN") || tag.startsWith("NN");
    }

    private static boolean isAdjective(String tag) {
        return tag.equals("ADJ") || tag.startsWith("JJ");
    }

    /** Rough token count for budgeting a prompt: about four characters per token. */
    public static int estimateTokens(String text) {
        return text == null ? 0 : (text.length() + 3) / 4;
    }

    /** Distinct values, preserving first-seen order. */
    public static List<String> distinctIgnoreCase(Collection<String> values) {
        Set<String> seen = new HashSet<>();
        return values.stream().filter(v -> seen.add(v.toLowerCase(Locale.ROOT))).collect(Collectors.toList());
    }
}
