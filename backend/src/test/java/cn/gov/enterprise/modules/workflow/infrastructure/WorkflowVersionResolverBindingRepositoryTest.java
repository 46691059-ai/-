package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverMode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.binding.EffectiveTimePolicy;
import cn.gov.enterprise.modules.workflow.domain.binding.OrganizationScopeType;
import cn.gov.enterprise.modules.workflow.domain.binding.ResolverBindingManifest;
import cn.gov.enterprise.modules.workflow.domain.binding.VersionNodeResolverBinding;
import cn.gov.enterprise.modules.workflow.domain.repository.ResolverBindingManifestRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.ResolverBindingManifestRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.VersionNodeResolverBindingRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowPersistenceAudit;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionNodeResolverBindingEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionResolverBindingManifestEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowVersionNodeResolverBindingMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowVersionResolverBindingManifestMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowVersionResolverBindingRepositoryTest {
    @Test
    void bindingAdapterShouldPersistAndQueryInCanonicalOrder() throws Exception {
        WorkflowVersionNodeResolverBindingMapper mapper =
                org.mockito.Mockito.mock(WorkflowVersionNodeResolverBindingMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowVersionNodeResolverBindingEntity.class))).thenReturn(1);
        VersionNodeResolverBindingRepositoryImpl repository =
                new VersionNodeResolverBindingRepositoryImpl(mapper, audit);
        VersionNodeResolverBinding binding = binding();

        repository.add(binding);

        ArgumentCaptor<WorkflowVersionNodeResolverBindingEntity> captor =
                ArgumentCaptor.forClass(WorkflowVersionNodeResolverBindingEntity.class);
        verify(mapper).insert(captor.capture());
        verify(audit).initialize(captor.getValue());
        assertThat(captor.getValue().getResolverCode()).isEqualTo("ROLE_DIRECTORY");
        assertThat(captor.getValue().getOrganizationScopeType()).isEqualTo("FIXED_ORG");

        Select select = WorkflowVersionNodeResolverBindingMapper.class
                .getMethod("selectActiveByVersionId", Long.class).getAnnotation(Select.class);
        assertThat(String.join(" ", select.value()))
                .contains("ORDER BY node_id,binding_order")
                .doesNotContain("SELECT *");
    }

    @Test
    void manifestRepositoryMustBeAppendAndReadOnly() {
        WorkflowVersionResolverBindingManifestMapper mapper =
                org.mockito.Mockito.mock(WorkflowVersionResolverBindingManifestMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowVersionResolverBindingManifestEntity.class))).thenReturn(1);
        ResolverBindingManifestRepositoryImpl repository =
                new ResolverBindingManifestRepositoryImpl(mapper, audit);
        ResolverBindingManifest manifest = new ResolverBindingManifest(
                10L, 20L, 30L, ResolverBindingManifest.CANONICAL_VERSION,
                1, "c".repeat(64), 40L, LocalDateTime.now());

        repository.append(manifest);

        ArgumentCaptor<WorkflowVersionResolverBindingManifestEntity> captor =
                ArgumentCaptor.forClass(WorkflowVersionResolverBindingManifestEntity.class);
        verify(mapper).insert(captor.capture());
        verify(audit).initialize(captor.getValue());
        assertThat(captor.getValue().getManifestHash()).isEqualTo("c".repeat(64));
        assertThat(Arrays.stream(ResolverBindingManifestRepository.class.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName))
                .containsExactlyInAnyOrder("append", "findByDefinitionVersionId")
                .noneMatch(name -> name.toLowerCase().contains("update")
                        || name.toLowerCase().contains("delete")
                        || name.toLowerCase().contains("saveorupdate"));
    }

    @Test
    void bindingQueriesMustMapAllFrozenBusinessFields() {
        WorkflowVersionNodeResolverBindingMapper mapper =
                org.mockito.Mockito.mock(WorkflowVersionNodeResolverBindingMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        WorkflowVersionNodeResolverBindingEntity entity = entity(binding());
        when(mapper.selectActiveByVersionId(3L)).thenReturn(List.of(entity));
        VersionNodeResolverBindingRepositoryImpl repository =
                new VersionNodeResolverBindingRepositoryImpl(mapper, audit);

        VersionNodeResolverBinding result = repository.findByVersionId(3L).getFirst();

        assertThat(result.definitionId()).isEqualTo(2L);
        assertThat(result.nodeId()).isEqualTo(4L);
        assertThat(result.roleCode()).isEqualTo("ROLE_APPROVER");
        assertThat(result.bindingHash()).isEqualTo("b".repeat(64));
    }

    private VersionNodeResolverBinding binding() {
        return new VersionNodeResolverBinding(
                1L, 2L, 3L, 4L, 1, ResolverCode.of("ROLE_DIRECTORY"),
                ResolverVersion.of("ROLE_DIRECTORY_V1"), ResolverContractHash.of("a".repeat(64)),
                AssignmentStrategy.Type.ROLE, ResolverMode.CANDIDATE_POOL,
                AssignmentStrategy.Type.ROLE, "ROLE_APPROVER", OrganizationScopeType.FIXED_ORG,
                99L, EffectiveTimePolicy.NODE_ACTIVATED_AT,
                VersionNodeResolverBinding.SCHEMA_VERSION, "b".repeat(64), 0);
    }

    private WorkflowVersionNodeResolverBindingEntity entity(VersionNodeResolverBinding value) {
        WorkflowVersionNodeResolverBindingEntity entity = new WorkflowVersionNodeResolverBindingEntity();
        entity.setId(value.id());
        entity.setDefinitionId(value.definitionId());
        entity.setDefinitionVersionId(value.definitionVersionId());
        entity.setNodeId(value.nodeId());
        entity.setBindingOrder(value.bindingOrder());
        entity.setResolverCode(value.resolverCode().value());
        entity.setResolverVersion(value.resolverVersion().value());
        entity.setResolverContractHash(value.resolverContractHash().value());
        entity.setStrategyType(value.strategyType().name());
        entity.setResolverMode(value.resolverMode().name());
        entity.setTargetType(value.targetType().name());
        entity.setRoleCode(value.roleCode());
        entity.setOrganizationScopeType(value.organizationScopeType().name());
        entity.setOrganizationId(value.organizationId());
        entity.setEffectiveTimePolicy(value.effectiveTimePolicy().name());
        entity.setBindingSchemaVersion(value.bindingSchemaVersion());
        entity.setBindingHash(value.bindingHash());
        entity.setVersion(value.version());
        return entity;
    }
}
