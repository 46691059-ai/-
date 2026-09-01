package cn.gov.enterprise.modules.workflow.domain;
import static org.assertj.core.api.Assertions.*;
import cn.gov.enterprise.modules.workflow.domain.role.*;
import org.junit.jupiter.api.Test;
class RoleRuntimeActivationDomainTest{
 @Test void runtimeLifecycleIsIndependentFromPersistentApprovalStatus(){assertThat(RoleRuntimeActivationState.values()).containsExactly(RoleRuntimeActivationState.DISABLED,RoleRuntimeActivationState.ACTIVATED);assertThat(java.util.Arrays.stream(PersistentActivationStatus.values()).map(Enum::name)).doesNotContain("ACTIVATED","ENABLED");}
}
