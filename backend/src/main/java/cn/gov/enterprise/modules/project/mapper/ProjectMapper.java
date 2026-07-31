package cn.gov.enterprise.modules.project.mapper;

import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProjectMapper extends BaseMapper<ProjectEntity> {
    @Select("""
        SELECT COUNT(1)
        FROM pm_project_member
        WHERE project_id = #{projectId}
          AND user_id = #{userId}
          AND member_status = 'ACTIVE'
          AND deleted = 0
        """)
    long countActiveMember(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId);

    @Update("""
        UPDATE pm_project
        SET deleted = 1, delete_token = id,
            updated_time = CURRENT_TIMESTAMP(3), updated_by = #{userId}
        WHERE id = #{id} AND deleted = 0
        """)
    int softDelete(
            @Param("id") Long id,
            @Param("userId") Long userId);
}
