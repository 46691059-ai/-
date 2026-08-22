package cn.gov.enterprise.modules.organization.approvalrole.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.gov.enterprise.modules.organization.approvalrole.domain.*;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApprovalRoleDirectoryApplicationTest {
    static final Instant JAN=Instant.parse("2026-01-01T00:00:00Z"),JUN=Instant.parse("2026-06-01T00:00:00Z"),MAY=Instant.parse("2026-05-01T00:00:00Z");
    ApprovalRoleRepository roles=mock(ApprovalRoleRepository.class); ApprovalRoleAssignmentRepository assignments=mock(ApprovalRoleAssignmentRepository.class); ApprovalRoleRevisionRepository revisions=mock(ApprovalRoleRevisionRepository.class); OrganizationUserDirectoryPort master=mock(OrganizationUserDirectoryPort.class); Clock clock=Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"),ZoneOffset.UTC); ApprovalRoleDirectoryService service;
    @BeforeEach void setup(){service=new ApprovalRoleDirectoryService(roles,assignments,revisions,master,clock);when(master.activeOrganization(10)).thenReturn(true);when(roles.findByKey(eq("E1"),any())).thenReturn(Optional.of(role(ApprovalRoleStatus.ACTIVE)));when(revisions.current(eq("E1"),eq(10L),any())).thenReturn(Optional.of(new ApprovalRoleRevisionHead(8,"E1",10,new ApprovalRoleCode("TEST_APPROVER"),3,"a".repeat(64),0)));}

    @Test void historicalHandoverUsesEffectiveAtNotCurrentMember(){
        when(assignments.findEffective(eq("E1"),eq(10L),any(),eq(MAY))).thenReturn(List.of(assignment(1,101,JAN,JUN,"A")));
        when(assignments.findEffective(eq("E1"),eq(10L),any(),eq(JUN))).thenReturn(List.of(assignment(2,102,JUN,null,"B")));
        assertThat(service.resolve(query(MAY)).members()).extracting(ApprovalRoleDirectoryMember::userId).containsExactly(101L);
        assertThat(service.resolve(query(JUN)).members()).extracting(ApprovalRoleDirectoryMember::userId).containsExactly(102L);
    }

    @Test void multipleUsersAndMultipleSourcesAreComplete(){
        when(assignments.findEffective(any(),anyLong(),any(),any())).thenReturn(List.of(assignment(1,101,JAN,null,"A"),assignment(2,101,JAN,null,"B"),assignment(3,102,JAN,null,"C")));
        var result=service.resolve(query(MAY));
        assertThat(result.complete()).isTrue();assertThat(result.members()).hasSize(2);
        assertThat(result.members().getFirst().evidence()).hasSize(2);assertThat(result.resultHash()).matches("[0-9a-f]{64}");
    }

    @Test void zeroMembersIsCompleteAndAuthoritative(){when(assignments.findEffective(any(),anyLong(),any(),any())).thenReturn(List.of());var r=service.resolve(query(MAY));assertThat(r.complete()).isTrue();assertThat(r.members()).isEmpty();}
    @Test void inactiveRoleFailsClosed(){when(roles.findByKey(eq("E1"),any())).thenReturn(Optional.of(role(ApprovalRoleStatus.INACTIVE)));assertThatThrownBy(()->service.resolve(query(MAY))).isInstanceOf(ApprovalRoleDirectoryFailure.class).extracting(e->((ApprovalRoleDirectoryFailure)e).code()).isEqualTo(ApprovalRoleDirectoryFailureCode.ROLE_INACTIVE);}
    @Test void inactiveOrganizationFailsClosed(){when(master.activeOrganization(10)).thenReturn(false);assertThatThrownBy(()->service.resolve(query(MAY))).isInstanceOf(ApprovalRoleDirectoryFailure.class).extracting(e->((ApprovalRoleDirectoryFailure)e).code()).isEqualTo(ApprovalRoleDirectoryFailureCode.ORGANIZATION_INACTIVE);}
    @Test void currentRevisionQueryUsesAggregateHead(){assertThat(service.getCurrentRevision("E1",10,"TEST_APPROVER")).isEqualTo(3);}

    private ApprovalRoleDirectoryQuery query(Instant at){return new ApprovalRoleDirectoryQuery("E1",10,new ApprovalRoleCode("TEST_APPROVER"),at);}
    private ApprovalRole role(ApprovalRoleStatus s){return new ApprovalRole(99,"E1",new ApprovalRoleCode("TEST_APPROVER"),"Test",ApprovalRole.ROLE_TYPE,ApprovalRole.SCOPE_TYPE,s,null,"owner",JAN,"owner",JAN,0,0);}
    private ApprovalRoleAssignment assignment(long id,long user,Instant from,Instant to,String ref){var source=new ApprovalRoleAssignmentSource(ApprovalRoleAssignmentSourceType.GOVERNANCE_DECISION,"GOV",ref,ApprovalRoleCanonical.sha256(ref),1,JAN);return new ApprovalRoleAssignment(id,"E1",10,99,new ApprovalRoleCode("TEST_APPROVER"),user,from,to,ApprovalRoleAssignmentStatus.ACTIVE,source,ApprovalRoleCanonical.assignmentKey("E1",10,99,user,from,to,source),"owner",JAN,"owner",JAN,0,0);}
}
