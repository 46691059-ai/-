package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverCode;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverVersion;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeApproval;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeBindingSnapshot;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimeEvidence;
import cn.gov.enterprise.modules.workflow.domain.role.RoleRuntimePersistencePolicy;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.RoleRuntimeBindingApprovalEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRuntimeBindingSnapshotEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.RoleRuntimeBindingApprovalMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleRuntimeBindingSnapshotMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class RoleRuntimePersistenceRepositoryTest {
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-13T02:03:04.005Z");

    @Test
    void approvalRepositoryMustInsertAndQueryWithoutUpdateApi() {
        RoleRuntimeBindingApprovalMapper mapper = mock(RoleRuntimeBindingApprovalMapper.class);
        WorkflowPersistenceAudit audit = mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(RoleRuntimeBindingApprovalEntity.class))).thenReturn(1);
        RoleRuntimeApprovalRepositoryImpl repository =
                new RoleRuntimeApprovalRepositoryImpl(mapper, audit);

        RoleRuntimeApproval approval = approval();
        repository.insert(approval);
        verify(mapper).insert(any(RoleRuntimeBindingApprovalEntity.class));

        RoleRuntimeBindingApprovalEntity entity = RoleRuntimePersistenceEntityMapper.toEntity(approval);
        when(mapper.selectById(approval.id())).thenReturn(entity);
        assertThat(repository.findById(approval.id())).contains(approval);
    }

    @Test
    void duplicateApprovalAndBindingMustFailClosed() {
        RoleRuntimeBindingApprovalMapper approvalMapper = mock(RoleRuntimeBindingApprovalMapper.class);
        when(approvalMapper.insert(any(RoleRuntimeBindingApprovalEntity.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        RoleRuntimeApprovalRepositoryImpl approvals = new RoleRuntimeApprovalRepositoryImpl(
                approvalMapper, mock(WorkflowPersistenceAudit.class));
        assertThatThrownBy(() -> approvals.insert(approval()))
                .isInstanceOf(BusinessException.class);

        WorkflowRoleRuntimeBindingSnapshotMapper snapshotMapper =
                mock(WorkflowRoleRuntimeBindingSnapshotMapper.class);
        when(snapshotMapper.insert(any(WorkflowRoleRuntimeBindingSnapshotEntity.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        RoleRuntimeBindingSnapshotRepositoryImpl snapshots =
                new RoleRuntimeBindingSnapshotRepositoryImpl(
                        snapshotMapper, mock(WorkflowPersistenceAudit.class));
        assertThatThrownBy(() -> snapshots.insert(snapshot()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void snapshotMappingMustRoundTripAllFrozenEvidence() {
        RoleRuntimeBindingSnapshot value = snapshot();
        WorkflowRoleRuntimeBindingSnapshotEntity entity =
                RoleRuntimePersistenceEntityMapper.toEntity(value);
        entity.setVersion(value.version());
        assertThat(RoleRuntimePersistenceEntityMapper.toDomain(entity)).isEqualTo(value);
    }

    private static RoleRuntimeApproval approval() {
        return RoleRuntimeApproval.pending(10L, HASH_B, HASH_C,
                ResolverCode.of("ROLE_DIRECTORY_V1"), ResolverVersion.of("1"),
                ResolverContractHash.of(HASH_A), "repository test evidence");
    }

    private static RoleRuntimeBindingSnapshot snapshot() {
        RoleRuntimeApproval approved = approval().approve("approver", NOW);
        RoleRuntimeEvidence evidence = new RoleRuntimeEvidence(
                12, HASH_B, HASH_C, "d".repeat(64), "e".repeat(64));
        return RoleRuntimePersistencePolicy.freeze(20L, approved, 30L, 31L, 32L,
                40L, 50L, 60L, "INVESTMENT_REVIEWER", "ORG-001", evidence,
                NOW, "repository runtime evidence");
    }
}
