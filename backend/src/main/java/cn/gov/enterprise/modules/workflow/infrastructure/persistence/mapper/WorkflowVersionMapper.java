package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WorkflowVersionMapper extends BaseMapperX<WorkflowVersionEntity> {
    @Select("SELECT * FROM workflow_version WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    WorkflowVersionEntity selectActiveByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM workflow_version "
            + "WHERE definition_id = #{definitionId} AND deleted = 0")
    int selectNextVersionNo(@Param("definitionId") Long definitionId);

    @Update("UPDATE workflow_version SET resolver_binding_manifest_hash = #{manifestHash}, "
            + "resolver_binding_count = #{bindingCount}, "
            + "resolver_binding_canonical_version = #{canonicalVersion}, "
            + "updated_by = #{updatedBy}, updated_time = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND status = 'DRAFT' "
            + "AND resolver_binding_model = 'VERSION_RESOLVER_BINDING_CAPABLE' "
            + "AND version = #{expectedVersion}")
    int prepareResolverBindingSnapshot(@Param("id") Long id,
            @Param("manifestHash") String manifestHash,
            @Param("bindingCount") int bindingCount,
            @Param("canonicalVersion") String canonicalVersion,
            @Param("updatedBy") String updatedBy,
            @Param("expectedVersion") int expectedVersion);

    @Update("UPDATE workflow_version SET status = #{status}, content_hash = #{contentHash}, "
            + "effective_from = #{effectiveFrom}, effective_to = #{effectiveTo}, "
            + "published_by = #{publishedBy}, published_time = #{publishedTime}, "
            + "resolver_binding_model = #{resolverBindingModel}, "
            + "resolver_binding_manifest_hash = #{resolverBindingManifestHash}, "
            + "resolver_binding_count = #{resolverBindingCount}, "
            + "resolver_binding_canonical_version = #{resolverBindingCanonicalVersion}, "
            + "updated_by = #{updatedBy}, updated_time = CURRENT_TIMESTAMP(3), version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND status = #{expectedStatus} AND version = #{expectedVersion}")
    int updateState(@Param("id") Long id, @Param("status") String status,
            @Param("contentHash") String contentHash,
            @Param("effectiveFrom") java.time.LocalDateTime effectiveFrom,
            @Param("effectiveTo") java.time.LocalDateTime effectiveTo,
            @Param("publishedBy") Long publishedBy,
            @Param("publishedTime") java.time.LocalDateTime publishedTime,
            @Param("resolverBindingModel") String resolverBindingModel,
            @Param("resolverBindingManifestHash") String resolverBindingManifestHash,
            @Param("resolverBindingCount") int resolverBindingCount,
            @Param("resolverBindingCanonicalVersion") String resolverBindingCanonicalVersion,
            @Param("updatedBy") String updatedBy, @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") int expectedVersion);
}
