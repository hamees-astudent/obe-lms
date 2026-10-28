package com.lms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A top-level key dropped into the middle of {@code spring:} in
 * application.yml re-parents every key after it, and Spring quietly falls back
 * to its defaults — that is how uploads ended up capped at 1 MB while the
 * client advertised 50 MB. These checks read the real file.
 */
class ApplicationYamlTest {

    private Binder binder;

    @BeforeEach
    void load() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))
                .forEach(env.getPropertySources()::addLast);
        binder = new Binder(ConfigurationPropertySources.get(env));
    }

    @Test
    @DisplayName("uploads accept the 50 MB the client advertises")
    void multipartLimitMatchesClient() {
        MultipartProperties multipart = binder
                .bind("spring.servlet.multipart", MultipartProperties.class)
                .orElseThrow(() -> new AssertionError("spring.servlet.multipart is not bound"));
        // Keep in step with MAX_UPLOAD_BYTES in src/main/frontend/src/lib/files.ts.
        assertThat(multipart.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(50));
        assertThat(multipart.getMaxRequestSize()).isGreaterThan(multipart.getMaxFileSize());
    }

    @Test
    @DisplayName("SMTP auth and STARTTLS sit under spring.mail")
    void mailPropertiesBound() {
        assertThat(binder.bind("spring.mail.properties.mail.smtp.starttls.enable", Bindable.of(Boolean.class)).get())
                .isTrue();
    }
}
