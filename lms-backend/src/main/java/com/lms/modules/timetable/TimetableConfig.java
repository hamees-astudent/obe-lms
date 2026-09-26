package com.lms.modules.timetable;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TimetableConfig {

    @Bean
    TimetableGrid timetableGrid(TimetableProperties props) {
        return TimetableGrid.from(props);
    }

    @Bean
    TimetableGenerator timetableGenerator(TimetableGrid grid, TimetableProperties props) {
        return new TimetableGenerator(grid, props.getAttempts());
    }
}
