package cn.gov.enterprise.modules.workflow.infrastructure.policy;

import cn.gov.enterprise.modules.workflow.domain.claim.SegregationOfDutiesPolicy;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaimContext;
import org.springframework.stereotype.Component;

/** Platform-level minimum SoD rule. Business-specific rules belong to their modules. */
@Component
public class DefaultWorkflowSoDPolicy implements SegregationOfDutiesPolicy {
    @Override
    public Decision evaluate(TaskClaimContext context) {
        boolean allowed = context.initiatorUserId() == null
                || !context.initiatorUserId().equals(context.claimantUserId());
        return new Decision(allowed, "WORKFLOW_BASIC_SOD", "V1",
                allowed ? "ALLOW" : "INITIATOR_CANNOT_CLAIM",
                allowed ? "claimant differs from initiator" : "claimant equals initiator");
    }
}
