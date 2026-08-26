package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.workflow.domain.canary.*;
import cn.gov.enterprise.modules.workflow.domain.repository.CanaryGovernanceRepository;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.CanaryGovernanceEntity;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper.CanaryGovernanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.*;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class CanaryGovernanceRepositoryImpl implements CanaryGovernanceRepository {
    private final CanaryGovernanceMapper mapper; private final WorkflowPersistenceAudit audit;
    public CanaryGovernanceRepositoryImpl(CanaryGovernanceMapper mapper,WorkflowPersistenceAudit audit){this.mapper=mapper;this.audit=audit;}
    @Override public void insert(CanaryGovernanceRecord record){
        CanaryGovernanceEntity entity=CanaryGovernanceEntityMapper.toEntity(record);audit.initialize(entity);
        try{if(mapper.insert(entity)!=1)throw new BusinessException("B26240","Canary governance insert failed");}
        catch(DuplicateKeyException ex){throw new BusinessException("B26249","concurrent or duplicate Canary governance revision");}
    }
    @Override public Optional<CanaryGovernanceRecord> latest(CanaryScope s,Instant at){
        LocalDateTime time=LocalDateTime.ofInstant(at,ZoneOffset.UTC);
        var rows=mapper.selectList(new LambdaQueryWrapper<CanaryGovernanceEntity>()
                .eq(CanaryGovernanceEntity::getEnterpriseId,s.enterpriseId())
                .eq(CanaryGovernanceEntity::getOrganizationId,s.organizationId())
                .eq(CanaryGovernanceEntity::getDefinitionId,s.definitionId())
                .eq(CanaryGovernanceEntity::getDefinitionVersionId,s.definitionVersionId())
                .eq(CanaryGovernanceEntity::getNodeId,s.nodeId()).eq(CanaryGovernanceEntity::getRoleCode,s.roleCode())
                .eq(CanaryGovernanceEntity::getDeleted,0).le(CanaryGovernanceEntity::getEffectiveFrom,time)
                .and(q->q.isNull(CanaryGovernanceEntity::getEffectiveTo).or().gt(CanaryGovernanceEntity::getEffectiveTo,time))
                .orderByDesc(CanaryGovernanceEntity::getGovernanceRevision).last("LIMIT 1"));
        if(rows.size()!=1)return Optional.empty();
        return Optional.of(CanaryGovernanceEntityMapper.toDomain(rows.getFirst()));
    }
}
