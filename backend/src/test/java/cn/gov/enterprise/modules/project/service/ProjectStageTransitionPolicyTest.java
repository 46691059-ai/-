package cn.gov.enterprise.modules.project.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.entity.ProjectStageEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectStageMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectTaskMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectStageTransitionPolicyTest {
    @Mock ProjectStageMapper stageMapper;
    @Mock ProjectTaskMapper taskMapper;
    private ProjectStageTransitionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ProjectStageTransitionPolicy(stageMapper, taskMapper);
    }

    @Test
    void rejectsStartingWhenPreviousStageIsUnfinished() {
        when(stageMapper.selectCount(any())).thenReturn(1L);
        ProjectStageEntity stage = stage("IMPLEMENTATION", "NOT_STARTED", 3);

        assertThatThrownBy(() -> policy.validate(stage, request("IN_PROGRESS", "APPROVED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("前置阶段");
    }

    @Test
    void rejectsCompletingApprovalStageWithOpenTasks() {
        when(taskMapper.selectCount(any())).thenReturn(1L);
        ProjectStageEntity stage = stage("ACCEPTANCE", "IN_PROGRESS", 5);

        assertThatThrownBy(() -> policy.validate(stage, request("COMPLETED", "APPROVED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未完成任务");
    }

    private ProjectStageEntity stage(String code, String status, int order) {
        ProjectStageEntity stage = new ProjectStageEntity();
        stage.setId(10L);
        stage.setProjectId(1L);
        stage.setStageCode(code);
        stage.setStatus(status);
        stage.setStageOrder(order);
        return stage;
    }

    private ProjectDtos.StageUpdateRequest request(String status, String approval) {
        return new ProjectDtos.StageUpdateRequest(
                null, null, null, null, null, status, approval,
                BigDecimal.ZERO, null, 0);
    }
}
