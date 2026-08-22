package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.ActivationEvidenceRepository;
import cn.gov.enterprise.modules.workflow.domain.role.ActivationEvidenceRecord;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.RoleRuntimeActivationEvidenceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.RoleRuntimeActivationEvidenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class ActivationEvidenceRepositoryImpl implements ActivationEvidenceRepository {
    private final RoleRuntimeActivationEvidenceMapper mapper; private final WorkflowPersistenceAudit audit;
    public ActivationEvidenceRepositoryImpl(RoleRuntimeActivationEvidenceMapper mapper, WorkflowPersistenceAudit audit) { this.mapper = mapper; this.audit = audit; }
    @Override public void insert(ActivationEvidenceRecord value) {
        RoleRuntimeActivationEvidenceEntity entity = ActivationPersistenceEntityMapper.toEntity(value); audit.initialize(entity);
        entity.setCreatedTime(java.time.LocalDateTime.ofInstant(value.createdTime(), java.time.ZoneOffset.UTC));
        try { if (mapper.insert(entity) != 1) throw new BusinessException("B26130", "activation evidence save failed"); }
        catch (DuplicateKeyException exception) { throw new BusinessException("B26139", "activation evidence already exists"); }
    }
    @Override public List<ActivationEvidenceRecord> findByActivationId(String activationId) {
        return mapper.selectList(new LambdaQueryWrapper<RoleRuntimeActivationEvidenceEntity>()
                .eq(RoleRuntimeActivationEvidenceEntity::getActivationId, activationId)
                .orderByAsc(RoleRuntimeActivationEvidenceEntity::getEvidenceType)).stream()
                .map(ActivationPersistenceEntityMapper::toDomain).toList();
    }
}
