package com.lms.infrastructure.persistence;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the current user's email to Spring Data JPA auditing.
 * Used by {@code @CreatedBy} / {@code @LastModifiedBy} fields on entities.
 *
 * Returns {@link Optional#empty()} for unauthenticated or anonymous requests
 * (e.g. public endpoints, Flyway migrations, system-triggered jobs).
 */
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return Optional.of(auth.getName()); // getName() returns UserPrincipal.getUsername() = email
    }
}
