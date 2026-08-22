package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowClaimEligibilityMapper {
    @Select("""
        SELECT u.id AS user_id, u.status AS user_status, u.locked_until,
               e.id AS employee_id, e.status AS employee_status,
               o.id AS org_id, o.status AS org_status,
               p.id AS position_id, p.status AS position_status
        FROM sys_user u
        LEFT JOIN hr_employee e ON e.id = u.employee_id AND e.deleted = 0
        LEFT JOIN sys_org o ON o.id = u.org_id AND o.deleted = 0
        LEFT JOIN hr_position p ON p.id = e.position_id AND p.deleted = 0
        WHERE u.id = #{userId} AND u.deleted = 0
        """)
    EligibilityFacts selectFacts(@Param("userId") Long userId);

    record EligibilityFacts(
            Long userId, Integer userStatus, LocalDateTime lockedUntil,
            Long employeeId, String employeeStatus,
            Long orgId, Integer orgStatus,
            Long positionId, Integer positionStatus) {
    }
}
