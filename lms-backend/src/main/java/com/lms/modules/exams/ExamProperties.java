package com.lms.modules.exams;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for exam marks-sheet extraction.
 *
 * <p>Extraction is optional. With no API key configured the extractor bean is
 * not created and the module still works — teachers enter marks manually. That
 * keeps the application startable on a machine with no Claude credentials
 * (a fresh checkout, CI, an offline demo) rather than failing at boot.
 */
@Component
@ConfigurationProperties(prefix = "app.exams.extraction")
@Data
public class ExamProperties {

    /** Master switch; extraction is also skipped when {@link #apiKey} is blank. */
    private boolean enabled = true;

    /** Anthropic API key. Blank disables extraction. */
    private String apiKey = "";

    /** Vision model used to read the marks sheet. */
    private String model = "claude-opus-5-5";

    /**
     * Largest page image accepted, in bytes. Well below the 50 MB multipart
     * limit: a phone photo of one page compresses to a few hundred KB, and
     * anything far larger is a wrong file rather than a marks sheet.
     */
    private long maxImageBytes = 10L * 1024 * 1024;

    /** How long to wait for one extraction before giving up. */
    private int timeoutSeconds = 120;
}
