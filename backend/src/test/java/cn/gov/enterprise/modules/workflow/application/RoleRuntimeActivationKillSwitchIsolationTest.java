package cn.gov.enterprise.modules.workflow.application;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class RoleRuntimeActivationKillSwitchIsolationTest{
 @Test void changedKillSwitchFailsAndActivationCannotMutateIt(){var f=RoleRuntimeActivationTestSupport.fixture();assertThatThrownBy(()->f.service("ALLOW").activate(RoleRuntimeActivationTestSupport.command())).hasMessageContaining("Kill Switch drift");assertThat(f.events().countByExactScope(RoleRuntimeActivationTestSupport.SCOPE)).isZero();}
}
