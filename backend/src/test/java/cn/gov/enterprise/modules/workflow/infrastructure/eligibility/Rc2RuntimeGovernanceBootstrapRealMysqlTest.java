package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.application.service.*;
import cn.gov.enterprise.modules.workflow.domain.canary.*;
import cn.gov.enterprise.modules.workflow.domain.repository.CanaryGovernanceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import cn.gov.enterprise.security.SecurityPrincipal;

@SpringBootTest(properties = {
        "workflow.role-runtime.claim-enabled=false",
        "spring.data.redis.repositories.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "RC2_RUNTIME_GOVERNANCE_BOOTSTRAP",
        matches = "EXECUTE_S11_7P_B")
class Rc2RuntimeGovernanceBootstrapRealMysqlTest {
    private static final CanaryScope SCOPE=new CanaryScope(990001,990101,990401,990402,
            990404,"RC1_TEST_CANARY_APPROVER");

    @Autowired private CanaryGovernanceApplicationService service;
    @Autowired private CanaryGovernanceRepository repository;

    @BeforeEach void installAuditedOperator(){
        var principal=new SecurityPrincipal(990201L,
                "EXPLICIT_INTERACTIVE_ENABLEMENT_APPROVER",990101L,Set.of(990101L),
                true,false,0);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal,null,List.of()));
    }

    @AfterEach void clearAuditedOperator(){SecurityContextHolder.clearContext();}

    @Test void bootstrapFrozenApprovalAsExactPreEnableLedgerAndReplayIdempotently(){
        var evidence=new CanaryApprovalEvidence("1",2,
                "2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234",
                "5b473845390a2a4c69f28a7b9d63d538a0a371cea447e97ecd00707ddbb87c1c",
                "e76bc7f8ee3cca977d4363b6073106d66deffe2491d0290a569d46fa3bac57ed",
                "b2bc64b3c6cebbd1713b92074f5fdcf6862d94fb70338a6b2d07c76c61ef8ade",
                "workflow-v1.0.0-rc2.1","c5946d272e8eb88115671d66b46e8c8ec67b1477",
                "20253809be2aeb7c76fe36a7d37293b6ca8b77741998aead726893056044a5b9");
        var command=new CanaryGovernanceBootstrapCommand(SCOPE,evidence,
                "3fb1ec7236274aa3c44c6a907477c5ae3851c109",
                "3904efd7cd2a35736f46988b48170a7cd2813654",
                "5d6881d509a76be9c8a6874117539bd1e99400b5",
                "e986791bc9a2870916b851e03b07186ebac4449e",
                "d627c38af00eb6be5f2a8fda572679c62c4937c3",
                "269595532f17cc3db09880404ca11629d108fc6d",
                "EXPLICIT_INTERACTIVE_ENABLEMENT_APPROVER");

        assertThat(service.bootstrapApproved(command))
                .isEqualTo(CanaryGovernanceBootstrapResult.CREATED);
        assertThat(service.bootstrapApproved(command))
                .isEqualTo(CanaryGovernanceBootstrapResult.ALREADY_EXISTS);
        var current=repository.latest(SCOPE,Instant.now()).orElseThrow();
        assertThat(current.state()).isEqualTo(CanaryGovernanceState.APPROVED_NOT_ENABLED);
        assertThat(current.revision()).isEqualTo(2);
        assertThat(current.evidence()).isEqualTo(evidence);
        assertThat(current.reason()).isEqualTo(command.bindingReason());
        assertThat(current.enablementActor()).isNull();
        assertThat(current.enabledAt()).isNull();
    }
}
