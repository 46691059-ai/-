package cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper;
import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.WorkflowInboxEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkflowInboxMapper extends BaseMapperX<WorkflowInboxEntity> {
    @Select("SELECT * FROM investment_workflow_inbox WHERE event_id=#{eventId} AND deleted=0 LIMIT 1")
    WorkflowInboxEntity selectByEventId(@Param("eventId") String eventId);

    @Select("""
            SELECT * FROM investment_workflow_inbox
            WHERE binding_id=#{bindingId} AND process_status='BUFFERED' AND deleted=0
            ORDER BY event_sequence LIMIT #{limit}
            """)
    List<WorkflowInboxEntity> selectBuffered(
            @Param("bindingId") Long bindingId, @Param("limit") int limit);

    @Select("""
            SELECT i.* FROM investment_workflow_inbox i
            JOIN (
                SELECT binding_id,MIN(event_sequence) event_sequence
                FROM investment_workflow_inbox
                WHERE process_status='BUFFERED' AND deleted=0 GROUP BY binding_id
            ) h ON h.binding_id=i.binding_id AND h.event_sequence=i.event_sequence
            WHERE i.process_status='BUFFERED' AND i.deleted=0
            ORDER BY i.received_time LIMIT #{limit}
            """)
    List<WorkflowInboxEntity> selectBufferedHeads(@Param("limit") int limit);
}
