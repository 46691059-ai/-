package cn.gov.enterprise.modules.project.security;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectMapper;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ProjectAccessPolicy {
    private final ProjectMapper projectMapper;
    private final CurrentSecurityContext securityContext;

    public ProjectAccessPolicy(
            ProjectMapper projectMapper,
            CurrentSecurityContext securityContext) {
        this.projectMapper = projectMapper;
        this.securityContext = securityContext;
    }

    public ProjectEntity requireAccessible(Long projectId) {
        ProjectEntity project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("B0404", "项目不存在");
        }
        SecurityPrincipal principal = securityContext.principal();
        if (!canAccess(principal, project)) {
            throw new AccessDeniedException("无权访问该项目");
        }
        return project;
    }

    public void requireOrgAccessible(Long orgId) {
        SecurityPrincipal principal = securityContext.principal();
        if (!principal.allDataScope() && !principal.allowedOrgIds().contains(orgId)) {
            throw new AccessDeniedException("无权访问该组织数据");
        }
    }

    public void applyScope(LambdaQueryWrapper<ProjectEntity> query, Long requestedOrgId) {
        SecurityPrincipal principal = securityContext.principal();
        if (principal.allDataScope()) {
            query.eq(requestedOrgId != null, ProjectEntity::getDepartmentId, requestedOrgId);
            return;
        }
        if (requestedOrgId != null && !principal.allowedOrgIds().contains(requestedOrgId)) {
            throw new AccessDeniedException("无权访问该组织数据");
        }
        if (principal.allowedOrgIds().isEmpty()) {
            query.apply("1 = 0");
            return;
        }
        query.in(ProjectEntity::getDepartmentId, principal.allowedOrgIds());
        if (requestedOrgId != null) {
            query.eq(ProjectEntity::getDepartmentId, requestedOrgId);
        }
        if (principal.selfOnly()) {
            query.apply("""
                EXISTS (
                    SELECT 1
                    FROM sys_user scope_user
                    WHERE scope_user.id = {0}
                      AND scope_user.deleted = 0
                      AND (
                        project_info.leader_id = scope_user.employee_id
                        OR EXISTS (
                            SELECT 1 FROM project_member scope_member
                            WHERE scope_member.project_id = project_info.id
                              AND scope_member.employee_id = scope_user.employee_id
                              AND scope_member.status = 'ACTIVE'
                              AND scope_member.deleted = 0
                        )
                      )
                )
                """, principal.userId());
        }
    }

    private boolean canAccess(SecurityPrincipal principal, ProjectEntity project) {
        if (principal.allDataScope()) {
            return true;
        }
        if (!principal.allowedOrgIds().contains(project.getDepartmentId())) {
            return false;
        }
        return !principal.selfOnly()
                || projectMapper.countSelfAccessible(project.getId(), principal.userId()) > 0;
    }
}
