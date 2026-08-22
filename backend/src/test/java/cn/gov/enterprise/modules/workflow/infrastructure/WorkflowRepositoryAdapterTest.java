package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowNode;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowDefinitionRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowNodeRepositoryImpl;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.WorkflowPersistenceAudit;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowDefinitionEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowNodeEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowNodeMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowRepositoryAdapterTest {
    @Test
    void definitionAdapterShouldMapDomainToV250Entity() {
        WorkflowDefinitionMapper mapper = org.mockito.Mockito.mock(WorkflowDefinitionMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(mapper.insert(any(WorkflowDefinitionEntity.class))).thenReturn(1);
        WorkflowDefinitionRepositoryImpl repository = new WorkflowDefinitionRepositoryImpl(mapper, audit);
        WorkflowDefinition definition = WorkflowDefinition.draft(
                1L, "INVESTMENT_DECISION", "投资决策", "INVESTMENT", 10L, 20L, "desc");

        repository.save(definition);

        ArgumentCaptor<WorkflowDefinitionEntity> captor = ArgumentCaptor.forClass(WorkflowDefinitionEntity.class);
        verify(mapper).insert(captor.capture());
        verify(audit).initialize(captor.getValue());
        assertThat(captor.getValue().getDefinitionCode()).isEqualTo("INVESTMENT_DECISION");
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void nodeReplacementShouldLogicalDeleteBeforeInsert() {
        WorkflowNodeMapper mapper = org.mockito.Mockito.mock(WorkflowNodeMapper.class);
        WorkflowPersistenceAudit audit = org.mockito.Mockito.mock(WorkflowPersistenceAudit.class);
        when(audit.operator()).thenReturn("admin");
        when(mapper.insert(any(WorkflowNodeEntity.class))).thenReturn(1);
        WorkflowNodeRepositoryImpl repository = new WorkflowNodeRepositoryImpl(mapper, audit);

        repository.replaceNodes(2L, java.util.List.of(node()));

        verify(mapper).logicalDeleteActiveByVersionId(2L, "admin");
        verify(mapper).insert(any(WorkflowNodeEntity.class));
    }

    private WorkflowNode node() {
        return new WorkflowNode(3L, 2L, "APPROVE", "审批", WorkflowNode.NodeType.APPROVAL, 1,
                WorkflowNode.GovernanceNodeType.GENERAL_APPROVAL, WorkflowNode.ApprovalMode.SINGLE,
                null, WorkflowNode.AssignmentRuleType.USER, "{}", null, null,
                null, true, true, 0);
    }
}
