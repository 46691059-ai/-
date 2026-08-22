package cn.gov.enterprise.modules.organization.approvalrole.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApprovalRoleDomainTest {
    private static final Instant T0=Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1=Instant.parse("2026-06-01T00:00:00Z");

    @Test void roleCodeAndFixedTypesAreGoverned(){
        assertThat(new ApprovalRoleCode("TEST_APPROVER").value()).isEqualTo("TEST_APPROVER");
        assertThatThrownBy(()->new ApprovalRoleCode("test_approver")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ApprovalRole(1,"E1",new ApprovalRoleCode("TEST_APPROVER"),"Test","SYSTEM_ROLE","BUSINESS_ORG",ApprovalRoleStatus.ACTIVE,null,"u",T0,"u",T0,0,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ApprovalRole(1,"E1",new ApprovalRoleCode("TEST_APPROVER"),"Test",ApprovalRole.ROLE_TYPE,"GROUP",ApprovalRoleStatus.ACTIVE,null,"u",T0,"u",T0,0,0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void halfOpenEffectiveIntervalIsExact(){
        ApprovalRoleAssignment a=assignment(1,1,T0,T1,source("R1","a"));
        assertThat(a.effectiveAt(T0)).isTrue();
        assertThat(a.effectiveAt(T1)).isFalse();
        assertThat(a.effectiveAt(T0.minusMillis(1))).isFalse();
        assertThatThrownBy(()->assignment(1,1,T1,T1,source("R1","a"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void activeExpiredAndFutureAssignmentsAreDistinguished(){
        assertThat(assignment(1,1,T0,null,source("R1","a")).effectiveAt(T1)).isTrue();
        assertThat(assignment(2,2,T0,T1,source("R2","b")).effectiveAt(T1)).isFalse();
        assertThat(assignment(3,3,T1,null,source("R3","c")).effectiveAt(T0)).isFalse();
    }

    @Test void overlapUsesHalfOpenIntervals(){
        ApprovalRoleAssignment a=assignment(1,1,T0,T1,source("R1","a"));
        assertThat(a.overlaps(T1,null)).isFalse();
        assertThat(a.overlaps(T1.minusMillis(1),null)).isTrue();
    }

    @Test void oneUserMultipleSourcesAreRetainedAndOrderIndependent(){
        var e1=new ApprovalRoleDirectoryMember.Evidence(2,T0,null,source("G2","b"));
        var e2=new ApprovalRoleDirectoryMember.Evidence(1,T0,null,source("H1","a"));
        var m1=new ApprovalRoleDirectoryMember(7,List.of(e1,e2));
        var m2=new ApprovalRoleDirectoryMember(7,List.of(e2,e1));
        var r1=ApprovalRoleDirectoryResult.complete("E1",10,new ApprovalRoleCode("TEST_APPROVER"),T1,4,List.of(m1),T1);
        var r2=ApprovalRoleDirectoryResult.complete("E1",10,new ApprovalRoleCode("TEST_APPROVER"),T1,4,List.of(m2),T1.plusSeconds(9));
        assertThat(r1.resultHash()).isEqualTo(r2.resultHash());
        assertThat(r1.members().getFirst().evidence()).hasSize(2);
    }

    @Test void hashChangesWithMemberRevisionAndEffectiveAt(){
        var m=new ApprovalRoleDirectoryMember(7,List.of(new ApprovalRoleDirectoryMember.Evidence(1,T0,null,source("R","a"))));
        var base=ApprovalRoleDirectoryResult.complete("E1",10,new ApprovalRoleCode("TEST_APPROVER"),T1,4,List.of(m),T1);
        var user=ApprovalRoleDirectoryResult.complete("E1",10,new ApprovalRoleCode("TEST_APPROVER"),T1,4,List.of(new ApprovalRoleDirectoryMember(8,m.evidence())),T1);
        var revision=ApprovalRoleDirectoryResult.complete("E1",10,new ApprovalRoleCode("TEST_APPROVER"),T1,5,List.of(m),T1);
        var time=ApprovalRoleDirectoryResult.complete("E1",10,new ApprovalRoleCode("TEST_APPROVER"),T1.plusMillis(1),4,List.of(m),T1);
        assertThat(base.resultHash()).isNotEqualTo(user.resultHash()).isNotEqualTo(revision.resultHash()).isNotEqualTo(time.resultHash());
    }

    @Test void sourceConflictFailsClosed(){
        var policy=new ApprovalRoleDirectoryPolicy();
        var left=assignment(1,1,T0,null,source("SAME","a"));
        var right=assignment(2,2,T0,null,source("SAME","b"));
        assertThatThrownBy(()->policy.rejectSourceConflict(List.of(left,right)))
                .isInstanceOf(ApprovalRoleDirectoryFailure.class)
                .extracting(e->((ApprovalRoleDirectoryFailure)e).code())
                .isEqualTo(ApprovalRoleDirectoryFailureCode.DIRECTORY_SOURCE_CONFLICT);
    }

    @Test void endAndCorrectionAreGoverned(){
        var a=assignment(1,1,T0,null,source("R","a"));
        assertThat(a.end(T1,"owner",T1).status()).isEqualTo(ApprovalRoleAssignmentStatus.ENDED);
        assertThat(a.corrected("owner",T1).status()).isEqualTo(ApprovalRoleAssignmentStatus.CORRECTED);
        assertThatThrownBy(()->a.end(T0,"owner",T1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ApprovalRoleAssignment assignment(long id,long user,Instant from,Instant to,ApprovalRoleAssignmentSource source){return new ApprovalRoleAssignment(id,"E1",10,99,new ApprovalRoleCode("TEST_APPROVER"),user,from,to,ApprovalRoleAssignmentStatus.ACTIVE,source,ApprovalRoleCanonical.assignmentKey("E1",10,99,user,from,to,source),"owner",T0,"owner",T0,0,0);}
    private static ApprovalRoleAssignmentSource source(String ref,String hashSeed){return new ApprovalRoleAssignmentSource(ApprovalRoleAssignmentSourceType.GOVERNANCE_DECISION,"GOV",ref,ApprovalRoleCanonical.sha256(hashSeed),1,T0);}
}
