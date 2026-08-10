package cn.gov.enterprise.modules.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleInstance;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleStage;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.StageSnapshot;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectAggregateModelTest {

    @Test
    void shouldCreateFrameworkIndependentAggregateWithImmutableStageSnapshotList() {
        List<LifecycleStage> sourceStages = new ArrayList<>();
        sourceStages.add(stage(100L, 11L, "RESERVE", 1, "40.0000"));
        sourceStages.add(stage(100L, 12L, "INITIATION", 2, "60.0000"));
        LifecycleInstance lifecycle = lifecycle(100L, sourceStages);

        ProjectAggregate project = new ProjectAggregate(
                100L,
                "PRJ-001",
                "Lifecycle aggregate pilot",
                "04",
                10L,
                20L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                "RESERVED",
                "RESERVE",
                BigDecimal.ZERO,
                lifecycle,
                0);

        sourceStages.clear();

        assertThat(project.getLifecycle().getStages()).hasSize(2);
        assertThat(project.getProjectNo()).isEqualTo("PRJ-001");
        assertThatThrownBy(() -> project.getLifecycle().getStages().add(
                        stage(100L, 13L, "ARCHIVE", 3, "0.0000")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectLifecycleOwnedByAnotherProject() {
        LifecycleInstance lifecycle = lifecycle(
                101L, List.of(stage(101L, 11L, "RESERVE", 1, "100.0000")));

        assertThatThrownBy(() -> new ProjectAggregate(
                        100L,
                        "PRJ-002",
                        "Invalid ownership",
                        "04",
                        10L,
                        20L,
                        null,
                        null,
                        "RESERVED",
                        "RESERVE",
                        BigDecimal.ZERO,
                        lifecycle,
                        0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lifecycle must belong");
    }

    @Test
    void shouldRejectDuplicateStageCodes() {
        List<LifecycleStage> stages = List.of(
                stage(100L, 11L, "RESERVE", 1, "50.0000"),
                stage(100L, 12L, "RESERVE", 2, "50.0000"));

        assertThatThrownBy(() -> lifecycle(100L, stages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate lifecycle stage code");
    }

    private static LifecycleInstance lifecycle(Long projectId, List<LifecycleStage> stages) {
        return new LifecycleInstance(
                1L,
                projectId,
                2L,
                3L,
                1,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                stages);
    }

    private static LifecycleStage stage(
            Long projectId, Long id, String code, int order, String weight) {
        StageSnapshot snapshot = new StageSnapshot(
                null,
                code,
                code,
                order,
                new BigDecimal(weight),
                true,
                false,
                false,
                null);
        return new LifecycleStage(
                id, projectId, snapshot, null, null, null, null,
                "NOT_STARTED", null, "NOT_SUBMITTED", BigDecimal.ZERO, null, 0);
    }
}
