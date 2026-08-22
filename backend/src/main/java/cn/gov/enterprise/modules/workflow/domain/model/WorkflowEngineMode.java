package cn.gov.enterprise.modules.workflow.domain.model;

/** Frozen runtime semantics selected when a workflow version and instance are created. */
public enum WorkflowEngineMode {
    SINGLE_NODE_LEGACY,
    MULTI_NODE_LINEAR_V1
}
