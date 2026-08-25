package cn.gov.enterprise.modules.workflow.domain.binding;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;

public final class VersionResolverBindingCanonicalFixtures {
    public static final String BINDING_ONE_CANONICAL =
            "32:VERSION_NODE_RESOLVER_BINDING_V1|2:10|2:20|2:30|1:1|"
                    + "14:ROLE_DIRECTORY|17:ROLE_DIRECTORY_V1|64:"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa|"
                    + "4:ROLE|14:CANDIDATE_POOL|4:ROLE|13:ROLE_APPROVER|9:FIXED_ORG|"
                    + "2:99|17:NODE_ACTIVATED_AT|";
    public static final String BINDING_ONE_HASH =
            "a7d5055376bcb4d39c47d312fd3fa6795b34e9ba37f263e8616416433f8b2949";
    public static final String BINDING_TWO_HASH =
            "85697c22a97898cc704a833ea915d59f422b1d243730ba802c62db7f6f9661cb";
    public static final String MANIFEST_CANONICAL =
            "36:VERSION_RESOLVER_BINDING_MANIFEST_V1|1:2|64:"
                    + BINDING_ONE_HASH + "|64:" + BINDING_TWO_HASH + "|";
    public static final String MANIFEST_HASH =
            "6dabc2572c7ef25fd81a4384b2817c76d4f5042ce21d95ea188327b85e26f45d";

    private VersionResolverBindingCanonicalFixtures() { }

    public static VersionNodeResolverBinding first() {
        return binding(9001L, 10L, 20L, 30L, 1, "a", "ROLE_APPROVER", 99L, 0,
                "0".repeat(64));
    }

    public static VersionNodeResolverBinding second() {
        return binding(9002L, 10L, 20L, 40L, 1, "b", "ROLE_REVIEWER", 100L, 0,
                "1".repeat(64));
    }

    public static VersionNodeResolverBinding binding(
            Long id, Long definitionId, Long versionId, Long nodeId, int order,
            String contractCharacter, String roleCode, Long organizationId,
            int optimisticVersion, String storedBindingHash) {
        return new VersionNodeResolverBinding(
                id, definitionId, versionId, nodeId, order,
                ResolverCode.of("ROLE_DIRECTORY"), ResolverVersion.of("ROLE_DIRECTORY_V1"),
                ResolverContractHash.of(contractCharacter.repeat(64)),
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                AssignmentStrategy.Type.ROLE, roleCode, OrganizationScopeType.FIXED_ORG,
                organizationId, EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION, storedBindingHash, optimisticVersion);
    }
}
