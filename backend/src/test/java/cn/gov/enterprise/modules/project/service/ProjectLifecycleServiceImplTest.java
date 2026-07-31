package cn.gov.enterprise.modules.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.entity.ProjectMemberEntity;
import cn.gov.enterprise.modules.project.entity.ProjectStageEntity;
import cn.gov.enterprise.modules.project.entity.ProjectTaskEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectMemberMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectStageMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectTaskMapper;
import cn.gov.enterprise.modules.project.service.impl.ProjectLifecycleServiceImpl;
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import cn.gov.enterprise.security.CurrentSecurityContext;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectLifecycleServiceImplTest {
    @Mock ProjectMapper projectMapper;
    @Mock ProjectStageMapper stageMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ProjectAccessPolicy accessPolicy;
    @Mock CurrentSecurityContext securityContext;
    @Mock ProjectStageTransitionPolicy stageTransitionPolicy;
    @Mock ProjectReferenceValidator referenceValidator;
    @Mock ProjectTaskCommandService taskCommandService;
    @Mock ProjectMemberCommandService memberCommandService;

    private ProjectLifecycleServiceImpl service;
    private ProjectLifecycleAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ProjectLifecycleAssembler();
        ProjectLifecycleQueryService queryService = new ProjectLifecycleQueryService(
                projectMapper, stageMapper, taskMapper, memberMapper, accessPolicy, assembler);
        service = new ProjectLifecycleServiceImpl(
                projectMapper, stageMapper, taskMapper, memberMapper,
                accessPolicy, securityContext, stageTransitionPolicy, queryService, assembler,
                referenceValidator, taskCommandService, memberCommandService);
    }

    @Test
    void createInitializesSixStagesAndManagerMembership() {
        AtomicReference<ProjectEntity> insertedProject = new AtomicReference<>();
        List<ProjectStageEntity> insertedStages = new ArrayList<>();
        List<ProjectMemberEntity> insertedMembers = new ArrayList<>();
        when(projectMapper.selectCount(any())).thenReturn(0L);
        when(projectMapper.insert(any())).thenAnswer(invocation -> {
            ProjectEntity project = invocation.getArgument(0);
            project.setId(9001L);
            project.setVersion(0);
            insertedProject.set(project);
            return 1;
        });
        when(stageMapper.insert(any())).thenAnswer(invocation -> {
            ProjectStageEntity stage = invocation.getArgument(0);
            stage.setId(9100L + insertedStages.size());
            stage.setVersion(0);
            insertedStages.add(stage);
            return 1;
        });
        when(memberMapper.insert(any())).thenAnswer(invocation -> {
            ProjectMemberEntity member = invocation.getArgument(0);
            member.setId(9201L);
            member.setVersion(0);
            insertedMembers.add(member);
            return 1;
        });
        when(accessPolicy.requireAccessible(9001L)).thenAnswer(invocation -> insertedProject.get());
        when(stageMapper.selectList(any())).thenAnswer(invocation -> insertedStages);
        when(taskMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ProjectTaskEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });
        when(memberMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ProjectMemberEntity> page = invocation.getArgument(0);
            page.setRecords(insertedMembers);
            page.setTotal(insertedMembers.size());
            return page;
        });

        ProjectDtos.DetailResponse result = service.create(new ProjectDtos.CreateRequest(
                "PRJ-2026-001",
                "县域数据运营项目",
                "DIGITAL",
                "SELF_OPERATED",
                200L,
                100L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2027, 7, 31),
                new BigDecimal("5000000.00"),
                new BigDecimal("800000.00"),
                new BigDecimal("200000.00"),
                "MEDIUM",
                "项目说明"));

        assertThat(result.project().id()).isEqualTo(9001L);
        assertThat(result.stages()).extracting(ProjectDtos.StageResponse::stageCode)
                .containsExactly(
                        "RESERVE", "INITIATION", "IMPLEMENTATION",
                        "OPERATION", "EVALUATION", "ARCHIVE");
        assertThat(result.stages().getFirst().status()).isEqualTo("IN_PROGRESS");
        assertThat(result.members()).hasSize(1);
        assertThat(result.members().getFirst().role()).isEqualTo("MANAGER");
        assertThat(result.members().getFirst().employeeId()).isEqualTo(200L);
    }

    @Test
    void createRejectsReversedPlanDatesBeforeWriting() {
        ProjectDtos.CreateRequest request = new ProjectDtos.CreateRequest(
                "PRJ-2026-002",
                "日期错误项目",
                "DIGITAL",
                null,
                200L,
                100L,
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 1, 1),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "LOW",
                null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");
    }
}
