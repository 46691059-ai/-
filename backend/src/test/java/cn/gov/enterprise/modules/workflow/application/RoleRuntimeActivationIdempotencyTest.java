package cn.gov.enterprise.modules.workflow.application;
import static org.assertj.core.api.Assertions.assertThat;
import cn.gov.enterprise.modules.workflow.application.command.RoleRuntimeActivationResult;
import org.junit.jupiter.api.Test;
class RoleRuntimeActivationIdempotencyTest{
 @Test void identicalReplayIsAlreadyActiveAndDoesNotAppend(){var f=RoleRuntimeActivationTestSupport.fixture();var s=f.service();assertThat(s.activate(RoleRuntimeActivationTestSupport.command())).isEqualTo(RoleRuntimeActivationResult.ACTIVATED);assertThat(s.activate(RoleRuntimeActivationTestSupport.command())).isEqualTo(RoleRuntimeActivationResult.ALREADY_ACTIVE);assertThat(f.events().countByExactScope(RoleRuntimeActivationTestSupport.SCOPE)).isEqualTo(1);}
}
