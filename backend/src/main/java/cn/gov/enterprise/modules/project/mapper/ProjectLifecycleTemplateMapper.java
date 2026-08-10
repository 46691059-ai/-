package cn.gov.enterprise.modules.project.mapper;

import cn.gov.enterprise.modules.project.infrastructure.persistence.LifecycleTemplateSelectionRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectLifecycleTemplateMapper {
    @Select("""
        SELECT t.id AS template_id,
               v.id AS template_version_id,
               t.template_code,
               t.template_name,
               v.version_no,
               v.version_name,
               v.content_checksum,
               CASE
                 WHEN t.project_type = #{projectType} AND t.org_id = #{orgId} THEN 1
                 WHEN t.project_type = #{projectType} AND t.org_id IS NULL THEN 2
                 WHEN t.project_type = 'ALL' AND t.org_id = #{orgId} THEN 3
                 ELSE 4
               END AS selection_priority
        FROM project_lifecycle_template t
        JOIN project_lifecycle_template_active a ON a.template_id = t.id
        JOIN project_lifecycle_template_version v
          ON v.id = a.template_version_id AND v.template_id = t.id
        WHERE t.deleted = 0
          AND v.deleted = 0
          AND t.status = 'ENABLED'
          AND v.status = 'ACTIVE'
          AND (t.project_type = #{projectType} OR t.project_type = 'ALL')
          AND (t.org_id = #{orgId} OR t.org_id IS NULL)
          AND (v.effective_from IS NULL OR v.effective_from <= CURRENT_TIMESTAMP(3))
          AND (v.effective_to IS NULL OR v.effective_to >= CURRENT_TIMESTAMP(3))
        ORDER BY selection_priority, t.id
        """)
    List<LifecycleTemplateSelectionRow> selectActiveCandidates(
            @Param("projectType") String projectType,
            @Param("orgId") Long orgId);
}
