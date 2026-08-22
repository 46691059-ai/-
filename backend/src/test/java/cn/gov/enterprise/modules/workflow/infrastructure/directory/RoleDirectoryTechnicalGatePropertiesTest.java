package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RoleDirectoryTechnicalGatePropertiesTest {
    @Test void executionEligibleIsAuditableButNeverActive(){
        var gate=new RoleDirectoryTechnicalGateProperties(
                RoleDirectoryTechnicalGateProperties.Status.EXECUTION_ELIGIBLE,false);
        assertThat(gate.status()).isEqualTo(RoleDirectoryTechnicalGateProperties.Status.EXECUTION_ELIGIBLE);
        assertThat(gate.runtimeActive()).isFalse();
        assertThatThrownBy(()->new RoleDirectoryTechnicalGateProperties(
                RoleDirectoryTechnicalGateProperties.Status.EXECUTION_ELIGIBLE,true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
