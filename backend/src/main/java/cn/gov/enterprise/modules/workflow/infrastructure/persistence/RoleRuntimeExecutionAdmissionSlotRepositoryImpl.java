package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.repository.RoleRuntimeExecutionAdmissionSlotRepository;
import cn.gov.enterprise.modules.workflow.domain.role.admission.persistence.RoleRuntimeExecutionAdmissionSlot;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowRoleRuntimeExecutionAdmissionSlotEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.WorkflowRoleRuntimeExecutionAdmissionSlotMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRuntimeExecutionAdmissionSlotRepositoryImpl implements RoleRuntimeExecutionAdmissionSlotRepository {
    private final WorkflowRoleRuntimeExecutionAdmissionSlotMapper mapper;
    public RoleRuntimeExecutionAdmissionSlotRepositoryImpl(WorkflowRoleRuntimeExecutionAdmissionSlotMapper mapper){this.mapper=mapper;}
    @Override public void ensure(Long candidateId,String snapshotId){var e=new WorkflowRoleRuntimeExecutionAdmissionSlotEntity();e.setCandidateSnapshotRowId(candidateId);e.setSnapshotId(snapshotId);e.setSourceDeleteToken(0L);e.setActiveToken(token());e.setSlotStatus("VACANT");e.setVersion(0);e.setUpdatedBy("SYSTEM");e.setUpdatedTime(LocalDateTime.now());try{mapper.insert(e);}catch(DuplicateKeyException ignored){/* idempotent ensure */}}
    @Override public Optional<RoleRuntimeExecutionAdmissionSlot> lockByCandidateSnapshotRowId(Long id){return Optional.ofNullable(mapper.selectForUpdate(id)).map(RoleRuntimeExecutionAdmissionEntityMapper::toDomain);}
    @Override public boolean compareAndSetActive(Long candidateId,int expected,String token,Long admissionRowId,String admissionId,String operator){return mapper.occupy(candidateId,expected,token,admissionRowId,admissionId,operator)==1;}
    @Override public boolean compareAndSetVacant(Long candidateId,int expected,String token,String operator){return mapper.release(candidateId,expected,token,operator)==1;}
    private static String token(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return HexFormat.of().formatHex(bytes);}
}
