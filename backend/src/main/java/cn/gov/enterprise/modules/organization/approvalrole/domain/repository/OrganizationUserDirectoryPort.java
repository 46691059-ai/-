package cn.gov.enterprise.modules.organization.approvalrole.domain.repository;

public interface OrganizationUserDirectoryPort {
    boolean activeOrganization(long organizationId);
    boolean activeUser(long userId);
}
