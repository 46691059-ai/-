package cn.gov.enterprise.modules.project.mapper;

import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProjectMapper extends BaseMapper<ProjectEntity> {
    @Select("""
        SELECT COUNT(1)
        FROM project_member pm
        JOIN sys_user su ON su.employee_id = pm.employee_id
        WHERE project_id = #{projectId}
          AND su.id = #{userId}
          AND pm.status = 'ACTIVE'
          AND pm.deleted = 0
          AND su.deleted = 0
        """)
    long countActiveMember(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId);

    @Select("""
        SELECT COUNT(1)
        FROM project_info p
        JOIN sys_user su ON su.id = #{userId} AND su.deleted = 0
        WHERE p.id = #{projectId}
          AND (
            p.leader_id = su.employee_id
            OR EXISTS (
                SELECT 1 FROM project_member pm
                WHERE pm.project_id = p.id
                  AND pm.employee_id = su.employee_id
                  AND pm.status = 'ACTIVE'
                  AND pm.deleted = 0
            )
          )
        """)
    long countSelfAccessible(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId);

    @Update("""
        UPDATE project_info
        SET deleted = 1, delete_token = id,
            update_time = CURRENT_TIMESTAMP(3), update_by = #{operator}
        WHERE id = #{id} AND deleted = 0
        """)
    int softDelete(
            @Param("id") Long id,
            @Param("operator") String operator);
}
