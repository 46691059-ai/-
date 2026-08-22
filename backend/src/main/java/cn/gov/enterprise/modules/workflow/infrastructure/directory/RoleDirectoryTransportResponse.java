package cn.gov.enterprise.modules.workflow.infrastructure.directory;

import cn.gov.enterprise.modules.workflow.domain.role.RoleDirectoryMember;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/** Transport DTO. It carries provider metadata but contains no Workflow runtime behavior. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoleDirectoryTransportResponse(
        String enterpriseId, String organizationId, String roleCode, Instant effectiveAt,
        long revision, boolean complete, List<RoleDirectoryMember> members, String resultHash,
        String contractVersion, String contractHash, String providerVersion, Instant resolvedAt,
        String source) {
    public RoleDirectoryTransportResponse {
        members = List.copyOf(members == null ? List.of() : members);
    }
}
