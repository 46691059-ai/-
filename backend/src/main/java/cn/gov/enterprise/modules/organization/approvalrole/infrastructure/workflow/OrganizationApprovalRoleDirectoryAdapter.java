package cn.gov.enterprise.modules.organization.approvalrole.infrastructure.workflow;

import cn.gov.enterprise.modules.organization.approvalrole.application.ApprovalRoleDirectoryService;
import cn.gov.enterprise.modules.organization.approvalrole.domain.*;
import cn.gov.enterprise.modules.workflow.domain.role.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Internal contract adapter only. It is deliberately not a Spring component while ROLE runtime is disabled. */
public final class OrganizationApprovalRoleDirectoryAdapter implements RoleDirectoryPort {
    private final ApprovalRoleDirectoryService directory;
    public OrganizationApprovalRoleDirectoryAdapter(ApprovalRoleDirectoryService directory){this.directory=Objects.requireNonNull(directory);}

    @Override public RoleDirectoryResult resolve(RoleDirectoryQuery query){
        long organizationId;
        try { organizationId=Long.parseLong(query.organizationId()); }
        catch(NumberFormatException ex){throw new RoleDirectoryException(RoleDirectoryErrorCode.INVALID_DIRECTORY_QUERY,"organizationId must be a stable numeric sys_org key");}
        ApprovalRoleDirectoryResult internal=directory.resolve(new ApprovalRoleDirectoryQuery(query.enterpriseId(),organizationId,new ApprovalRoleCode(query.roleCode()),query.effectiveAt()));
        List<RoleDirectoryMember> members=new ArrayList<>();
        internal.members().forEach(member->member.evidence().forEach(evidence->members.add(new RoleDirectoryMember(
                Long.toString(member.userId()),Long.toString(evidence.assignmentId()),internal.roleCode().value(),
                Long.toString(internal.organizationId()),evidence.effectiveFrom(),evidence.effectiveTo(),
                RoleDirectorySourceType.valueOf(evidence.source().sourceType().workflowSourceType()),
                evidence.source().sourceReference(),internal.revision()))));
        RoleDirectoryResult result=new RoleDirectoryResult(internal.roleCode().value(),Long.toString(internal.organizationId()),
                internal.effectiveAt(),internal.revision(),internal.complete(),members,internal.resultHash(),
                internal.contractHash(),"ORGANIZATION_APPROVAL_ROLE_DIRECTORY_V1");
        if(!result.hasValidHash())throw new RoleDirectoryException(RoleDirectoryErrorCode.DIRECTORY_CONTRACT_MISMATCH,"organization canonical is incompatible with frozen Workflow canonical");
        return result;
    }
}
