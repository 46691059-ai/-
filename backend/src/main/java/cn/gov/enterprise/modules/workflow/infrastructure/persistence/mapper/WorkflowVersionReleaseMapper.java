package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionReleaseEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkflowVersionReleaseMapper extends BaseMapperX<WorkflowVersionReleaseEntity> {
    @Select("SELECT id,definition_id,previous_version_id,published_version_id,published_version_no,"
            + "content_hash,engine_mode,content_hash_algorithm,resolver_binding_model,"
            + "resolver_binding_manifest_hash,resolver_binding_count,resolver_binding_canonical_version,"
            + "operator_user_id,operator_org_id,published_time,trace_id,validation_summary,"
            + "created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version "
            + "FROM workflow_version_release WHERE definition_id=#{definitionId} "
            + "AND published_version_id=#{publishedVersionId} AND deleted=0")
    WorkflowVersionReleaseEntity selectByDefinitionIdAndPublishedVersionId(
            @Param("definitionId") Long definitionId,
            @Param("publishedVersionId") Long publishedVersionId);
}
