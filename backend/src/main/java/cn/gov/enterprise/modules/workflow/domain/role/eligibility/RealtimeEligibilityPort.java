package cn.gov.enterprise.modules.workflow.domain.role.eligibility;

@FunctionalInterface
public interface RealtimeEligibilityPort {
    RealtimeEligibilityResult assess(RealtimeEligibilityContext context);
}
