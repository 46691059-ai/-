package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionResolverBindingManifestEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkflowVersionResolverBindingManifestMapper
        extends BaseMapperX<WorkflowVersionResolverBindingManifestEntity> {
    String COLUMNS = "id,definition_id,definition_version_id,canonical_version,binding_count,"
            + "manifest_hash,released_by,released_time,created_by,created_time,updated_by,updated_time,"
            + "deleted,delete_token,remark,version";

    @Select("SELECT " + COLUMNS + " FROM workflow_version_resolver_binding_manifest "
            + "WHERE definition_version_id=#{definitionVersionId} AND deleted=0")
    WorkflowVersionResolverBindingManifestEntity selectByDefinitionVersionId(
            @Param("definitionVersionId") Long definitionVersionId);
}
