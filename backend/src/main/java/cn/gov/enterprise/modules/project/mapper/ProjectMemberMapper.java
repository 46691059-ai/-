package cn.gov.enterprise.modules.project.mapper;

import cn.gov.enterprise.modules.project.entity.ProjectMemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProjectMemberMapper extends BaseMapper<ProjectMemberEntity> {
    @Update("""
        UPDATE pm_project_member
        SET deleted = 1, delete_token = id,
            updated_time = CURRENT_TIMESTAMP(3), updated_by = #{userId}
        WHERE id = #{id} AND deleted = 0
        """)
    int softDelete(
            @Param("id") Long id,
            @Param("userId") Long userId);

    @Update("""
        UPDATE pm_project_member
        SET deleted = 1, delete_token = id,
            updated_time = CURRENT_TIMESTAMP(3), updated_by = #{userId}
        WHERE project_id = #{projectId} AND deleted = 0
        """)
    int softDeleteByProject(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId);
}
