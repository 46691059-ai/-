package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.modules.workflow.domain.claim.RealtimeEligibilityChecker;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaimContext;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Reads current personnel facts through a narrow port; no HR Entity leaks into Workflow. */
@Component
public class DatabaseRealtimeEligibilityChecker implements RealtimeEligibilityChecker {
    private final WorkflowClaimEligibilityMapper mapper;
    private final Clock clock;

    @Autowired
    public DatabaseRealtimeEligibilityChecker(WorkflowClaimEligibilityMapper mapper) {
        this(mapper, Clock.systemDefaultZone());
    }

    DatabaseRealtimeEligibilityChecker(WorkflowClaimEligibilityMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public EligibilityDecision check(TaskClaimContext context) {
        WorkflowClaimEligibilityMapper.EligibilityFacts facts =
                mapper.selectFacts(context.claimantUserId());
        if (facts == null) return denied("USER_NOT_FOUND", false, false, false, false);
        boolean user = Integer.valueOf(1).equals(facts.userStatus())
                && (facts.lockedUntil() == null
                    || !facts.lockedUntil().isAfter(LocalDateTime.now(clock)));
        boolean employment = facts.employeeId() != null
                && "ACTIVE".equalsIgnoreCase(facts.employeeStatus());
        boolean org = facts.orgId() != null && Integer.valueOf(1).equals(facts.orgStatus());
        boolean position = facts.positionId() != null
                && Integer.valueOf(1).equals(facts.positionStatus());
        boolean allowed = user && employment && org && position;
        String reason = !user ? "USER_INACTIVE" : !employment ? "EMPLOYMENT_INACTIVE"
                : !org ? "ORG_INACTIVE" : !position ? "POSITION_INACTIVE" : "ELIGIBLE";
        return new EligibilityDecision(allowed, user, employment, org, position, reason,
                "user=" + user + ",employment=" + employment
                        + ",org=" + org + ",position=" + position);
    }

    private EligibilityDecision denied(
            String reason, boolean user, boolean employment, boolean org, boolean position) {
        return new EligibilityDecision(false, user, employment, org, position, reason,
                "user=false,employment=false,org=false,position=false");
    }
}
