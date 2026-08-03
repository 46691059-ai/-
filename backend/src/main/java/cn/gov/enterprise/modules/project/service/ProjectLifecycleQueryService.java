package cn.gov.enterprise.modules.project.service;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.datascope.annotation.DataScope;
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
import cn.gov.enterprise.modules.project.security.ProjectAccessPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProjectLifecycleQueryService {
    private final ProjectMapper projectMapper;
    private final ProjectStageMapper stageMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectAccessPolicy accessPolicy;
    private final ProjectLifecycleAssembler assembler;

    public ProjectLifecycleQueryService(
            ProjectMapper projectMapper,
            ProjectStageMapper stageMapper,
            ProjectTaskMapper taskMapper,
            ProjectMemberMapper memberMapper,
            ProjectAccessPolicy accessPolicy,
            ProjectLifecycleAssembler assembler) {
        this.projectMapper = projectMapper;
        this.stageMapper = stageMapper;
        this.taskMapper = taskMapper;
        this.memberMapper = memberMapper;
        this.accessPolicy = accessPolicy;
        this.assembler = assembler;
    }

    @DataScope(
            orgField = "project_info.department_id",
            userField = "project_info.create_by")
    public PageResponse<ProjectDtos.Response> page(
            long page, long size, String keyword, String status,
            String stageCode, Long departmentId) {
        String safeKeyword = escapeLikeKeyword(keyword);
        LambdaQueryWrapper<ProjectEntity> query = new LambdaQueryWrapper<ProjectEntity>()
                .and(StringUtils.hasText(safeKeyword), wrapper -> wrapper
                        .likeRight(ProjectEntity::getProjectNo, safeKeyword)
                        .or()
                        .likeRight(ProjectEntity::getProjectName, safeKeyword))
                .eq(StringUtils.hasText(status), ProjectEntity::getStatus, status)
                .eq(StringUtils.hasText(stageCode), ProjectEntity::getCurrentStageCode, stageCode);
        accessPolicy.applyScope(query, departmentId);
        query.orderByDesc(ProjectEntity::getCreateTime);
        Page<ProjectEntity> source = projectMapper.selectPage(
                Page.of(normalizePage(page), normalizeSize(size)), query);
        return new PageResponse<>(
                source.getRecords().stream().map(assembler::project).toList(),
                source.getTotal(), source.getCurrent(), source.getSize());
    }

    public ProjectDtos.DetailResponse detail(Long projectId) {
        ProjectEntity project = accessPolicy.requireAccessible(projectId);
        Page<ProjectTaskEntity> tasks = selectTaskPage(projectId, null, 1, 20);
        Page<ProjectMemberEntity> members = selectMemberPage(projectId, 1, 20);
        return new ProjectDtos.DetailResponse(
                assembler.project(project),
                selectStages(projectId).stream().map(assembler::stage).toList(),
                tasks.getRecords().stream().map(assembler::task).toList(),
                tasks.getTotal(),
                members.getRecords().stream().map(assembler::member).toList(),
                members.getTotal());
    }

    public List<ProjectDtos.StageResponse> stages(Long projectId) {
        accessPolicy.requireAccessible(projectId);
        return selectStages(projectId).stream().map(assembler::stage).toList();
    }

    public PageResponse<ProjectDtos.TaskResponse> tasks(
            Long projectId, Long stageId, long page, long size) {
        accessPolicy.requireAccessible(projectId);
        Page<ProjectTaskEntity> source = selectTaskPage(projectId, stageId, page, size);
        return new PageResponse<>(
                source.getRecords().stream().map(assembler::task).toList(),
                source.getTotal(), source.getCurrent(), source.getSize());
    }

    public PageResponse<ProjectDtos.MemberResponse> members(
            Long projectId, long page, long size) {
        accessPolicy.requireAccessible(projectId);
        Page<ProjectMemberEntity> source = selectMemberPage(projectId, page, size);
        return new PageResponse<>(
                source.getRecords().stream().map(assembler::member).toList(),
                source.getTotal(), source.getCurrent(), source.getSize());
    }

    private List<ProjectStageEntity> selectStages(Long projectId) {
        return stageMapper.selectList(new LambdaQueryWrapper<ProjectStageEntity>()
                .eq(ProjectStageEntity::getProjectId, projectId)
                .orderByAsc(ProjectStageEntity::getStageOrder));
    }

    private Page<ProjectTaskEntity> selectTaskPage(
            Long projectId, Long stageId, long page, long size) {
        return taskMapper.selectPage(
                Page.of(normalizePage(page), normalizeSize(size)),
                new LambdaQueryWrapper<ProjectTaskEntity>()
                        .eq(ProjectTaskEntity::getProjectId, projectId)
                        .eq(stageId != null, ProjectTaskEntity::getStageId, stageId)
                        .orderByAsc(ProjectTaskEntity::getSortNo)
                        .orderByAsc(ProjectTaskEntity::getCreateTime));
    }

    private Page<ProjectMemberEntity> selectMemberPage(
            Long projectId, long page, long size) {
        return memberMapper.selectPage(
                Page.of(normalizePage(page), normalizeSize(size)),
                new LambdaQueryWrapper<ProjectMemberEntity>()
                        .eq(ProjectMemberEntity::getProjectId, projectId)
                        .orderByAsc(ProjectMemberEntity::getCreateTime));
    }

    private long normalizePage(long page) {
        return Math.max(1, page);
    }

    private long normalizeSize(long size) {
        return Math.min(100, Math.max(1, size));
    }

    private String escapeLikeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new BusinessException("B0001", "检索关键词不能超过100个字符");
        }
        return normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
