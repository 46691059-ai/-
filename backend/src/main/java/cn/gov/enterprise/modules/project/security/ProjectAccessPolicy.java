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
            query.eq(requestedOrgId != null, ProjectEntity::getOrgId, requestedOrgId);
            return;
        }
        if (requestedOrgId != null && !principal.allowedOrgIds().contains(requestedOrgId)) {
            throw new AccessDeniedException("无权访问该组织数据");
        }
        if (principal.allowedOrgIds().isEmpty()) {
            query.apply("1 = 0");
            return;
        }
        query.in(ProjectEntity::getOrgId, principal.allowedOrgIds());
        if (requestedOrgId != null) {
            query.eq(ProjectEntity::getOrgId, requestedOrgId);
        }
        if (principal.selfOnly()) {
            query.and(wrapper -> wrapper
                    .eq(ProjectEntity::getManagerUserId, principal.userId())
                    .or()
                    .apply("""
                        EXISTS (
                            SELECT 1 FROM pm_project_member member_scope
                            WHERE member_scope.project_id = pm_project.id
                              AND member_scope.user_id = {0}
                              AND member_scope.member_status = 'ACTIVE'
                              AND member_scope.deleted = 0
                        )
                        """, principal.userId()));
        }
    }

    private boolean canAccess(SecurityPrincipal principal, ProjectEntity project) {
        if (principal.allDataScope()) {
            return true;
        }
        if (!principal.allowedOrgIds().contains(project.getOrgId())) {
            return false;
        }
        if (!principal.selfOnly() || principal.userId().equals(project.getManagerUserId())) {
            return true;
        }
        return projectMapper.countActiveMember(project.getId(), principal.userId()) > 0;
    }
}
