package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.modules.studyai.DocumentTextExtractor.Segment;
import com.lms.modules.studyai.RetrievalService.RetrievedChunk;

import java.util.List;
import java.util.UUID;

/** A small databases lecture, indexed the way ingestion would index it. */
final class NlpFixtures {

    private NlpFixtures() {}

    static final TextAnalyzer ANALYZER = new TextAnalyzer();
    static final UUID MATERIAL = UUID.randomUUID();

    static final List<Segment> LECTURE = List.of(
            new Segment("SLIDE", 1, "Primary keys",
                    "A primary key is a column or set of columns that uniquely identifies each row in a table. "
                    + "A primary key cannot contain null values. A candidate key is any minimal set of attributes "
                    + "that could serve as the primary key. Surrogate keys are system generated identifiers used "
                    + "when no natural key exists."),
            new Segment("SLIDE", 2, "Normalisation",
                    "Normalisation is the process of organising tables to reduce redundancy and update anomalies. "
                    + "First normal form requires atomic values in every column. Second normal form removes partial "
                    + "dependencies on a composite key. Third normal form removes transitive dependencies between "
                    + "non-key attributes. Denormalisation deliberately adds redundancy to speed up read queries."),
            new Segment("SLIDE", 3, "Indexes",
                    "An index is a data structure that speeds up data retrieval at the cost of extra storage. "
                    + "A B-tree index keeps keys sorted so lookups take logarithmic time. A hash index supports fast "
                    + "equality lookups but cannot answer range queries. Every index slows down insert and update "
                    + "operations because the index must also be maintained."),
            new Segment("SLIDE", 4, "Transactions",
                    "A transaction is a unit of work that is executed completely or not at all. The ACID properties "
                    + "are atomicity, consistency, isolation and durability. Isolation levels control which "
                    + "concurrency anomalies such as dirty reads and phantom reads are allowed. A deadlock occurs "
                    + "when two transactions wait for locks held by each other."));

    static List<RetrievedChunk> chunks() {
        Chunker chunker = new Chunker(ANALYZER);
        // One passage per slide here: each slide is long enough not to be merged.
        return chunker.chunk(LECTURE).stream().map(c -> new RetrievedChunk(UUID.randomUUID(), MATERIAL,
                "Lecture 5 — Database design", c.index(), c.locationType(), c.start(), c.end(), c.heading(),
                c.content(), c.keyPhrases(), c.tokenEstimate(), 1.0 / (c.index() + 1))).toList();
    }

    private static final List<CourseContextReader.CloRow> CLOS = List.of(
                new CourseContextReader.CloRow(UUID.randomUUID(), "CLO-1",
                        "Describe relational keys and integrity constraints", null, "UNDERSTAND"),
                new CourseContextReader.CloRow(UUID.randomUUID(), "CLO-2",
                        "Apply normalisation to remove redundancy from relational schemas", null, "APPLY"),
                new CourseContextReader.CloRow(UUID.randomUUID(), "CLO-3",
                        "Analyze transaction isolation, concurrency and indexing trade-offs", null, "ANALYZE"));

    static List<CourseContextReader.CloRow> clos() {
        return CLOS;
    }
}
