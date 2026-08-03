package cn.gov.enterprise.modules.project.mapper;

import cn.gov.enterprise.modules.project.entity.ProjectEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProjectMapper extends BaseMapper<ProjectEntity> {
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
