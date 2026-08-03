package cn.gov.enterprise.modules.system.datascope.service.impl;

import cn.gov.enterprise.common.datascope.context.DataScopeType;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.datascope.dto.RoleDataScopeSaveRequest;
import cn.gov.enterprise.modules.system.datascope.mapper.DataScopeManagementMapper;
import cn.gov.enterprise.modules.system.datascope.service.DataScopeManagementService;
import cn.gov.enterprise.modules.system.datascope.vo.RoleDataScopeVO;
import cn.gov.enterprise.modules.system.entity.SysRoleEntity;
import cn.gov.enterprise.modules.system.entity.SysRoleOrgEntity;
import cn.gov.enterprise.modules.system.mapper.SysRoleMapper;
import cn.gov.enterprise.modules.system.mapper.SysRoleOrgMapper;
import cn.gov.enterprise.modules.system.org.service.OrgManagementService;
import cn.gov.enterprise.modules.system.org.vo.OrgTreeVO;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.PermissionCacheService;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DataScopeManagementServiceImpl implements DataScopeManagementService {
    private static final Set<String> SUPPORTED_SCOPES = Set.of(
            DataScopeType.ALL.name(),
            DataScopeType.ORG.name(),
            DataScopeType.ORG_AND_CHILDREN.name(),
            DataScopeType.SELF.name(),
            DataScopeType.CUSTOM.name());
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final SysRoleMapper roleMapper;
    private final SysRoleOrgMapper roleOrgMapper;
    private final DataScopeManagementMapper managementMapper;
    private final OrgManagementService orgManagementService;
    private final CurrentSecurityContext securityContext;
    private final PermissionCacheService permissionCacheService;

    public DataScopeManagementServiceImpl(
            SysRoleMapper roleMapper,
            SysRoleOrgMapper roleOrgMapper,
            DataScopeManagementMapper managementMapper,
            OrgManagementService orgManagementService,
            CurrentSecurityContext securityContext,
            PermissionCacheService permissionCacheService) {
        this.roleMapper = roleMapper;
        this.roleOrgMapper = roleOrgMapper;
        this.managementMapper = managementMapper;
        this.orgManagementService = orgManagementService;
        this.securityContext = securityContext;
        this.permissionCacheService = permissionCacheService;
    }

    @Override
    public RoleDataScopeVO roleDataScope(Long roleId) {
        SysRoleEntity role = requireRole(roleId);
        List<Long> orgIds = managementMapper.selectActiveRoleOrganizationIds(roleId);
        return new RoleDataScopeVO(role.getId(), role.getDataScopeType(), orgIds);
    }

    @Override
    @Transactional
    public void saveRoleDataScope(RoleDataScopeSaveRequest request) {
        SysRoleEntity role = requireRole(request.roleId());
        assertConfigurable(role);
        String scope = normalizeScope(request.dataScope());
        List<Long> requestedOrgIds = normalizeOrganizationIds(request.orgIds());
        if (DataScopeType.CUSTOM.name().equals(scope)) {
            if (requestedOrgIds.isEmpty()) {
                throw new BusinessException("B0409", "自定义数据范围至少需要选择一个组织");
            }
            validateOrganizations(requestedOrgIds);
        } else if (!requestedOrgIds.isEmpty()) {
            throw new BusinessException("B0409", "非自定义数据范围不能提交组织列表");
        }
        assertOperatorCanAssign(scope, requestedOrgIds);

        List<Long> currentOrgIds = managementMapper.selectActiveRoleOrganizationIds(role.getId());
        if (scope.equals(role.getDataScopeType()) && currentOrgIds.equals(requestedOrgIds)) {
            return;
        }

        managementMapper.logicallyDeleteRoleOrganizations(role.getId(), securityContext.username());
        if (DataScopeType.CUSTOM.name().equals(scope)) {
            for (Long orgId : requestedOrgIds) {
                SysRoleOrgEntity relation = new SysRoleOrgEntity();
                relation.setRoleId(role.getId());
                relation.setOrgId(orgId);
                roleOrgMapper.insert(relation);
            }
        }
        role.setDataScopeType(scope);
        if (roleMapper.updateById(role) != 1) {
            throw new BusinessException("B0409", "角色数据权限已被其他操作修改，请刷新后重试");
        }
        evictAffectedUsersAfterCommit(role.getId());
    }

    @Override
    public List<OrgTreeVO> selectableOrganizationTree() {
        return orgManagementService.tree();
    }

    private SysRoleEntity requireRole(Long roleId) {
        SysRoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("B0404", "角色不存在");
        }
        return role;
    }

    private void assertConfigurable(SysRoleEntity role) {
        if (SUPER_ADMIN.equals(role.getRoleCode())) {
            throw new BusinessException("B0409", "超级管理员数据范围不可修改");
        }
    }

    private String normalizeScope(String value) {
        String scope = value == null ? "" : value.trim().toUpperCase();
        if (!SUPPORTED_SCOPES.contains(scope)) {
            throw new BusinessException("B0409", "数据范围仅支持ALL、ORG、ORG_AND_CHILDREN、SELF、CUSTOM");
        }
        return scope;
    }

    private List<Long> normalizeOrganizationIds(List<Long> orgIds) {
        List<Long> normalized = new ArrayList<>(new LinkedHashSet<>(orgIds));
        normalized.sort(Comparator.naturalOrder());
        return List.copyOf(normalized);
    }

    private void validateOrganizations(List<Long> orgIds) {
        Set<Long> validIds = Set.copyOf(managementMapper.selectValidAdministrativeOrgIds(orgIds));
        if (validIds.size() != orgIds.size() || !validIds.containsAll(orgIds)) {
            throw new BusinessException("B0409", "自定义范围包含不存在、停用或非行政组织");
        }
    }

    private void assertOperatorCanAssign(String scope, List<Long> orgIds) {
        if (!DataScopeType.ALL.name().equals(scope)
                && !DataScopeType.CUSTOM.name().equals(scope)) {
            return;
        }
        SecurityPrincipal principal = securityContext.principal();
        if (DataScopeType.ALL.name().equals(scope) && !principal.allDataScope()) {
            throw new BusinessException("B0403", "当前用户无权授予全部数据范围");
        }
        if (DataScopeType.CUSTOM.name().equals(scope)
                && !principal.allDataScope()
                && !principal.allowedOrgIds().containsAll(orgIds)) {
            throw new BusinessException("B0403", "不能配置当前用户数据范围之外的组织");
        }
    }

    private void evictAffectedUsersAfterCommit(Long roleId) {
        List<Long> userIds = List.copyOf(managementMapper.selectUserIdsByRole(roleId));
        Runnable eviction = () -> userIds.forEach(permissionCacheService::evict);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eviction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eviction.run();
            }
        });
    }
}
