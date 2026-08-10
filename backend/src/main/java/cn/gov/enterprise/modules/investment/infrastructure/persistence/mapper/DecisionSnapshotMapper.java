package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;
import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DecisionSnapshotEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
public interface DecisionSnapshotMapper extends BaseMapperX<DecisionSnapshotEntity> {
    @Select("SELECT COALESCE(MAX(snapshot_version),0)+1 FROM investment_decision_snapshot WHERE decision_id=#{id} AND deleted=0")
    int nextVersion(@Param("id") Long decisionId);
}
