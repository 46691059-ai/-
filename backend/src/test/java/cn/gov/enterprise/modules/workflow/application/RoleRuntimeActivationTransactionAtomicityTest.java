package cn.gov.enterprise.modules.workflow.application;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class RoleRuntimeActivationTransactionAtomicityTest{
 @Test void persistenceFailureLeavesDisabledDerivedState(){var f=RoleRuntimeActivationTestSupport.fixture();f.events().appendFailure=new IllegalStateException("simulated persistence failure");assertThatThrownBy(()->f.service().activate(RoleRuntimeActivationTestSupport.command())).hasMessageContaining("simulated");assertThat(f.events().countByExactScope(RoleRuntimeActivationTestSupport.SCOPE)).isZero();}
}
