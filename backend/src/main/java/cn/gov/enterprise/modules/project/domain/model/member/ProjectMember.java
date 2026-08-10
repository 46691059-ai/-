package cn.gov.enterprise.modules.project.domain.model.member;

import java.time.LocalDate;

/** Immutable state of a project-scoped member sub-aggregate. */
public final class ProjectMember {
    private final Long id;
    private final Long projectId;
    private final Long employeeId;
    private final String role;
    private final String responsibilities;
    private final LocalDate joinedDate;
    private final LocalDate leftDate;
    private final String status;
    private final String remark;
    private final int aggregateVersion;

    public ProjectMember(
            Long id, Long projectId, Long employeeId, String role, String responsibilities,
            LocalDate joinedDate, LocalDate leftDate, String status, String remark,
            int aggregateVersion) {
        this.id = requirePositive(id, "Member id");
        this.projectId = requirePositive(projectId, "Project id");
        this.employeeId = requirePositive(employeeId, "Employee id");
        this.role = requireText(role, "Project member role");
        this.responsibilities = responsibilities;
        if (joinedDate == null) throw new IllegalArgumentException("Joined date must not be null");
        if (leftDate != null && joinedDate.isAfter(leftDate)) {
            throw new IllegalArgumentException("Joined date must not be after left date");
        }
        this.joinedDate = joinedDate;
        this.leftDate = leftDate;
        this.status = requireText(status, "Project member status");
        this.remark = remark;
        if (aggregateVersion < 0) throw new IllegalArgumentException("Member version must not be negative");
        this.aggregateVersion = aggregateVersion;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getEmployeeId() { return employeeId; }
    public String getRole() { return role; }
    public String getResponsibilities() { return responsibilities; }
    public LocalDate getJoinedDate() { return joinedDate; }
    public LocalDate getLeftDate() { return leftDate; }
    public String getStatus() { return status; }
    public String getRemark() { return remark; }
    public int getAggregateVersion() { return aggregateVersion; }

    private static Long requirePositive(Long value, String label) {
        if (value == null || value <= 0) throw new IllegalArgumentException(label + " must be positive");
        return value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value.trim();
    }
}
