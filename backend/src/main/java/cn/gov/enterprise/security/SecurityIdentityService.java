package cn.gov.enterprise.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SecurityIdentityService {
    private final SecurityIdentityMapper mapper;

    public SecurityIdentityService(SecurityIdentityMapper mapper) {
        this.mapper = mapper;
    }

    public SecuritySnapshot load(Long userId) {
        return loadFromDatabase(userId);
    }

    public void evict(Long userId) {
        // Identity is loaded from the database on every authenticated request.
    }

    private SecuritySnapshot loadFromDatabase(Long userId) {
        SecurityIdentityMapper.SecurityUserRow user = mapper.selectUser(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        if (!Integer.valueOf(1).equals(user.status())) {
            throw new DisabledException("用户已停用或锁定");
        }
        Set<String> permissions = Set.copyOf(mapper.selectPermissions(userId));
        List<String> scopeTypes = mapper.selectDataScopeTypes(userId);
        boolean all = scopeTypes.contains("ALL");
        boolean selfOnly = !all && !scopeTypes.isEmpty()
                && scopeTypes.stream().allMatch("SELF"::equals);
        Set<Long> orgIds = new HashSet<>();
        if (!all) {
            if (scopeTypes.contains("ORG") || scopeTypes.contains("SELF")) {
                orgIds.add(user.orgId());
            }
            if (scopeTypes.contains("ORG_AND_CHILDREN")) {
                orgIds.addAll(mapper.selectOrgAndChildren(user.orgId()));
            }
            if (scopeTypes.contains("CUSTOM")) {
                orgIds.addAll(mapper.selectCustomOrgIds(userId));
            }
        }
        return new SecuritySnapshot(
                user.id(), user.username(), user.orgId(),
                user.tokenVersion() == null ? 0 : user.tokenVersion(),
                permissions, Set.copyOf(orgIds), all, selfOnly);
    }
}
