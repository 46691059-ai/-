package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RoleRuntimeProductionCapabilityBundleTest {
    private static final String H="a".repeat(64);
    private static final RealtimeCapabilityResult PASS=RealtimeCapabilityResult.pass("ok",H,"V1",Instant.parse("2030-01-01T00:00:00Z"));

    @Test void rejects_fake_from_unique_production_bundle(){
        var fake=new FakeDataScope();
        var bundle=new RealtimeEligibilityCapabilities.Bundle(
                q->new RealtimeRoleMembershipResult(RealtimeCapabilityResult.Outcome.PASS,true,false,"1",H,H,"ok",H,Instant.parse("2030-01-01T00:00:00Z")),
                q->new RealtimeUserStatusResult(RealtimeUserStatus.ACTIVE,"V1",H),q->PASS,fake,
                q->PASS,q->PASS,q->PASS,q->PASS,q->PASS,q->PASS);
        assertThatThrownBy(()->new RoleRuntimeProductionCapabilityBundle(bundle))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-production capability");
    }

    private static final class FakeDataScope implements RealtimeEligibilityCapabilities.RoleRuntimeDataScopePort {
        @Override public RealtimeCapabilityResult check(RealtimeEligibilityQuery query){return PASS;}
    }
}
