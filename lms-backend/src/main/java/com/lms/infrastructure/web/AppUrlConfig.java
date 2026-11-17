package com.lms.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exposes {@link AppUrls}; an invalid {@code APP_BASE_URL} stops startup instead of producing broken links. */
@Slf4j
@Configuration
@EnableConfigurationProperties(AppUrlProperties.class)
class AppUrlConfig {

    @Bean
    AppUrls appUrls(AppUrlProperties properties) {
        AppUrls urls = new AppUrls(properties.getBaseUrl());
        if (urls.isLocal()) {
            log.warn("App base URL is {} — links the app sends out (e.g. password-reset e-mails) will only work "
                    + "on this machine. Set APP_BASE_URL to the address users open the app at.", urls);
        } else {
            log.info("App base URL: {}", urls);
        }
        return urls;
    }
}
