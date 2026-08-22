package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.workflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.gov.enterprise.modules.organization.approvalrole.application.ApprovalRoleDirectoryService;
import cn.gov.enterprise.modules.organization.approvalrole.domain.*;
import cn.gov.enterprise.modules.workflow.domain.role.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApprovalRoleDirectoryContractCompatibilityTest {
    @Test void organizationResultExactlyMatchesFrozenWorkflowContract(){
        Instant at=Instant.parse("2026-05-01T00:00:00Z");var source=new ApprovalRoleAssignmentSource(ApprovalRoleAssignmentSourceType.GOVERNANCE_DECISION,"GOV","DEC-1",ApprovalRoleCanonical.sha256("DEC-1"),1,at);
        var internal=ApprovalRoleDirectoryResult.complete("E1",10,new ApprovalRoleCode("TEST_APPROVER"),at,7,List.of(new ApprovalRoleDirectoryMember(101,List.of(new ApprovalRoleDirectoryMember.Evidence(201,Instant.parse("2026-01-01T00:00:00Z"),null,source)))),at);
        ApprovalRoleDirectoryService service=mock(ApprovalRoleDirectoryService.class);when(service.resolve(any())).thenReturn(internal);
        RoleDirectoryResult result=new OrganizationApprovalRoleDirectoryAdapter(service).resolve(new RoleDirectoryQuery("E1","10","TEST_APPROVER",at,"ROLE_DIRECTORY_PORT_V1","trace"));
        assertThat(result.contractHash()).isEqualTo("5e8f9870effc7f886671f3cc5077a7bf0419cd9a6698dd6ca01e9de0c01f708d");
        assertThat(result.hasValidHash()).isTrue();assertThat(result.resultHash()).isEqualTo(internal.resultHash());assertThat(result.members()).hasSize(1);
    }
}
