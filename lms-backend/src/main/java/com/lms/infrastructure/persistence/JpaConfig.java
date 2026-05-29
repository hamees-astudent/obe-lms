package com.lms.infrastructure.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA / Spring Data infrastructure configuration.
 *
 * <ul>
 *   <li>{@code @EnableJpaAuditing} activates {@link org.springframework.data.annotation.CreatedDate},
 *       {@link org.springframework.data.annotation.LastModifiedDate},
 *       {@link org.springframework.data.annotation.CreatedBy}, and
 *       {@link org.springframework.data.annotation.LastModifiedBy} on all entities
 *       that use {@link org.springframework.data.jpa.domain.support.AuditingEntityListener}.</li>
 *   <li>{@code auditorAwareRef} wires in {@link AuditorAwareImpl} which reads the currently
 *       authenticated user's email from the Spring Security context.</li>
 * </ul>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableTransactionManagement
public class JpaConfig {
}
