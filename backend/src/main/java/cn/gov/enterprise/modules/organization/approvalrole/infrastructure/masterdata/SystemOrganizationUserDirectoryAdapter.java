package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.masterdata;

import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.OrganizationUserDirectoryPort;
import cn.gov.enterprise.modules.system.mapper.SysOrgMapper;
import cn.gov.enterprise.modules.system.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

@Component
public class SystemOrganizationUserDirectoryAdapter implements OrganizationUserDirectoryPort {
    private final SysOrgMapper orgs; private final SysUserMapper users;
    public SystemOrganizationUserDirectoryAdapter(SysOrgMapper orgs,SysUserMapper users){this.orgs=orgs;this.users=users;}
    public boolean activeOrganization(long id){var e=orgs.selectById(id);return e!=null&&Integer.valueOf(1).equals(e.getStatus())&&Integer.valueOf(0).equals(e.getDeleted());}
    public boolean activeUser(long id){var e=users.selectById(id);return e!=null&&Integer.valueOf(1).equals(e.getStatus())&&Integer.valueOf(0).equals(e.getDeleted());}
}
