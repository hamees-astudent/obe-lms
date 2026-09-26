package com.lms.seed;

import com.lms.seed.SeedCatalog.CourseDef;
import com.lms.seed.SeedCatalog.CourseKind;
import com.lms.seed.SeedCatalog.GradeBand;
import com.lms.seed.SeedCatalog.GradingScaleDef;
import com.lms.seed.SeedCatalog.ProgramDef;
import com.lms.seed.SeedModel.Term;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Invariants of the demo-data catalog that the seeder relies on. */
class SeedCatalogTest {

    @Test
    @DisplayName("course codes follow PR-### and every curriculum course exists")
    void courseCodes() {
        assertThat(SeedCatalog.courses().keySet()).allMatch(code -> code.matches("^[A-Z]{2}-\\d{3}$"));
        Set<String> used = new HashSet<>();
        for (ProgramDef p : SeedCatalog.PROGRAMS) {
            p.semesters().forEach(sem -> sem.forEach(code -> used.add(SeedCatalog.course(code).code())));
        }
        assertThat(used).containsExactlyInAnyOrderElementsOf(SeedCatalog.courses().keySet());
    }

    @Test
    @DisplayName("BS programs have 8 semesters of 5 courses, MS CS 4 of 4")
    void curricula() {
        for (ProgramDef p : SeedCatalog.PROGRAMS) {
            int courses = p.code().startsWith("MS") ? 4 : 5;
            assertThat(p.semesters()).hasSize(p.durationYears() * 2)
                    .allSatisfy(sem -> assertThat(sem).hasSize(courses).doesNotHaveDuplicates());
        }
    }

    @Test
    @DisplayName("each regular course has 6 quiz questions and 3–4 CLOs")
    void questionsAndClos() {
        for (CourseDef c : SeedCatalog.courses().values()) {
            assertThat(c.clos()).as(c.code()).hasSizeBetween(3, 4);
            if (c.kind() == CourseKind.REGULAR) {
                assertThat(SeedCatalog.questionsFor(c.code())).as(c.code()).hasSize(6)
                        .allSatisfy(q -> assertThat(q.wrong()).hasSize(3).doesNotContain(q.correct()));
            }
        }
    }

    @Test
    @DisplayName("PLO mapping stays inside each program's PLO list")
    void ploMapping() {
        for (ProgramDef p : SeedCatalog.PROGRAMS) {
            p.semesters().forEach(sem -> sem.forEach(code -> {
                CourseDef c = SeedCatalog.course(code);
                for (int i = 0; i < c.clos().size(); i++) {
                    assertThat(DatabaseSeeder.ploIndex(p, c, i)).isBetween(0, p.plos().size() - 1);
                }
            }));
        }
    }

    @Test
    @DisplayName("grading bands are contiguous from 0 to 100 without overlap")
    void gradingScales() {
        for (GradingScaleDef scale : SeedCatalog.GRADING_SCALES) {
            List<GradeBand> bands = scale.bands();
            assertThat(bands.get(0).max()).isEqualTo(100);
            assertThat(bands.get(bands.size() - 1).min()).isZero();
            for (int i = 1; i < bands.size(); i++) {
                assertThat(bands.get(i).max()).as(scale.name()).isEqualTo(bands.get(i - 1).min());
                assertThat(bands.get(i).points()).isLessThan(bands.get(i - 1).points());
            }
        }
    }

    @Test
    @DisplayName("roll numbers follow YY110000CNNN")
    void rollNumbers() {
        assertThat(SeedCatalog.rollNumber(2024, 4, 2)).isEqualTo("241100004002");
        assertThat(SeedCatalog.rollNumber(2022, 1, 30)).matches("^\\d{2}110000\\d\\d{3}$");
    }

    @Test
    @DisplayName("terms run Spring 2022 – Fall 2026 inside the calendar years, batches admitted in Spring")
    void terms() {
        List<Term> terms = Term.all();
        assertThat(terms).hasSize(10);
        assertThat(terms.get(0).start()).isEqualTo(LocalDate.of(2022, 1, 8));
        assertThat(terms.get(9).end()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(terms).allSatisfy(t -> assertThat(t.closedAt().getYear()).isEqualTo(t.year()));
        // BS 2023 is in its 8th semester in Fall 2026; BS 2022 graduated in Fall 2025
        assertThat(terms.get(9).semesterNumberOf(2023)).isEqualTo(8);
        assertThat(terms.get(7).semesterNumberOf(2022)).isEqualTo(8);
    }
}
