package cn.gov.enterprise.modules.project.application.service;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.domain.model.member.ProjectMember;
import cn.gov.enterprise.modules.project.domain.model.project.ProjectAggregate;
import cn.gov.enterprise.modules.project.domain.repository.ProjectIdentityGenerator;
import cn.gov.enterprise.modules.project.domain.repository.ProjectMemberRepository;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.service.ProjectReferenceValidator;
import cn.gov.enterprise.security.CurrentSecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application use cases for migrated member query, create and delete operations. */
@Service
public class ProjectMemberApplicationService {
    private final ProjectResourceAccessService resourceAccessService;
    private final ProjectMemberRepository repository;
    private final ProjectIdentityGenerator identityGenerator;
    private final ProjectReferenceValidator referenceValidator;
    private final CurrentSecurityContext securityContext;

    public ProjectMemberApplicationService(
            ProjectResourceAccessService resourceAccessService,
            ProjectMemberRepository repository,
            ProjectIdentityGenerator identityGenerator,
            ProjectReferenceValidator referenceValidator,
            CurrentSecurityContext securityContext) {
        this.resourceAccessService = resourceAccessService;
        this.repository = repository;
        this.identityGenerator = identityGenerator;
        this.referenceValidator = referenceValidator;
        this.securityContext = securityContext;
    }

    public PageResponse<ProjectDtos.MemberResponse> queryMembers(
            Long projectId, long page, long size) {
        resourceAccessService.requireAccessible(projectId);
        long normalizedPage = Math.max(1, page);
        long normalizedSize = Math.min(100, Math.max(1, size));
        long offset = (normalizedPage - 1) * normalizedSize;
        return new PageResponse<>(
                repository.findPage(projectId, offset, normalizedSize)
                        .stream().map(this::toResponse).toList(),
                repository.count(projectId), normalizedPage, normalizedSize);
    }

    @Transactional
    public ProjectDtos.MemberResponse addMember(
            Long projectId, ProjectDtos.MemberRequest request) {
        resourceAccessService.requireAccessible(projectId);
        validate(request);
        if (repository.existsMembership(projectId, request.employeeId(), request.role())) {
            throw new BusinessException("B0001", "该员工已具有相同项目角色");
        }
        ProjectMember member = new ProjectMember(
                identityGenerator.nextId(), projectId, request.employeeId(), request.role(),
                request.responsibilities(), request.joinedDate(), request.leftDate(),
                request.status(), request.remark(), 0);
        repository.insert(member);
        return toResponse(member);
    }

    @Transactional
    public void deleteMember(Long projectId, Long memberId) {
        ProjectAggregate project = resourceAccessService.requireAccessible(projectId);
        ProjectMember member = repository.findById(projectId, memberId)
                .orElseThrow(() -> new BusinessException("B0404", "项目成员不存在"));
        if (project.getLeaderId().equals(member.getEmployeeId())
                && "MANAGER".equals(member.getRole())) {
            throw new BusinessException("B0001", "项目负责人不能从成员中移除");
        }
        repository.softDelete(memberId, securityContext.username());
    }

    private void validate(ProjectDtos.MemberRequest request) {
        if (request.leftDate() != null && request.joinedDate().isAfter(request.leftDate())) {
            throw new BusinessException("B0001", "成员加入日期不能晚于退出日期");
        }
        referenceValidator.requireActiveEmployee(request.employeeId(), "项目成员");
    }

    private ProjectDtos.MemberResponse toResponse(ProjectMember member) {
        return new ProjectDtos.MemberResponse(
                member.getId(), member.getProjectId(), member.getEmployeeId(), member.getRole(),
                member.getResponsibilities(), member.getJoinedDate(), member.getLeftDate(),
                member.getStatus(), member.getRemark(), member.getAggregateVersion());
    }
}
