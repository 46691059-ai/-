package cn.gov.enterprise.modules.workflow.domain.sandbox;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryPort;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryQuery;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResult;
import java.time.Instant;
import java.util.List;

/** Fixed, non-refreshing synthetic directory. It rejects every non-Sandbox query. */
public final class FakeRoleDirectoryAdapter implements RoleDirectoryPort {
    private final String roleCode;
    private final String organizationId;
    private final Instant effectiveAt;
    private final long revision;
    private final List<RoleDirectoryMember> members;

    public FakeRoleDirectoryAdapter(
            String roleCode, String organizationId, Instant effectiveAt,
            long revision, List<RoleDirectoryMember> members) {
        SandboxRoleRuntimeContext.requirePrefix(roleCode, "SANDBOX_ROLE_", "roleCode");
        SandboxRoleRuntimeContext.requirePrefix(
                organizationId, "SANDBOX_ORG_", "organizationId");
        if (effectiveAt == null || revision <= 0) {
            throw new IllegalArgumentException("fixed Sandbox time and revision are required");
        }
        this.members = List.copyOf(members);
        this.members.forEach(member -> {
            SandboxRoleRuntimeContext.requirePrefix(
                    member.userId(), "SANDBOX_USER_", "member.userId");
            if (!roleCode.equals(member.roleCode())
                    || !organizationId.equals(member.organizationId())) {
                throw new IllegalArgumentException("Fake member scope does not match Sandbox");
            }
        });
        this.roleCode = roleCode;
        this.organizationId = organizationId;
        this.effectiveAt = effectiveAt;
        this.revision = revision;
    }

    @Override
    public RoleDirectoryResult resolve(RoleDirectoryQuery query) {
        if (!roleCode.equals(query.roleCode())
                || !organizationId.equals(query.organizationId())
                || !effectiveAt.equals(query.effectiveAt())
                || !query.enterpriseId().startsWith("SANDBOX_ENTERPRISE_")) {
            throw new IllegalArgumentException("non-Sandbox or dynamic directory query is forbidden");
        }
        return RoleDirectoryResult.complete(roleCode, organizationId, effectiveAt,
                revision, members, RoleDirectoryResolver.CONTRACT_HASH.value(),
                "ROLE_RUNTIME_SANDBOX_FIXED_DIRECTORY");
    }
}
