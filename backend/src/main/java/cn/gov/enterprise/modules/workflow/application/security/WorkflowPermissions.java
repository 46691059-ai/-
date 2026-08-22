package cn.gov.enterprise.modules.workflow.application.security;

import java.util.Set;

public final class WorkflowPermissions {
    public static final String MANAGE = "workflow:manage";
    public static final String DEFINITION_VIEW = "workflow:definition:view";
    public static final String DEFINITION_CREATE = "workflow:definition:create";
    public static final String DEFINITION_EDIT = "workflow:definition:edit";
    public static final String DEFINITION_PUBLISH = "workflow:definition:publish";
    public static final String VIEW = "workflow:view";
    public static final String START = "workflow:start";
    public static final String APPROVE = "workflow:approve";
    public static final String WITHDRAW = "workflow:withdraw";

    public static final Set<String> ALL = Set.of(
            MANAGE, DEFINITION_VIEW, DEFINITION_CREATE, DEFINITION_EDIT, DEFINITION_PUBLISH,
            VIEW, START, APPROVE, WITHDRAW);

    private WorkflowPermissions() {}
}
