package com.lms.modules.attendance;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.attendance")
@Data
public class AttendanceProperties {
    /** Attendance percentage below which an {@link com.lms.shared.events.AttendanceAlertEvent} is published. */
    private double thresholdPercentage = 75.0;
}
