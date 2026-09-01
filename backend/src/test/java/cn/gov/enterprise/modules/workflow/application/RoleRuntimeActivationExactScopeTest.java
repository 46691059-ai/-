package cn.gov.enterprise.modules.workflow.application;
import static org.assertj.core.api.Assertions.*;
import cn.gov.enterprise.modules.workflow.application.command.RoleRuntimeActivationCommand;
import cn.gov.enterprise.modules.workflow.domain.canary.CanaryScope;
import org.junit.jupiter.api.Test;
class RoleRuntimeActivationExactScopeTest{
 @Test void oneDimensionDriftHasNoFallback(){var f=RoleRuntimeActivationTestSupport.fixture();var c=RoleRuntimeActivationTestSupport.command();var drift=new RoleRuntimeActivationCommand(new CanaryScope(990001,990101,990401,990402,990405,c.scope().roleCode()),c.eventId(),c.authorizationId(),c.authorizationType(),c.authorizationCommit(),c.designCommit(),c.observationEvidenceCommit(),c.runtimeEnablementEvidenceCommit(),c.runtimeReleaseCommit(),c.runtimeReleaseTag(),c.approvalEvidenceHash(),c.directoryResultHash(),c.versionBindingHash(),c.manifestHash(),c.contentHash(),c.structuralFingerprint(),c.actorType(),c.actorId());assertThatThrownBy(()->f.service().activate(drift)).hasMessageContaining("Canary scope missing");}
}
