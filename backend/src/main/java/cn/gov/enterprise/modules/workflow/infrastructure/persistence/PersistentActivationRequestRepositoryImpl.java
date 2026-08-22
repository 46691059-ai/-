package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.repository.PersistentActivationRequestRepository;
import cn.gov.enterprise.modules.workflow.domain.role.PersistentActivationRequest;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.RoleRuntimeActivationRequestEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.RoleRuntimeActivationRequestMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class PersistentActivationRequestRepositoryImpl implements PersistentActivationRequestRepository {
    private final RoleRuntimeActivationRequestMapper mapper;
    private final WorkflowPersistenceAudit audit;
    public PersistentActivationRequestRepositoryImpl(RoleRuntimeActivationRequestMapper mapper, WorkflowPersistenceAudit audit) {
        this.mapper = mapper; this.audit = audit;
    }
    @Override public void insert(PersistentActivationRequest value) {
        RoleRuntimeActivationRequestEntity entity = ActivationPersistenceEntityMapper.toEntity(value);
        audit.initialize(entity);
        try { if (mapper.insert(entity) != 1) throw new BusinessException("B26130", "activation request save failed"); }
        catch (DuplicateKeyException exception) { throw new BusinessException("B26139", "activation already exists"); }
    }
    @Override public Optional<PersistentActivationRequest> findByActivationId(String activationId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<RoleRuntimeActivationRequestEntity>()
                .eq(RoleRuntimeActivationRequestEntity::getActivationId, activationId)))
                .map(ActivationPersistenceEntityMapper::toDomain);
    }
}
