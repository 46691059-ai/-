package cn.gov.enterprise.modules.project.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.domain.model.task.ProjectTask;
import cn.gov.enterprise.modules.project.domain.repository.ProjectTaskRepository;
import cn.gov.enterprise.modules.project.entity.ProjectTaskEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis Plus task repository adapter. */
@Repository
public class ProjectTaskRepositoryImpl implements ProjectTaskRepository {
    private final ProjectTaskMapper mapper;

    public ProjectTaskRepositoryImpl(ProjectTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ProjectTask> findPage(Long projectId, Long stageId, long offset, long limit) {
        long pageNumber = offset / limit + 1;
        return mapper.selectPage(
                        Page.of(pageNumber, limit, false),
                        baseQuery(projectId, stageId)
                                .orderByAsc(ProjectTaskEntity::getSortNo)
                                .orderByAsc(ProjectTaskEntity::getCreateTime))
                .getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(Long projectId, Long stageId) {
        return mapper.selectCount(baseQuery(projectId, stageId));
    }

    @Override
    public Optional<ProjectTask> findById(Long projectId, Long taskId) {
        ProjectTaskEntity entity = mapper.selectOne(new LambdaQueryWrapper<ProjectTaskEntity>()
                .eq(ProjectTaskEntity::getProjectId, projectId)
                .eq(ProjectTaskEntity::getId, taskId));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public boolean existsTaskNo(Long projectId, String taskNo, Long excludedTaskId) {
        return mapper.selectCount(new LambdaQueryWrapper<ProjectTaskEntity>()
                .eq(ProjectTaskEntity::getProjectId, projectId)
                .eq(ProjectTaskEntity::getTaskNo, taskNo)
                .ne(excludedTaskId != null, ProjectTaskEntity::getId, excludedTaskId)) > 0;
    }

    @Override
    public void insert(ProjectTask task) {
        if (mapper.insert(toEntity(task)) != 1) {
            throw new BusinessException("B0500", "项目任务保存失败");
        }
    }

    @Override
    public void update(ProjectTask task) {
        ProjectTaskEntity entity = toEntity(task);
        entity.setVersion(task.getAggregateVersion());
        if (mapper.updateById(entity) != 1) {
            throw new BusinessException("B0001", "数据已被其他操作修改，请刷新后重试");
        }
    }

    private LambdaQueryWrapper<ProjectTaskEntity> baseQuery(Long projectId, Long stageId) {
        return new LambdaQueryWrapper<ProjectTaskEntity>()
                .eq(ProjectTaskEntity::getProjectId, projectId)
                .eq(stageId != null, ProjectTaskEntity::getStageId, stageId);
    }

    private ProjectTask toDomain(ProjectTaskEntity entity) {
        return new ProjectTask(
                entity.getId(), entity.getProjectId(), entity.getStageId(), entity.getParentTaskId(),
                entity.getTaskNo(), entity.getTaskName(), entity.getTaskContent(),
                entity.getResponsiblePerson(), entity.getPlanDate(), entity.getPlanStart(),
                entity.getPlanEnd(), entity.getActualDate(), entity.getActualStart(),
                entity.getActualEnd(), entity.getStatus(), entity.getPriority(),
                valueOrZero(entity.getProgress()), valueOrZero(entity.getSortNo()),
                entity.getRemark(), valueOrZero(entity.getVersion()));
    }

    private ProjectTaskEntity toEntity(ProjectTask task) {
        ProjectTaskEntity entity = new ProjectTaskEntity();
        entity.setId(task.getId());
        entity.setProjectId(task.getProjectId());
        entity.setStageId(task.getStageId());
        entity.setParentTaskId(task.getParentTaskId());
        entity.setTaskNo(task.getTaskNo());
        entity.setTaskName(task.getTaskName());
        entity.setTaskContent(task.getTaskContent());
        entity.setResponsiblePerson(task.getResponsiblePersonId());
        entity.setPlanDate(task.getPlanDate());
        entity.setPlanStart(task.getPlanStart());
        entity.setPlanEnd(task.getPlanEnd());
        entity.setActualDate(task.getActualDate());
        entity.setActualStart(task.getActualStart());
        entity.setActualEnd(task.getActualEnd());
        entity.setStatus(task.getStatus());
        entity.setPriority(task.getPriority());
        entity.setProgress(task.getProgress());
        entity.setSortNo(task.getSortNo());
        entity.setRemark(task.getRemark());
        return entity;
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
