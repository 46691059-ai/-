package cn.gov.enterprise.modules.organization.approvalrole.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import cn.gov.enterprise.modules.organization.approvalrole.domain.*;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class ApprovalRoleCommandServiceTest {
    private static final Instant NOW=Instant.parse("2026-08-01T00:00:00Z"),FROM=Instant.parse("2026-01-01T00:00:00Z");
    ApprovalRoleRepository roles=mock(ApprovalRoleRepository.class);ApprovalRoleAssignmentRepository assignments=mock(ApprovalRoleAssignmentRepository.class);ApprovalRoleRevisionRepository revisions=mock(ApprovalRoleRevisionRepository.class);OrganizationUserDirectoryPort master=mock(OrganizationUserDirectoryPort.class);ApprovalRoleDirectoryService directory=mock(ApprovalRoleDirectoryService.class);AtomicLong sequence=new AtomicLong(1000);ApprovalRoleCommandService service;
    @BeforeEach void setup(){ApprovalRole role=role();ApprovalRoleRevisionHead head=new ApprovalRoleRevisionHead(900,"E1",10,role.roleCode(),1,"a".repeat(64),0);when(roles.lockByKey(eq("E1"),any())).thenReturn(Optional.of(role));when(revisions.lockHead(eq("E1"),eq(10L),any())).thenReturn(Optional.of(head));when(master.activeOrganization(10)).thenReturn(true);when(master.activeUser(anyLong())).thenReturn(true);when(assignments.lockOverlapping(any(),anyLong(),anyLong(),anyLong(),any(),any(),any(),nullable(Instant.class))).thenReturn(List.of());when(directory.resolveAt(any(),eq(2L),any())).thenReturn(ApprovalRoleDirectoryResult.complete("E1",10,role.roleCode(),FROM,2,List.of(),NOW));when(revisions.advance(any(),eq(2L),any(),any())).thenReturn(true);service=new ApprovalRoleCommandService(roles,assignments,revisions,sequence::incrementAndGet,master,directory,Clock.fixed(NOW,ZoneOffset.UTC));}

    @Test void createRoleCreatesProcessApprovalRole(){var role=service.createRole(new ApprovalRoleCommandService.CreateRole("E1","TEST_FINANCE_REVIEWER","Finance",null,"owner"));assertThat(role.roleType()).isEqualTo(ApprovalRole.ROLE_TYPE);verify(roles).insert(role);}
    @Test void duplicateRoleFailsClosed(){doThrow(new DuplicateKeyException("duplicate")).when(roles).insert(any());assertThatThrownBy(()->service.createRole(new ApprovalRoleCommandService.CreateRole("E1","TEST_APPROVER","Test",null,"owner"))).isInstanceOf(ApprovalRoleDirectoryFailure.class);}
    @Test void assignOneUserPublishesOneRevision(){service.assignUser(assign(101,"R1"));verify(assignments).insert(any());verify(revisions,times(1)).append(any());verify(revisions,times(1)).advance(any(),eq(2L),any(),eq("owner"));}
    @Test void batchAssignmentPublishesOnlyOneRevision(){ApprovalRoleAssignmentSource source1=source("R1"),source2=source("R2");var command=new ApprovalRoleCommandService.BatchAssignUsers("E1",10,"TEST_APPROVER",List.of(new ApprovalRoleCommandService.UserAssignment(101,FROM,null,source1),new ApprovalRoleCommandService.UserAssignment(102,FROM,null,source2)),"owner");assertThat(service.assignUsers(command)).hasSize(2);verify(assignments,times(2)).insert(any());verify(revisions,times(1)).append(any());}
    @Test void overlapIsRejectedBeforeInsert(){when(assignments.lockOverlapping(any(),anyLong(),anyLong(),anyLong(),any(),any(),any(),nullable(Instant.class))).thenReturn(List.of(assignment(44,101,"R1")));assertThatThrownBy(()->service.assignUser(assign(101,"R1"))).isInstanceOf(ApprovalRoleDirectoryFailure.class).extracting(e->((ApprovalRoleDirectoryFailure)e).code()).isEqualTo(ApprovalRoleDirectoryFailureCode.ASSIGNMENT_OVERLAP);verify(assignments,never()).insert(any());}
    @Test void endAssignmentUsesOptimisticUpdateAndRevision(){var current=assignment(44,101,"R1");when(assignments.findByIdForUpdate(44)).thenReturn(Optional.of(current));when(assignments.update(any(),eq(0))).thenReturn(true);var ended=service.endAssignment(44,Instant.parse("2026-06-01T00:00:00Z"),"handover","owner");assertThat(ended.status()).isEqualTo(ApprovalRoleAssignmentStatus.ENDED);verify(revisions).append(any());}
    @Test void correctionPreservesOldEvidenceAndAddsReplacementInOneRevision(){var current=assignment(44,101,"R1");when(assignments.findByIdForUpdate(44)).thenReturn(Optional.of(current));when(assignments.update(any(),eq(0))).thenReturn(true);var corrected=service.correctAssignment(new ApprovalRoleCommandService.CorrectAssignment(44,102,FROM,Instant.parse("2026-07-01T00:00:00Z"),source("CORR"),"wrong user","DEC-CORR",FROM,null,"owner"));assertThat(corrected.userId()).isEqualTo(102);verify(assignments).update(argThat(a->a.status()==ApprovalRoleAssignmentStatus.CORRECTED),eq(0));verify(assignments).insert(any());verify(revisions,times(1)).append(argThat(r->r.changeType().equals("CORRECTION")));}

    private ApprovalRole role(){return ApprovalRole.active(99,"E1","TEST_APPROVER","Test",null,"owner",NOW);}
    private ApprovalRoleCommandService.AssignUser assign(long user,String ref){return new ApprovalRoleCommandService.AssignUser("E1",10,"TEST_APPROVER",user,FROM,null,source(ref),"owner");}
    private ApprovalRoleAssignmentSource source(String ref){return new ApprovalRoleAssignmentSource(ApprovalRoleAssignmentSourceType.GOVERNANCE_DECISION,"GOV",ref,ApprovalRoleCanonical.sha256(ref),1,NOW);}
    private ApprovalRoleAssignment assignment(long id,long user,String ref){var s=source(ref);return new ApprovalRoleAssignment(id,"E1",10,99,new ApprovalRoleCode("TEST_APPROVER"),user,FROM,null,ApprovalRoleAssignmentStatus.ACTIVE,s,ApprovalRoleCanonical.assignmentKey("E1",10,99,user,FROM,null,s),"owner",NOW,"owner",NOW,0,0);}
}
