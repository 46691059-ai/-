package cn.gov.enterprise.modules.project.infrastructure.persistence;

import cn.gov.enterprise.modules.project.application.port.ProjectDetailQueryRepository;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleStage;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.entity.ProjectMemberEntity;
import cn.gov.enterprise.modules.project.entity.ProjectTaskEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectMemberMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectTaskMapper;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleAssembler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Read adapter that preserves the existing project detail response contract. */
@Repository
public class ProjectDetailQueryRepositoryImpl implements ProjectDetailQueryRepository {
    private static final long DETAIL_CHILD_LIMIT = 20;

    private final ProjectTaskMapper taskMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectLifecycleAssembler assembler;

    public ProjectDetailQueryRepositoryImpl(
            ProjectTaskMapper taskMapper,
            ProjectMemberMapper memberMapper,
            ProjectLifecycleAssembler assembler) {
        this.taskMapper = taskMapper;
        this.memberMapper = memberMapper;
        this.assembler = assembler;
    }

    @Override
    public ProjectDtos.DetailResponse findDetail(ProjectAggregate project) {
        Page<ProjectTaskEntity> tasks = taskMapper.selectPage(
                Page.of(1, DETAIL_CHILD_LIMIT),
                new LambdaQueryWrapper<ProjectTaskEntity>()
                        .eq(ProjectTaskEntity::getProjectId, project.getId())
                        .orderByAsc(ProjectTaskEntity::getSortNo)
                        .orderByAsc(ProjectTaskEntity::getCreateTime));
        Page<ProjectMemberEntity> members = memberMapper.selectPage(
                Page.of(1, DETAIL_CHILD_LIMIT),
                new LambdaQueryWrapper<ProjectMemberEntity>()
                        .eq(ProjectMemberEntity::getProjectId, project.getId())
                        .orderByAsc(ProjectMemberEntity::getCreateTime));
        return new ProjectDtos.DetailResponse(
                toResponse(project),
                project.getLifecycle().getStages().stream().map(this::toResponse).toList(),
                tasks.getRecords().stream().map(assembler::task).toList(),
                tasks.getTotal(),
                members.getRecords().stream().map(assembler::member).toList(),
                members.getTotal());
    }

    private ProjectDtos.Response toResponse(ProjectAggregate project) {
        return new ProjectDtos.Response(
                project.getId(), project.getProjectNo(), project.getProjectName(),
                project.getProjectType(), project.getProjectMode(), project.getSourceType(),
                project.getCustomerId(), project.getLeaderId(), project.getResponsibleOrgId(),
                project.getStatus(), project.getPlannedStartDate(), project.getPlannedEndDate(),
                project.getActualStartDate(), project.getActualEndDate(), project.getBudgetAmount(),
                project.getContractAmount(), project.getExpectedIncome(), project.getExpectedProfit(),
                project.getActualIncome(), project.getActualProfit(), project.getCurrentStageCode(),
                project.getRiskLevel(), project.getProgress(), project.getDescription(),
                project.getRemark(), project.getCreateTime(), project.getUpdateTime(),
                project.getAggregateVersion());
    }

    private ProjectDtos.StageResponse toResponse(LifecycleStage stage) {
        return new ProjectDtos.StageResponse(
                stage.getId(), stage.getProjectId(), stage.getSnapshot().getStageCode(),
                stage.getSnapshot().getStageName(), stage.getSnapshot().getStageOrder(),
                stage.getPlannedStartDate(), stage.getPlannedEndDate(), stage.getActualStartDate(),
                stage.getActualEndDate(), stage.getStatus(), stage.getResponsiblePersonId(),
                stage.getApprovalStatus(), stage.getCompletionPercent(), stage.getRemark(),
                stage.getVersion());
    }
}
