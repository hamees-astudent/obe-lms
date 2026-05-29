package com.lms.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka infrastructure configuration.
 *
 * <p>Declares all application topics as {@link NewTopic} beans so that Spring Boot's
 * {@code KafkaAdmin} creates them on startup (respecting the
 * {@code spring.cloud.stream.kafka.binder.auto-create-topics} flag).
 *
 * <ul>
 *   <li><b>Partitions: 3</b> — allows three parallel consumers per consumer group.</li>
 *   <li><b>Replicas: 1</b> — suitable for a single-broker dev/test environment;
 *       increase to 3 in production.</li>
 * </ul>
 *
 * <p>Serialization is handled by Spring Cloud Stream's content-type negotiation
 * (configured as {@code application/json} in {@code application.yml}); no explicit
 * serializer/deserializer beans are required here.
 */
@Configuration
public class KafkaConfig {

    private static final int PARTITIONS = 3;
    private static final int REPLICAS   = 1;

    @Bean
    public NewTopic enrollmentEventsTopic() {
        return TopicBuilder.name(KafkaTopics.TOPIC_ENROLLMENT)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic attendanceEventsTopic() {
        return TopicBuilder.name(KafkaTopics.TOPIC_ATTENDANCE)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic assessmentEventsTopic() {
        return TopicBuilder.name(KafkaTopics.TOPIC_ASSESSMENT)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic semesterEventsTopic() {
        return TopicBuilder.name(KafkaTopics.TOPIC_SEMESTER)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }
}
