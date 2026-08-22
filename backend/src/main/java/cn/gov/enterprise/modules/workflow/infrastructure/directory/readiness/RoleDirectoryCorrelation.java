package cn.gov.enterprise.modules.workflow.infrastructure.directory.readiness;

/** Directory-side trace references; no business payload or PII. */
public record RoleDirectoryCorrelation(
        String correlationId, String directoryRequestId, String workflowAdmissionId) {
    public RoleDirectoryCorrelation {
        correlationId = RoleDirectoryEndpointDescriptor.required(correlationId, "correlationId");
        directoryRequestId = RoleDirectoryEndpointDescriptor.required(directoryRequestId, "directoryRequestId");
        workflowAdmissionId = RoleDirectoryEndpointDescriptor.required(workflowAdmissionId, "workflowAdmissionId");
    }
}
