package com.lms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
@EnableCaching
@EnableFeignClients(basePackages = "com.lms")
@EnableAsync
public class LmsApplication {

    /** The institution's zone; override with {@code APP_TIME_ZONE}. */
    static final String DEFAULT_TIME_ZONE = "Asia/Karachi";

    public static void main(String[] args) {
        pinTimeZone(System.getenv("APP_TIME_ZONE"));
        SpringApplication.run(LmsApplication.class, args);
    }

    /**
     * Every date-time in the system is a zone-less {@code LocalDateTime}: due
     * dates, quiz windows and attempt start times are stored, compared against
     * {@code LocalDateTime.now()} and sent to the browser without an offset,
     * and the browser reads them as its own local time. That only holds if the
     * JVM runs in the users' zone. Left to inherit the host's zone — UTC in a
     * container — every deadline check was off by five hours and a quiz timer
     * could read a just-started attempt as already expired.
     *
     * <p>Set before Spring starts, so nothing reads the old default.
     */
    static void pinTimeZone(String configured) {
        String zone = configured == null || configured.isBlank() ? DEFAULT_TIME_ZONE : configured;
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)));
    }
}
