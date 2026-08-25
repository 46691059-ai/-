package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersionRelease;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowPersistenceAudit;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowVersionReleaseRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowVersionRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionReleaseEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowVersionMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowVersionReleaseMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowVersionV2621MappingTest {
    @Test
    void legacyVersionReadMustPreserveV2621Defaults() {
        WorkflowVersionMapper mapper = org.mockito.Mockito.mock(WorkflowVersionMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        WorkflowVersionEntity entity = legacyVersionEntity();
        when(mapper.selectById(2L)).thenReturn(entity);

        WorkflowVersion result = new WorkflowVersionRepositoryImpl(mapper, audit)
                .findById(2L).orElseThrow();

        assertThat(result.resolverBindingModel()).isEqualTo(ResolverBindingModel.LEGACY_USER_ONLY);
        assertThat(result.resolverBindingCount()).isZero();
        assertThat(result.resolverBindingManifestHash()).isNull();
        assertThat(result.resolverBindingCanonicalVersion()).isNull();
    }

    @Test
    void capableVersionSaveMustMapAllFourSnapshotFields() {
        WorkflowVersionMapper mapper = org.mockito.Mockito.mock(WorkflowVersionMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowVersionEntity.class))).thenReturn(1);
        LocalDateTime now = LocalDateTime.now();
        WorkflowVersion value = new WorkflowVersion(
                2L, 1L, 1, WorkflowVersion.Status.PUBLISHED, "2.0", "d".repeat(64),
                null, now, null, 7L, now, null, WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                "c".repeat(64), 1, "VERSION_RESOLVER_BINDING_MANIFEST_V1", 0);

        new WorkflowVersionRepositoryImpl(mapper, audit).save(value);

        ArgumentCaptor<WorkflowVersionEntity> captor = ArgumentCaptor.forClass(WorkflowVersionEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getResolverBindingModel())
                .isEqualTo("VERSION_RESOLVER_BINDING_CAPABLE");
        assertThat(captor.getValue().getResolverBindingManifestHash()).isEqualTo("c".repeat(64));
        assertThat(captor.getValue().getResolverBindingCount()).isEqualTo(1);
        assertThat(captor.getValue().getResolverBindingCanonicalVersion())
                .isEqualTo("VERSION_RESOLVER_BINDING_MANIFEST_V1");
    }

    @Test
    void publicationUpdatesMustPersistThePreparedAndPublishedBindingSnapshots() {
        WorkflowVersionMapper mapper = org.mockito.Mockito.mock(WorkflowVersionMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(audit.operator()).thenReturn("publisher");
        when(mapper.prepareResolverBindingSnapshot(
                eq(2L), eq("c".repeat(64)), eq(1),
                eq("VERSION_RESOLVER_BINDING_MANIFEST_V1"), eq("publisher"), eq(0)))
                .thenReturn(1);
        WorkflowVersion prepared = new WorkflowVersion(
                2L, 1L, 1, WorkflowVersion.Status.DRAFT, "2.0", null,
                null, null, null, null, null, null,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                "c".repeat(64), 1, "VERSION_RESOLVER_BINDING_MANIFEST_V1", 1);
        WorkflowVersionRepositoryImpl repository = new WorkflowVersionRepositoryImpl(mapper, audit);

        assertThat(repository.prepareResolverBindingSnapshot(prepared, 0)).isTrue();
        verify(mapper).prepareResolverBindingSnapshot(
                2L, "c".repeat(64), 1,
                "VERSION_RESOLVER_BINDING_MANIFEST_V1", "publisher", 0);
    }

    @Test
    void releaseRepositoryMustPersistAndReadItsOwnFrozenSnapshot() {
        WorkflowVersionReleaseMapper mapper = org.mockito.Mockito.mock(WorkflowVersionReleaseMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowVersionReleaseEntity.class))).thenReturn(1);
        WorkflowVersionRelease value = capableRelease();
        WorkflowVersionReleaseRepositoryImpl repository =
                new WorkflowVersionReleaseRepositoryImpl(mapper, audit);

        repository.save(value);

        ArgumentCaptor<WorkflowVersionReleaseEntity> captor =
                ArgumentCaptor.forClass(WorkflowVersionReleaseEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getResolverBindingModel())
                .isEqualTo("VERSION_RESOLVER_BINDING_CAPABLE");
        assertThat(captor.getValue().getResolverBindingManifestHash()).isEqualTo("c".repeat(64));

        when(mapper.selectByDefinitionIdAndPublishedVersionId(1L, 2L))
                .thenReturn(captor.getValue());
        WorkflowVersionRelease read = repository
                .findByDefinitionIdAndPublishedVersionId(1L, 2L).orElseThrow();
        assertThat(read.resolverBindingCount()).isEqualTo(1);
        assertThat(read.resolverBindingManifestHash()).isEqualTo("c".repeat(64));
    }

    private WorkflowVersionEntity legacyVersionEntity() {
        WorkflowVersionEntity entity = new WorkflowVersionEntity();
        entity.setId(2L);
        entity.setDefinitionId(1L);
        entity.setVersionNo(1);
        entity.setStatus("DRAFT");
        entity.setSchemaVersion("1.0");
        entity.setEngineMode("SINGLE_NODE_LEGACY");
        entity.setContentHashAlgorithm("NODE_V1_SHA256");
        entity.setResolverBindingModel("LEGACY_USER_ONLY");
        entity.setResolverBindingCount(0);
        entity.setVersion(0);
        return entity;
    }

    private WorkflowVersionRelease capableRelease() {
        return new WorkflowVersionRelease(
                10L, 1L, null, 2L, 1, "d".repeat(64), 7L, 8L,
                LocalDateTime.now(), "trace", "validated",
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256,
                ResolverBindingModel.VERSION_RESOLVER_BINDING_CAPABLE,
                "c".repeat(64), 1, "VERSION_RESOLVER_BINDING_MANIFEST_V1");
    }
}
