package cn.gov.enterprise.modules.workflow.domain.model;

/** Canonical publication hash algorithms. Historical values must never be recomputed. */
public enum WorkflowContentHashAlgorithm {
    NODE_V1_SHA256,
    GRAPH_V2_SHA256
}
