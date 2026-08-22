package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import static cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeCapabilityResult.Outcome.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.gov.enterprise.modules.workflow.domain.role.eligibility.RealtimeEligibilityQuery;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowInstanceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowInstanceMapper;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowTaskActionMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformSoDGovernanceTest {
    private static final Instant NOW=Instant.parse("2026-08-21T00:00:00Z");
    private static final String HASH="a".repeat(64);

    @Test void threeVersionedRulesMustAllAllowAndVersionDriftMustFailClosed(){
        WorkflowInstanceMapper instances=mock(WorkflowInstanceMapper.class);
        WorkflowTaskActionMapper actions=mock(WorkflowTaskActionMapper.class);
        RoleRuntimeGovernanceControlStore controls=mock(RoleRuntimeGovernanceControlStore.class);
        WorkflowInstanceEntity instance=new WorkflowInstanceEntity(); instance.setId(1L); instance.setInitiatorUserId(7L);
        when(instances.selectById(1L)).thenReturn(instance);
        when(actions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(java.util.List.of());
        when(controls.latest(eq("PLATFORM_SOD"),anyString(),eq(NOW)))
                .thenReturn(Optional.of(control("ALLOW",1)));
        var adapter=new ProductionRealtimeCapabilityAdapters.PlatformSoD(instances,actions,controls);
        var initial=adapter.check(query());
        assertThat(initial.outcome()).as(initial.reason()).isEqualTo(PASS);

        when(controls.latest(eq("PLATFORM_SOD"),contains("GOVERNANCE_ADMIN_SELF_APPROVE"),eq(NOW)))
                .thenReturn(Optional.of(control("DENY",2)));
        assertThat(adapter.check(query()).outcome()).isEqualTo(DENY);

        when(controls.latest(eq("PLATFORM_SOD"),contains("GOVERNANCE_ADMIN_SELF_APPROVE"),eq(NOW)))
                .thenReturn(Optional.empty());
        assertThat(adapter.check(query()).outcome()).isEqualTo(INDETERMINATE);
    }

    private static RoleRuntimeGovernanceControlStore.Control control(String decision,long version){
        return new RoleRuntimeGovernanceControlStore.Control(decision,version,HASH,HASH,NOW.minusSeconds(1),NOW.plusSeconds(60));
    }
    private static RealtimeEligibilityQuery query(){
        return new RealtimeEligibilityQuery("E1",1,2,3,4,99,"GOVERNANCE_ADMIN","10",NOW,
                "R1",HASH,HASH,HASH,"DEF:1","corr-1");
    }
}
