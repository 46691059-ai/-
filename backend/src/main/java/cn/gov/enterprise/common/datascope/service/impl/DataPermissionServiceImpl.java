package cn.gov.enterprise.common.datascope.service.impl;

import cn.gov.enterprise.common.datascope.context.DataPermissionContext;
import cn.gov.enterprise.common.datascope.context.DataScopeType;
import cn.gov.enterprise.common.datascope.service.DataPermissionService;
import cn.gov.enterprise.modules.system.org.service.OrgDataScopeService;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.SecurityIdentityMapper;
import cn.gov.enterprise.security.SecurityPrincipal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DataPermissionServiceImpl implements DataPermissionService {
    private final CurrentSecurityContext securityContext;
    private final SecurityIdentityMapper identityMapper;
    private final OrgDataScopeService orgDataScopeService;

    public DataPermissionServiceImpl(
            CurrentSecurityContext securityContext,
            SecurityIdentityMapper identityMapper,
            OrgDataScopeService orgDataScopeService) {
        this.securityContext = securityContext;
        this.identityMapper = identityMapper;
        this.orgDataScopeService = orgDataScopeService;
    }

    @Override
    public DataPermissionContext current() {
        SecurityPrincipal principal = securityContext.principal();
        List<SecurityIdentityMapper.RoleScopeRow> rows = identityMapper.selectRoleScopes(principal.userId());
        Set<Long> roleIds = new LinkedHashSet<>();
        Set<DataScopeType> scopes = new LinkedHashSet<>();
        for (SecurityIdentityMapper.RoleScopeRow row : rows) {
            roleIds.add(row.roleId());
            scopes.add(DataScopeType.from(row.dataScopeType()));
        }
        if (scopes.isEmpty()) scopes.add(DataScopeType.SELF);

        DataScopeType effective = effectiveScope(scopes);
        Set<Long> orgIds = new LinkedHashSet<>();
        if (effective != DataScopeType.ALL) {
            if (scopes.contains(DataScopeType.ORG)) orgIds.add(principal.orgId());
            if (scopes.contains(DataScopeType.ORG_AND_CHILDREN)) {
                orgIds.addAll(orgDataScopeService.getChildrenOrgIds(principal.orgId()));
            }
            if (scopes.contains(DataScopeType.CUSTOM)) {
                orgIds.addAll(identityMapper.selectCustomOrgIds(principal.userId()));
            }
        }
        return new DataPermissionContext(principal.userId(), principal.username(), principal.orgId(),
                roleIds, effective, orgIds, scopes.contains(DataScopeType.SELF));
    }

    private DataScopeType effectiveScope(Set<DataScopeType> scopes) {
        if (scopes.contains(DataScopeType.ALL)) return DataScopeType.ALL;
        if (scopes.contains(DataScopeType.ORG_AND_CHILDREN)) return DataScopeType.ORG_AND_CHILDREN;
        if (scopes.contains(DataScopeType.CUSTOM)) return DataScopeType.CUSTOM;
        if (scopes.contains(DataScopeType.ORG)) return DataScopeType.ORG;
        return DataScopeType.SELF;
    }
}
