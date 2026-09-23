package com.lms.modules.transcript;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.modules.transcript.dto.CloAttainmentDetail;
import com.lms.modules.transcript.dto.CourseTranscriptDetail;
import com.lms.modules.transcript.dto.PloAttainmentDetail;
import com.lms.modules.transcript.dto.TranscriptSnapshotData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A transcript is stored as a JSON map and read back into
 * {@link TranscriptSnapshotData} for the detail view and the PDF. If the
 * nested DTOs cannot be rebuilt from that map, every transcript detail
 * request fails.
 */
class TranscriptSnapshotRoundTripTest {

    @Test
    void storedSnapshotReadsBackIntoDto() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(ctx -> {
                    ObjectMapper mapper = ctx.getBean(ObjectMapper.class);
                    var original = TranscriptSnapshotData.builder()
                            .studentName("Ayesha")
                            .semesterName("Fall 2026")
                            .semesterGpa(3.5)
                            .cumulativeGpa(3.4)
                            .courses(List.of(CourseTranscriptDetail.builder()
                                    .pscId(UUID.randomUUID())
                                    .courseCode("CS-101")
                                    .courseName("Intro")
                                    .creditHours(3)
                                    .gradeLetter("A")
                                    .gradePoints(4.0)
                                    .cloAttainment(List.of(CloAttainmentDetail.builder()
                                            .cloCode("CLO-1").cloTitle("t").attainmentPercentage(80.0).build()))
                                    .build()))
                            .ploAttainment(List.of(PloAttainmentDetail.builder()
                                    .ploCode("PLO-1").ploTitle("p").attainmentPercentage(70.0).build()))
                            .build();

                    Map<String, Object> stored = mapper.convertValue(original, new TypeReference<>() {});
                    TranscriptSnapshotData read = mapper.convertValue(stored, TranscriptSnapshotData.class);

                    assertThat(read.getSemesterGpa()).isEqualTo(3.5);
                    assertThat(read.getCourses()).singleElement()
                            .satisfies(c -> {
                                assertThat(c.getGradeLetter()).isEqualTo("A");
                                assertThat(c.getCloAttainment()).singleElement()
                                        .extracting(CloAttainmentDetail::getCloCode).isEqualTo("CLO-1");
                            });
                    assertThat(read.getPloAttainment()).singleElement()
                            .extracting(PloAttainmentDetail::getPloCode).isEqualTo("PLO-1");
                });
    }
}
