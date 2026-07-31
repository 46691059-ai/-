package cn.gov.enterprise.modules.project.mapper;

import cn.gov.enterprise.modules.project.entity.ProjectStageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProjectStageMapper extends BaseMapper<ProjectStageEntity> {
    @Update("""
        UPDATE project_stage
        SET deleted = 1, delete_token = id,
            update_time = CURRENT_TIMESTAMP(3), update_by = #{operator}
        WHERE project_id = #{projectId} AND deleted = 0
        """)
    int softDeleteByProject(
            @Param("projectId") Long projectId,
            @Param("operator") String operator);
}
