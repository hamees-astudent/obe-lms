package com.lms.infrastructure.nlp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NlpProperties.class)
class NlpConfig {
}
