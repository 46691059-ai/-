package cn.gov.enterprise.modules.organization.approvalrole.application;

import cn.gov.enterprise.modules.organization.approvalrole.domain.*;
import cn.gov.enterprise.modules.organization.approvalrole.domain.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalRoleCommandService {
    private final ApprovalRoleRepository roles;
    private final ApprovalRoleAssignmentRepository assignments;
    private final ApprovalRoleRevisionRepository revisions;
    private final ApprovalRoleIdentityGenerator ids;
    private final OrganizationUserDirectoryPort masterData;
    private final ApprovalRoleDirectoryService directory;
    private final ApprovalRoleRevisionPolicy revisionPolicy = new ApprovalRoleRevisionPolicy();
    private final Clock clock;

    @Autowired
    public ApprovalRoleCommandService(ApprovalRoleRepository roles,
            ApprovalRoleAssignmentRepository assignments, ApprovalRoleRevisionRepository revisions,
            ApprovalRoleIdentityGenerator ids, OrganizationUserDirectoryPort masterData,
            ApprovalRoleDirectoryService directory) {
        this(roles, assignments, revisions, ids, masterData, directory, Clock.systemUTC());
    }
    ApprovalRoleCommandService(ApprovalRoleRepository roles,
            ApprovalRoleAssignmentRepository assignments, ApprovalRoleRevisionRepository revisions,
            ApprovalRoleIdentityGenerator ids, OrganizationUserDirectoryPort masterData,
            ApprovalRoleDirectoryService directory, Clock clock) {
        this.roles=roles;this.assignments=assignments;this.revisions=revisions;this.ids=ids;
        this.masterData=masterData;this.directory=directory;this.clock=clock;
    }

    @Transactional
    public ApprovalRole createRole(CreateRole command) {
        Instant now=clock.instant(); ApprovalRole role=ApprovalRole.active(ids.nextId(), command.enterpriseId(),
                command.roleCode(),command.roleName(),command.description(),command.actor(),now);
        try { roles.insert(role); } catch (DuplicateKeyException ex) { throw failure(ApprovalRoleDirectoryFailureCode.DUPLICATE_ASSIGNMENT,"approval role already exists"); }
        return role;
    }

    @Transactional public ApprovalRole activateRole(String enterpriseId,String roleCode,String actor){return status(enterpriseId,roleCode,ApprovalRoleStatus.ACTIVE,actor);}
    @Transactional public ApprovalRole deactivateRole(String enterpriseId,String roleCode,String actor){return status(enterpriseId,roleCode,ApprovalRoleStatus.INACTIVE,actor);}

    @Transactional
    public ApprovalRoleAssignment assignUser(AssignUser command) {
        return assignUsers(new BatchAssignUsers(command.enterpriseId(),command.organizationId(),command.roleCode(),List.of(
                new UserAssignment(command.userId(),command.effectiveFrom(),command.effectiveTo(),command.source())),command.actor())).getFirst();
    }

    /** One governed business batch advances the aggregate revision exactly once. */
    @Transactional
    public List<ApprovalRoleAssignment> assignUsers(BatchAssignUsers command) {
        if(command.users()==null||command.users().isEmpty())throw new IllegalArgumentException("users must not be empty");
        ApprovalRole role=lockRole(command.enterpriseId(),command.roleCode());
        ApprovalRoleRevisionHead head=lockOrCreateHead(command.enterpriseId(),command.organizationId(),role.roleCode(),command.actor());
        java.util.ArrayList<ApprovalRoleAssignment> created=new java.util.ArrayList<>();Instant earliest=null;
        for(UserAssignment user:command.users()){
            validateMasterData(command.organizationId(),user.userId());AssignUser single=new AssignUser(command.enterpriseId(),command.organizationId(),command.roleCode(),user.userId(),user.effectiveFrom(),user.effectiveTo(),user.source(),command.actor());rejectOverlap(single,role.id());
            Instant now=clock.instant();String key=ApprovalRoleCanonical.assignmentKey(command.enterpriseId(),command.organizationId(),role.id(),user.userId(),user.effectiveFrom(),user.effectiveTo(),user.source());
            ApprovalRoleAssignment assignment=new ApprovalRoleAssignment(ids.nextId(),command.enterpriseId(),command.organizationId(),role.id(),role.roleCode(),user.userId(),user.effectiveFrom(),user.effectiveTo(),ApprovalRoleAssignmentStatus.ACTIVE,user.source(),key,command.actor(),now,command.actor(),now,0,0);
            try{assignments.insert(assignment);}catch(DuplicateKeyException ex){throw failure(ApprovalRoleDirectoryFailureCode.DUPLICATE_ASSIGNMENT,"duplicate assignment");}created.add(assignment);if(earliest==null||user.effectiveFrom().isBefore(earliest))earliest=user.effectiveFrom();
        }
        publish(head,created.size()==1?"ASSIGN":"BATCH","user assignment batch",earliest,null,null,null,command.actor());return List.copyOf(created);
    }

    @Transactional
    public ApprovalRoleAssignment endAssignment(long assignmentId, Instant effectiveTo, String reason, String actor) {
        ApprovalRoleAssignment current=assignments.findByIdForUpdate(assignmentId).orElseThrow();
        ApprovalRole role=lockRole(current.enterpriseId(),current.roleCode().value());
        ApprovalRoleRevisionHead head=lockOrCreateHead(current.enterpriseId(),current.organizationId(),role.roleCode(),actor);
        ApprovalRoleAssignment ended=current.end(effectiveTo,actor,clock.instant());
        if(!assignments.update(ended,current.version())) throw failure(ApprovalRoleDirectoryFailureCode.CONCURRENT_MODIFICATION,"assignment changed concurrently");
        publish(head,"END",reason,effectiveTo,null,null,null,actor); return ended;
    }

    @Transactional
    public ApprovalRoleAssignment correctAssignment(CorrectAssignment command) {
        ApprovalRoleAssignment current=assignments.findByIdForUpdate(command.assignmentId()).orElseThrow();
        ApprovalRole role=lockRole(current.enterpriseId(),current.roleCode().value());
        ApprovalRoleRevisionHead head=lockOrCreateHead(current.enterpriseId(),current.organizationId(),role.roleCode(),command.actor());
        ApprovalRoleAssignment corrected=current.corrected(command.actor(),clock.instant());
        if(!assignments.update(corrected,current.version())) throw failure(ApprovalRoleDirectoryFailureCode.CONCURRENT_MODIFICATION,"assignment changed concurrently");
        AssignUser replacement=new AssignUser(current.enterpriseId(),current.organizationId(),current.roleCode().value(),
                command.userId(),command.effectiveFrom(),command.effectiveTo(),command.source(),command.actor());
        rejectOverlap(replacement,role.id());
        String key=ApprovalRoleCanonical.assignmentKey(current.enterpriseId(),current.organizationId(),role.id(),
                command.userId(),command.effectiveFrom(),command.effectiveTo(),command.source());
        ApprovalRoleAssignment next=new ApprovalRoleAssignment(ids.nextId(),current.enterpriseId(),current.organizationId(),role.id(),
                role.roleCode(),command.userId(),command.effectiveFrom(),command.effectiveTo(),ApprovalRoleAssignmentStatus.ACTIVE,
                command.source(),key,command.actor(),clock.instant(),command.actor(),clock.instant(),0,0);
        assignments.insert(next);
        publish(head,"CORRECTION",command.reason(),command.effectiveFrom(),command.affectedFrom(),command.affectedTo(),command.reference(),command.actor());
        return next;
    }

    private ApprovalRole status(String enterpriseId,String roleCode,ApprovalRoleStatus status,String actor){
        ApprovalRole role=lockRole(enterpriseId,roleCode);ApprovalRole next=role.withStatus(status,actor,clock.instant());
        if(next!=role&&!roles.updateStatus(next,role.version()))throw failure(ApprovalRoleDirectoryFailureCode.CONCURRENT_MODIFICATION,"role changed concurrently");
        if(next!=role){for(ApprovalRoleRevisionHead snapshot:revisions.listByRole(enterpriseId,role.roleCode())){ApprovalRoleRevisionHead locked=revisions.lockHead(enterpriseId,snapshot.organizationId(),role.roleCode()).orElseThrow();publish(locked,status==ApprovalRoleStatus.ACTIVE?"ACTIVATE":"DEACTIVATE","approval role status changed",clock.instant(),null,null,null,actor);}}
        return next;
    }
    private ApprovalRole lockRole(String enterpriseId,String roleCode){return roles.lockByKey(enterpriseId,new ApprovalRoleCode(roleCode)).orElseThrow();}
    private void validateMasterData(long org,long user){if(!masterData.activeOrganization(org))throw failure(ApprovalRoleDirectoryFailureCode.ORGANIZATION_INACTIVE,"organization inactive");if(!masterData.activeUser(user))throw failure(ApprovalRoleDirectoryFailureCode.INVALID_QUERY,"user inactive");}
    private ApprovalRoleRevisionHead lockOrCreateHead(String enterprise,long org,ApprovalRoleCode code,String actor){
        return revisions.lockHead(enterprise,org,code).orElseGet(()->{revisions.insertHead(new ApprovalRoleRevisionHead(ids.nextId(),enterprise,org,code,0,null,0),actor);return revisions.lockHead(enterprise,org,code).orElseThrow();});
    }
    private void rejectOverlap(AssignUser c,long roleId){List<ApprovalRoleAssignment> overlaps=assignments.lockOverlapping(c.enterpriseId(),c.organizationId(),roleId,c.userId(),c.source().sourceSystem(),c.source().sourceReference(),c.effectiveFrom(),c.effectiveTo());if(!overlaps.isEmpty())throw failure(ApprovalRoleDirectoryFailureCode.ASSIGNMENT_OVERLAP,"assignment interval overlaps existing evidence");}
    private void publish(ApprovalRoleRevisionHead head,String type,String reason,Instant effectiveFrom,Instant affectedFrom,Instant affectedTo,String correctionReference,String actor){
        long next=revisionPolicy.next(head.currentRevision());ApprovalRoleDirectoryResult result=directory.resolveAt(new ApprovalRoleDirectoryQuery(head.enterpriseId(),head.organizationId(),head.roleCode(),effectiveFrom),next,clock.instant());
        ApprovalRoleRevision revision=new ApprovalRoleRevision(ids.nextId(),head.enterpriseId(),head.organizationId(),head.roleCode(),next,result.resultHash(),type,reason,effectiveFrom,affectedFrom,affectedTo,correctionReference,clock.instant(),actor,head.currentRevision()==0?null:head.currentRevision(),head.currentRevision()==0?null:head.currentResultHash(),clock.instant(),0);
        revisions.append(revision);if(!revisions.advance(head,next,result.resultHash(),actor))throw failure(ApprovalRoleDirectoryFailureCode.CONCURRENT_MODIFICATION,"revision changed concurrently");
    }
    private ApprovalRoleDirectoryFailure failure(ApprovalRoleDirectoryFailureCode c,String m){return new ApprovalRoleDirectoryFailure(c,m);}

    public record CreateRole(String enterpriseId,String roleCode,String roleName,String description,String actor){}
    public record AssignUser(String enterpriseId,long organizationId,String roleCode,long userId,Instant effectiveFrom,Instant effectiveTo,ApprovalRoleAssignmentSource source,String actor){}
    public record UserAssignment(long userId,Instant effectiveFrom,Instant effectiveTo,ApprovalRoleAssignmentSource source){}
    public record BatchAssignUsers(String enterpriseId,long organizationId,String roleCode,List<UserAssignment> users,String actor){}
    public record CorrectAssignment(long assignmentId,long userId,Instant effectiveFrom,Instant effectiveTo,ApprovalRoleAssignmentSource source,String reason,String reference,Instant affectedFrom,Instant affectedTo,String actor){}
}
