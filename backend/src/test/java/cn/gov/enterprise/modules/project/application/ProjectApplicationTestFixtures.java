package cn.gov.enterprise.modules.project.application;

import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleInstance;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleStage;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.StageSnapshot;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import java.math.BigDecimal;
import java.util.List;

final class ProjectApplicationTestFixtures {
    private ProjectApplicationTestFixtures() {
    }

    static ProjectAggregate project(Long projectId) {
        LifecycleStage implementation = stage(projectId + 11, projectId, "IMPLEMENTATION", "实施", 2);
        LifecycleStage reserve = stage(projectId + 10, projectId, "RESERVE", "储备", 1);
        LifecycleInstance lifecycle = LifecycleInstance.legacy(
                projectId + 20, projectId, List.of(implementation, reserve));
        return ProjectAggregate.create(
                projectId, "PRJ-" + projectId, "测试项目", "04", null,
                20L, 10L, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "LOW", null, lifecycle);
    }

    private static LifecycleStage stage(
            Long stageId, Long projectId, String code, String name, int order) {
        StageSnapshot snapshot = new StageSnapshot(
                1000L + order, code, name, order, new BigDecimal("50.0000"),
                true, false, false, null);
        return new LifecycleStage(
                stageId, projectId, snapshot, null, null, null, null,
                order == 1 ? "IN_PROGRESS" : "NOT_STARTED", 20L,
                "NOT_SUBMITTED", BigDecimal.ZERO, null, 0);
    }
}
