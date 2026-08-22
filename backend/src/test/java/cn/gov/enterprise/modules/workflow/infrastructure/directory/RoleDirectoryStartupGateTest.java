package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverRegistry;
import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryResolver;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleDirectoryStartupGateTest {
    @Test
    void validFrameworkMayBeIntegrationReadyButNeverResolverEligible() {
        var properties = properties("test");
        var gate = new RoleDirectoryProductionStartupGate();
        var result = gate.evaluate(new RoleDirectoryProductionStartupGate.Input(properties,
                List.of(descriptor(properties, RoleDirectoryCapabilityDescriptor.AdapterType.PRODUCTION)),
                1, true, RoleDirectoryResolver.PREPARED_DESCRIPTOR));

        assertThat(result.status()).isEqualTo(
                RoleDirectoryCapabilityDescriptor.CapabilityStatus.READY_FOR_INTEGRATION_TEST);
        assertThat(result.resolverExecutionEligible()).isFalse();
        assertThat(RoleDirectoryResolver.PREPARED_DESCRIPTOR.enabled()).isFalse();
        assertThatThrownBy(() -> new ResolverRegistry(
                List.of(RoleDirectoryResolver.PREPARED_DESCRIPTOR), List.of())
                .require(RoleDirectoryResolver.CODE, RoleDirectoryResolver.VERSION,
                        RoleDirectoryResolver.CONTRACT_HASH)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void fakeLeakDuplicateAdapterAndDependencyViolationMustBlock() {
        var production = properties("production");
        var gate = new RoleDirectoryProductionStartupGate();
        var prod = descriptor(production, RoleDirectoryCapabilityDescriptor.AdapterType.PRODUCTION);
        var fake = descriptor(production, RoleDirectoryCapabilityDescriptor.AdapterType.FAKE);

        assertBlocked(gate.evaluate(input(production, List.of(prod, fake), 1, true)));
        assertBlocked(gate.evaluate(input(production, List.of(prod, prod), 1, true)));
        assertBlocked(gate.evaluate(input(production, List.of(prod), 2, true)));
        assertBlocked(gate.evaluate(input(production, List.of(prod), 1, false)));
    }

    @Test
    void typedConfigRejectsMissingOrUnsafeValues() {
        assertThatThrownBy(() -> new RoleDirectoryClientProperties(URI.create("file:///tmp/directory"),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 0, "production", "PROVIDER",
                RoleDirectoryResolver.PORT_CONTRACT, RoleDirectoryResolver.CONTRACT_HASH.value()))
                .isInstanceOf(DirectoryFailure.class);
        assertThatThrownBy(() -> new RoleDirectoryClientProperties(URI.create("https://directory.invalid"),
                Duration.ZERO, Duration.ofSeconds(1), 0, "production", "PROVIDER",
                RoleDirectoryResolver.PORT_CONTRACT, RoleDirectoryResolver.CONTRACT_HASH.value()))
                .isInstanceOf(DirectoryFailure.class);
        assertThatThrownBy(() -> new RoleDirectoryClientProperties(URI.create("https://directory.invalid"),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 99, "production", "PROVIDER",
                RoleDirectoryResolver.PORT_CONTRACT, RoleDirectoryResolver.CONTRACT_HASH.value()))
                .isInstanceOf(DirectoryFailure.class);
    }

    private static RoleDirectoryProductionStartupGate.Input input(RoleDirectoryClientProperties properties,
            List<RoleDirectoryCapabilityDescriptor> adapters, int clients, boolean dependencyPassed) {
        return new RoleDirectoryProductionStartupGate.Input(properties, adapters, clients,
                dependencyPassed, RoleDirectoryResolver.PREPARED_DESCRIPTOR);
    }

    private static RoleDirectoryCapabilityDescriptor descriptor(RoleDirectoryClientProperties properties,
            RoleDirectoryCapabilityDescriptor.AdapterType type) {
        return new RoleDirectoryCapabilityDescriptor(properties.providerCode(), "PROVIDER_V1",
                properties.expectedContractVersion(), properties.expectedContractHash(), type,
                properties.environmentIdentity(), RoleDirectoryCapabilityDescriptor.CapabilityStatus.PREPARED);
    }

    private static RoleDirectoryClientProperties properties(String environment) {
        return new RoleDirectoryClientProperties(URI.create("https://directory.invalid/roles"),
                Duration.ofMillis(100), Duration.ofMillis(200), 1, environment,
                "APPROVAL_ROLE_DIRECTORY", RoleDirectoryResolver.PORT_CONTRACT,
                RoleDirectoryResolver.CONTRACT_HASH.value());
    }

    private static void assertBlocked(RoleDirectoryProductionStartupGate.GateResult result) {
        assertThat(result.status()).isEqualTo(RoleDirectoryCapabilityDescriptor.CapabilityStatus.BLOCKED);
        assertThat(result.resolverExecutionEligible()).isFalse();
    }
}
