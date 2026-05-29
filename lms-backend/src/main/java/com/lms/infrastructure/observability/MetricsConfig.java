package com.lms.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer / Actuator configuration.
 *
 * <p>Spring Boot auto-configures metrics for:
 * <ul>
 *   <li>HTTP server requests (latency, error rate) via {@code spring-boot-starter-web}</li>
 *   <li>JVM memory, GC, threads via {@code jvm.*} meters</li>
 *   <li>HikariCP connection pool via {@code hikaricp.*} meters</li>
 *   <li>Spring Cache (Redis) via {@code cache.*} meters</li>
 *   <li>Kafka producer/consumer via {@code kafka.*} meters</li>
 *   <li>Spring Data repositories via {@code spring.data.repository.*} meters</li>
 * </ul>
 *
 * <p>This class only adds customizations that go beyond auto-configuration:
 * a {@link MeterRegistryCustomizer} that stamps every metric with common
 * {@code application} and {@code environment} tags (mirrors what is set in
 * {@code management.metrics.tags} in {@code application.yml} — the YAML tags
 * cover Prometheus; this bean covers any in-process registry used in tests).
 *
 * <h3>Dashboards</h3>
 * Import the standard Grafana dashboard IDs to get started:
 * <ul>
 *   <li><b>4701</b> — JVM (Micrometer)</li>
 *   <li><b>11378</b> — Spring Boot 3 Statistics</li>
 *   <li><b>7589</b> — Kafka Overview</li>
 * </ul>
 */
@Configuration
public class MetricsConfig {

    /**
     * Stamps every metric — regardless of which registry receives it — with
     * {@code application} and {@code environment} tags so Grafana queries can
     * filter by service and deployment environment without extra relabelling.
     *
     * <p>The tag values are resolved from environment variables / properties at
     * startup (see {@code management.metrics.tags} in {@code application.yml}).
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            org.springframework.core.env.Environment env) {
        String appName = env.getProperty("spring.application.name", "lms-backend");
        String environment = env.getProperty("ENVIRONMENT", "local");
        return registry -> registry.config()
                .commonTags("application", appName, "environment", environment);
    }
}
