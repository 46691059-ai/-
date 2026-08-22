package cn.gov.enterprise.modules.workflow.domain.role.admission;

public record RoleRuntimeCanaryScope(
        String enterpriseId, String definitionId, String definitionVersionId, String nodeId) {

    public RoleRuntimeCanaryScope {
        enterpriseId = RoleRuntimeExecutionAdmissionRequest.text(
                enterpriseId, "enterpriseId", 100);
        definitionId = RoleRuntimeExecutionAdmissionRequest.text(
                definitionId, "definitionId", 100);
        definitionVersionId = RoleRuntimeExecutionAdmissionRequest.text(
                definitionVersionId, "definitionVersionId", 100);
        nodeId = RoleRuntimeExecutionAdmissionRequest.text(nodeId, "nodeId", 100);
    }

    public String canonicalValue() {
        return enterpriseId + "|" + definitionId + "|" + definitionVersionId + "|" + nodeId;
    }
}
