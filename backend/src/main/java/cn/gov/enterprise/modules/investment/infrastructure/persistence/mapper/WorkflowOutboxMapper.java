package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;
import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowOutboxEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WorkflowOutboxMapper extends BaseMapperX<WorkflowOutboxEntity> {
    @Select("""
            SELECT * FROM investment_workflow_outbox
            WHERE deleted=0 AND (
                (status IN ('PENDING','FAILED') AND (next_retry_time IS NULL OR next_retry_time<=#{now}))
                OR (status='PROCESSING' AND lock_until<#{now})
            )
            ORDER BY COALESCE(next_retry_time,create_time),create_time,id
            LIMIT #{limit} FOR UPDATE SKIP LOCKED
            """)
    List<WorkflowOutboxEntity> selectDispatchCandidatesForUpdate(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE investment_workflow_outbox
            SET status='PROCESSING',worker_id=#{workerId},locked_at=#{now},lock_until=#{lockUntil},
                update_time=#{now},version=version+1
            WHERE id=#{id} AND deleted=0 AND version=#{version}
              AND (status IN ('PENDING','FAILED') OR (status='PROCESSING' AND lock_until<#{now}))
            """)
    int claim(@Param("id") Long id, @Param("version") Integer version,
            @Param("workerId") String workerId, @Param("now") LocalDateTime now,
            @Param("lockUntil") LocalDateTime lockUntil);
}
