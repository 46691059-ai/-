package cn.gov.enterprise.modules.workflow.application;
import static org.assertj.core.api.Assertions.assertThat;
import cn.gov.enterprise.modules.workflow.application.command.RoleRuntimeActivationResult;
import org.junit.jupiter.api.Test;
class RoleRuntimeActivationApplicationServiceTest{
 @Test void appendsIndependentActivationEvent(){var f=RoleRuntimeActivationTestSupport.fixture();assertThat(f.service().activate(RoleRuntimeActivationTestSupport.command())).isEqualTo(RoleRuntimeActivationResult.ACTIVATED);assertThat(f.events().countByExactScope(RoleRuntimeActivationTestSupport.SCOPE)).isEqualTo(1);assertThat(f.events().value.get().resultingState().name()).isEqualTo("ACTIVATED");}
}
