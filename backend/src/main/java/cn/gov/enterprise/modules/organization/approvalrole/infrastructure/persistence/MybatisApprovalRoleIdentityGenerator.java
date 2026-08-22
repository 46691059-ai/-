package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence;

import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.ApprovalRoleIdentityGenerator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Component;

@Component
public class MybatisApprovalRoleIdentityGenerator implements ApprovalRoleIdentityGenerator {
    public long nextId(){return IdWorker.getId();}
}
