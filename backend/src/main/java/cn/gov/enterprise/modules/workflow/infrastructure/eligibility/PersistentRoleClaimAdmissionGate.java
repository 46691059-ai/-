package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.modules.workflow.domain.claim.RoleClaimAdmissionGate;
import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeExecutionAdmissionRepository;
import cn.gov.enterprise.modules.workflow.domain.role.admission.RoleRuntimeExecutionAdmissionStatus;
import org.springframework.stereotype.Component;

/** Fail-closed read of the active V2.6.15 admission slot. */
@Component
public final class PersistentRoleClaimAdmissionGate implements RoleClaimAdmissionGate {
    private final RoleRuntimeExecutionAdmissionRepository admissions;
    public PersistentRoleClaimAdmissionGate(RoleRuntimeExecutionAdmissionRepository admissions){this.admissions=admissions;}
    @Override public Decision verify(Facts f){
        var a=admissions.findActiveByBindingHash(f.runtimeBindingHash()).orElse(null);
        if(a==null)return Decision.deny("ADMISSION_NOT_FOUND");
        if(a.decision()!=RoleRuntimeExecutionAdmissionStatus.APPROVED_FOR_EXECUTION)
            return Decision.deny("ADMISSION_NOT_APPROVED");
        if(!f.now().isBefore(a.admissionExpiresAt()))return Decision.deny("ADMISSION_EXPIRED");
        long revision;
        try { revision=Long.parseLong(f.directoryRevision()); }
        catch(NumberFormatException ex){return Decision.deny("ADMISSION_DIRECTORY_REVISION_INVALID");}
        if(!a.definitionVersionId().equals(f.definitionVersionId())||!a.nodeId().equals(f.nodeId())
                ||!a.enterpriseId().equals(Long.toString(f.enterpriseId()))
                ||!a.resolverCode().equals(f.resolverCode())||!a.resolverVersion().equals(f.resolverVersion())
                ||!a.resolverContractHash().equals(f.resolverContractHash())
                ||a.directoryRevision()!=revision)
            return Decision.deny("ADMISSION_OWNERSHIP_OR_HASH_MISMATCH");
        return Decision.allow(a.admissionId(),a.executionAdmissionHash());
    }
}
