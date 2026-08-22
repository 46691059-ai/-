package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverRegistryException;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverStatus;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import cn.gov.enterprise.modules.workflow.domain.role.RoleCandidateAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRoleResolverContractTest {
    private static final String CONTRACT_CANONICAL = "{\"canonicalSerialization\":\"ROLE_CANONICAL_JSON_V1\",\"completeSemantics\":\"ATOMIC_COMPLETE_ONLY\",\"dedupPolicy\":\"MERGE_BY_USER_ID\",\"effectiveAtSemantics\":\"NODE_ACTIVATED_AT_UTC_INSTANT\",\"errorSemantics\":\"FAIL_CLOSED_V1\",\"memberSchema\":\"ROLE_DIRECTORY_MEMBER_V1\",\"mode\":\"CANDIDATE_POOL\",\"organizationScope\":\"BUSINESS_ORG\",\"portContract\":\"ROLE_DIRECTORY_PORT_V1\",\"querySchema\":\"ROLE_DIRECTORY_QUERY_V1\",\"resolverCode\":\"ROLE_DIRECTORY\",\"resolverVersion\":\"ROLE_DIRECTORY_V1\",\"resultSchema\":\"ROLE_DIRECTORY_RESULT_V1\",\"revisionSemantics\":\"ENTERPRISE_ORG_ROLE_MONOTONIC\",\"roleType\":\"PROCESS_APPROVAL_ROLE\",\"schema\":\"RESOLVER_CONTRACT_HASH_V1\",\"strategyType\":\"ROLE\",\"timeoutFailureSemantics\":\"BOUNDED_RETRY_NO_FALLBACK\"}";

    @Test
    void frozenContractHashMustMatchCanonicalSchema() {
        assertThat(ResolverContractHash.sha256(CONTRACT_CANONICAL))
                .isEqualTo(RoleDirectoryResolver.CONTRACT_HASH);
    }

    @Test
    void roleDescriptorMustExistButRemainPreparedAndUnexecutable() {
        ResolverRegistry registry = new ResolverRegistry(
                List.of(new ExplicitUserResolver().descriptor(),
                        RoleDirectoryResolver.PREPARED_DESCRIPTOR,
                        RoleCandidateAdapter.PREPARED_DESCRIPTOR),
                List.of(new ExplicitUserResolver()));

        assertThat(registry.descriptors()).contains(
                RoleDirectoryResolver.PREPARED_DESCRIPTOR,
                RoleCandidateAdapter.PREPARED_DESCRIPTOR);
        assertThat(RoleDirectoryResolver.PREPARED_DESCRIPTOR.status())
                .isEqualTo(ResolverStatus.PREPARED);
        assertThat(RoleDirectoryResolver.PREPARED_DESCRIPTOR.enabled()).isFalse();
        assertThatThrownBy(() -> registry.require(RoleDirectoryResolver.CODE))
                .isInstanceOf(AssignmentResolverRegistryException.class)
                .extracting(error -> ((AssignmentResolverRegistryException) error).reason())
                .isEqualTo(AssignmentResolverRegistryException.Reason.DISABLED);
        assertThatThrownBy(() -> registry.require(RoleCandidateAdapter.CODE))
                .isInstanceOf(AssignmentResolverRegistryException.class)
                .extracting(error -> ((AssignmentResolverRegistryException) error).reason())
                .isEqualTo(AssignmentResolverRegistryException.Reason.DISABLED);
    }

    @Test
    void explicitUserResolverContractMustRemainActiveAndUnchanged() {
        ResolverRegistry registry = new ResolverRegistry(
                List.of(new ExplicitUserResolver().descriptor(),
                        RoleDirectoryResolver.PREPARED_DESCRIPTOR,
                        RoleCandidateAdapter.PREPARED_DESCRIPTOR),
                List.of(new ExplicitUserResolver()));
        assertThat(registry.require(ExplicitUserResolver.CODE).descriptor())
                .isEqualTo(new ExplicitUserResolver().descriptor());
    }
}
