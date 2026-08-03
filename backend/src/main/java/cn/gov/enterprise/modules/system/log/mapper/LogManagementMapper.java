package cn.gov.enterprise.modules.system.log.mapper;

import cn.gov.enterprise.modules.system.log.dto.LogPageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LogManagementMapper {
    @Select("""
        <script>
        SELECT l.id, l.user_id, COALESCE(u.username, '') AS username, l.operation,
               l.request_url, l.request_method, l.ip, l.result, l.error_message,
               l.duration_ms, l.trace_id, l.remark, l.create_time
        FROM sys_log l
        LEFT JOIN sys_user u ON u.id = l.user_id
        WHERE l.deleted = 0
        <if test="query.username != null and query.username != ''">
          AND (u.username LIKE CONCAT('%', #{query.username}, '%')
               OR l.remark LIKE CONCAT('%', #{query.username}, '%'))
        </if>
        <if test="query.logType != null and query.logType != ''">
          AND l.operation LIKE CONCAT(#{query.logType}, '|%')
        </if>
        <if test="query.moduleName != null and query.moduleName != ''">
          AND l.operation LIKE CONCAT('%|', #{query.moduleName}, '|%')
        </if>
        <if test="query.status != null and query.status != ''">
          AND l.result = #{query.status}
        </if>
        <if test="query.startTime != null">AND l.create_time &gt;= #{query.startTime}</if>
        <if test="query.endTime != null">AND l.create_time &lt;= #{query.endTime}</if>
        ORDER BY l.create_time DESC, l.id DESC
        </script>
        """)
    Page<LogRow> selectLogPage(Page<LogRow> page, @Param("query") LogPageQuery query);

    @Select("""
        SELECT l.id, l.user_id, COALESCE(u.username, '') AS username, l.operation,
               l.request_url, l.request_method, l.ip, l.result, l.error_message,
               l.duration_ms, l.trace_id, l.remark, l.create_time
        FROM sys_log l LEFT JOIN sys_user u ON u.id = l.user_id
        WHERE l.id = #{id} AND l.deleted = 0
        """)
    LogRow selectLogDetail(@Param("id") Long id);

    @Update("""
        UPDATE sys_log SET deleted = 1, delete_token = id, update_time = CURRENT_TIMESTAMP(3),
               update_by = #{operator}, version = version + 1
        WHERE deleted = 0 AND create_time &lt; #{before}
        """)
    int logicallyDeleteBefore(@Param("before") LocalDateTime before, @Param("operator") String operator);

    record LogRow(
            Long id, Long userId, String username, String operation, String requestUrl,
            String requestMethod, String ip, String result, String errorMessage,
            Long durationMs, String traceId, String remark, LocalDateTime createTime) {
    }
}
