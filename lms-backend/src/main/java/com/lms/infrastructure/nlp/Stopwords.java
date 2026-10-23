package com.lms.infrastructure.nlp;

import java.util.Set;

/** English function words, plus filler common in lecture slides. */
final class Stopwords {

    private Stopwords() {}

    private static final Set<String> WORDS = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "also", "am", "an", "and", "any",
            "are", "aren't", "as", "at", "be", "because", "been", "before", "being", "below", "between",
            "both", "but", "by", "can", "cannot", "could", "did", "do", "does", "doing", "down", "during",
            "each", "eg", "e.g", "etc", "few", "for", "from", "further", "had", "has", "have", "having",
            "he", "her", "here", "hers", "herself", "him", "himself", "his", "how", "however", "i", "ie",
            "i.e", "if", "in", "into", "is", "isn't", "it", "it's", "its", "itself", "just", "let", "may",
            "me", "might", "more", "most", "must", "my", "myself", "no", "nor", "not", "now", "of", "off",
            "on", "once", "one", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over",
            "own", "per", "same", "shall", "she", "should", "so", "some", "such", "than", "that", "the",
            "their", "theirs", "them", "themselves", "then", "there", "these", "they", "this", "those",
            "through", "thus", "to", "too", "under", "until", "up", "upon", "us", "use", "used", "uses",
            "using", "very", "via", "was", "we", "were", "what", "when", "where", "whether", "which",
            "while", "who", "whom", "why", "will", "with", "within", "without", "would", "yet", "you",
            "your", "yours", "yourself", "yourselves", "many", "much", "every", "example", "examples",
            "slide", "slides", "lecture", "chapter", "page", "figure", "fig", "table", "section", "today",
            "following", "given", "known", "called", "like", "way", "ways", "thing", "things", "lot",
            "type", "types", "kind", "kinds", "part", "parts", "case", "cases", "number", "set", "two",
            "three", "first", "second", "new", "well", "make", "makes", "made", "get", "gets", "see");

    static boolean contains(String lowerCaseWord) {
        return WORDS.contains(lowerCaseWord);
    }
}
