package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.*;
import cn.gov.enterprise.modules.workflow.domain.canary.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CanaryGovernanceStateMachineTest {
    private static final Instant NOW=Instant.parse("2026-08-25T00:00:00Z");
    private static final String H="a".repeat(64);
    private static final CanaryScope SCOPE=new CanaryScope(990001,990101,990401,990402,990404,"RC1_TEST_CANARY_APPROVER");
    private static final CanaryApprovalEvidence EVIDENCE=new CanaryApprovalEvidence("1",2,H,H,H,H,
            "workflow-v1.0.0-rc2","740bee63e79a2744eb06ff693b17e7ed3fdf9375",H);

    @Test void approvalDoesNotEnableAndLegalLifecycleIsAppendOnly() {
        var proposed=CanaryGovernanceRecord.proposed(1,SCOPE,EVIDENCE,"propose",NOW);
        var approved=proposed.transition(2,CanaryGovernanceState.APPROVED_NOT_ENABLED,"approver","approve",NOW.plusSeconds(1));
        assertThat(approved.state()).isEqualTo(CanaryGovernanceState.APPROVED_NOT_ENABLED);
        assertThat(approved.enabledAt()).isNull();
        var enabled=approved.transition(3,CanaryGovernanceState.ENABLED,"release","enable",NOW.plusSeconds(2));
        var suspended=enabled.transition(4,CanaryGovernanceState.SUSPENDED,"release","suspend",NOW.plusSeconds(3));
        var resumed=suspended.transition(5,CanaryGovernanceState.ENABLED,"release","resume",NOW.plusSeconds(4));
        var revoked=resumed.transition(6,CanaryGovernanceState.REVOKED,"release","revoke",NOW.plusSeconds(5));
        assertThat(revoked.previousRecordId()).isEqualTo(5);
        assertThat(revoked.revision()).isEqualTo(6);
        assertThatThrownBy(()->revoked.transition(7,CanaryGovernanceState.ENABLED,"release","invalid",NOW.plusSeconds(6)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void illegalTransitionsFailClosed() {
        var proposed=CanaryGovernanceRecord.proposed(1,SCOPE,EVIDENCE,"propose",NOW);
        assertThatThrownBy(()->proposed.transition(2,CanaryGovernanceState.ENABLED,"actor","bad",NOW))
                .isInstanceOf(IllegalStateException.class);
        var approved=proposed.transition(2,CanaryGovernanceState.APPROVED_NOT_ENABLED,"actor","ok",NOW);
        assertThatThrownBy(()->approved.transition(3,CanaryGovernanceState.SUSPENDED,"actor","bad",NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void everyScopeDimensionIsRequiredAndCaseSensitive() {
        assertThatThrownBy(()->new CanaryScope(0,990101,990401,990402,990404,"ROLE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CanaryScope(990001,0,990401,990402,990404,"ROLE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CanaryScope(990001,990101,0,990402,990404,"ROLE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CanaryScope(990001,990101,990401,0,990404,"ROLE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CanaryScope(990001,990101,990401,990402,0,"ROLE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CanaryScope(990001,990101,990401,990402,990404,"role")).isInstanceOf(IllegalArgumentException.class);
    }
}
