package com.lms.infrastructure.nlp;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread pools for NLP work, kept apart from the application's shared
 * {@code @Async} pool so a teacher uploading a 900-page PDF or a long
 * generation job never delays transcripts or notifications.
 *
 * <p>Plain {@link ExecutorService}s rather than {@code Executor} beans on
 * purpose: declaring an executor bean switches off Spring Boot's default
 * task executor, which every other {@code @Async} method relies on.
 */
@Component
public class NlpExecutors {

    /** Reading and indexing uploaded documents. */
    private final ExecutorService ingestion = pool("lms-nlp-ingest-", 2, 200);

    /** Question generation, mock papers and streamed chat answers. */
    private final ExecutorService tasks = pool("lms-nlp-task-", 4, 200);

    public ExecutorService ingestion() {
        return ingestion;
    }

    public ExecutorService tasks() {
        return tasks;
    }

    private static ExecutorService pool(String prefix, int threads, int queue) {
        AtomicInteger n = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, prefix + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    void shutdown() {
        ingestion.shutdownNow();
        tasks.shutdownNow();
    }
}
