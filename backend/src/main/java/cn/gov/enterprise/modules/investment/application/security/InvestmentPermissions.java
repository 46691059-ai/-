package cn.gov.enterprise.modules.investment.application.security;

import java.util.Set;

/** Canonical RBAC authority names for the Investment module. */
public final class InvestmentPermissions {
    public static final String VIEW = "investment:view";
    public static final String CREATE = "investment:create";
    public static final String EDIT = "investment:edit";
    public static final String APPROVE = "investment:approve";
    public static final String DECISION = "investment:decision";
    public static final String OPPORTUNITY_CREATE = "investment:opportunity:create";
    public static final String OPPORTUNITY_REVIEW = "investment:opportunity:review";
    public static final String OPPORTUNITY_CONVERT = "investment:opportunity:convert";
    public static final String FEASIBILITY_VIEW = "investment:feasibility:view";
    public static final String FEASIBILITY_EDIT = "investment:feasibility:edit";
    public static final String DUE_DILIGENCE_VIEW = "investment:due_diligence:view";
    public static final String DUE_DILIGENCE_EDIT = "investment:due_diligence:edit";
    public static final String SCHEME_VIEW = "investment:scheme:view";
    public static final String SCHEME_EDIT = "investment:scheme:edit";
    public static final String DECISION_VIEW = "investment:decision:view";
    public static final String DECISION_CREATE = "investment:decision:create";
    public static final String DECISION_SUBMIT = "investment:decision:submit";
    public static final String DECISION_WITHDRAW = "investment:decision:withdraw";
    public static final String DECISION_APPROVE = "investment:decision:approve";
    public static final String DECISION_CONDITION = "investment:decision:condition";
    public static final String DECISION_ARCHIVE = "investment:decision:archive";

    public static final Set<String> ALL = Set.of(
            VIEW, CREATE, EDIT, APPROVE, DECISION,
            OPPORTUNITY_CREATE, OPPORTUNITY_REVIEW, OPPORTUNITY_CONVERT,
            FEASIBILITY_VIEW, FEASIBILITY_EDIT, DUE_DILIGENCE_VIEW, DUE_DILIGENCE_EDIT,
            SCHEME_VIEW, SCHEME_EDIT,
            DECISION_VIEW, DECISION_CREATE, DECISION_SUBMIT, DECISION_WITHDRAW,
            DECISION_APPROVE, DECISION_CONDITION, DECISION_ARCHIVE);

    private InvestmentPermissions() {
    }
}
