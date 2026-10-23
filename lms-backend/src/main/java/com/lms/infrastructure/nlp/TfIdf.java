package com.lms.infrastructure.nlp;

import java.util.*;

/**
 * TF-IDF vectors and cosine similarity over a small corpus (a course's
 * chunks, or its CLOs plus the question being matched).
 *
 * <p>Built per request from already-stemmed terms; a course has at most a few
 * thousand chunks, so there is no index to maintain.
 */
public final class TfIdf {

    private final Map<String, Double> idf;
    private final double defaultIdf;

    private TfIdf(Map<String, Double> idf, double defaultIdf) {
        this.idf = idf;
        this.defaultIdf = defaultIdf;
    }

    /** Fits inverse document frequencies on {@code documents} (each a term list). */
    public static TfIdf fit(Collection<List<String>> documents) {
        Map<String, Integer> df = new HashMap<>();
        for (List<String> doc : documents) {
            for (String term : new HashSet<>(doc)) df.merge(term, 1, Integer::sum);
        }
        int n = Math.max(1, documents.size());
        Map<String, Double> idf = new HashMap<>();
        df.forEach((term, count) -> idf.put(term, Math.log(1.0 + (double) n / count)));
        // An unseen term is as rare as possible.
        return new TfIdf(idf, Math.log(1.0 + n));
    }

    /** Log-scaled term frequency × idf, L2-normalised. */
    public Map<String, Double> vector(List<String> terms) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : terms) tf.merge(t, 1, Integer::sum);
        Map<String, Double> v = new HashMap<>();
        double norm = 0;
        for (var e : tf.entrySet()) {
            double w = (1 + Math.log(e.getValue())) * idf.getOrDefault(e.getKey(), defaultIdf);
            v.put(e.getKey(), w);
            norm += w * w;
        }
        if (norm > 0) {
            double n = Math.sqrt(norm);
            v.replaceAll((k, w) -> w / n);
        }
        return v;
    }

    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Map<String, Double> small = a.size() <= b.size() ? a : b;
        Map<String, Double> large = small == a ? b : a;
        double dot = 0;
        for (var e : small.entrySet()) {
            Double w = large.get(e.getKey());
            if (w != null) dot += e.getValue() * w;
        }
        return dot;
    }

    public double similarity(List<String> a, List<String> b) {
        return cosine(vector(a), vector(b));
    }
}
