package cn.gov.enterprise.modules.workflow.infrastructure.persistence.mapper;

import cn.gov.enterprise.common.persistence.BaseMapperX;
import cn.gov.enterprise.modules.workflow.infrastructure.persistence.entity.WorkflowVersionNodeResolverBindingEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WorkflowVersionNodeResolverBindingMapper
        extends BaseMapperX<WorkflowVersionNodeResolverBindingEntity> {
    String COLUMNS = "id,definition_id,definition_version_id,node_id,binding_order,"
            + "resolver_code,resolver_version,resolver_contract_hash,strategy_type,resolver_mode,"
            + "target_type,role_code,organization_scope_type,organization_id,effective_time_policy,"
            + "binding_schema_version,binding_hash,created_by,created_time,updated_by,updated_time,"
            + "deleted,delete_token,remark,version";

    @Select("SELECT " + COLUMNS + " FROM workflow_version_node_resolver_binding "
            + "WHERE id=#{id} AND deleted=0")
    WorkflowVersionNodeResolverBindingEntity selectActiveById(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM workflow_version_node_resolver_binding "
            + "WHERE definition_version_id=#{definitionVersionId} AND deleted=0 "
            + "ORDER BY node_id,binding_order")
    List<WorkflowVersionNodeResolverBindingEntity> selectActiveByVersionId(
            @Param("definitionVersionId") Long definitionVersionId);

    @Select("SELECT " + COLUMNS + " FROM workflow_version_node_resolver_binding "
            + "WHERE definition_version_id=#{definitionVersionId} AND node_id=#{nodeId} AND deleted=0 "
            + "ORDER BY node_id,binding_order")
    List<WorkflowVersionNodeResolverBindingEntity> selectActiveByVersionIdAndNodeId(
            @Param("definitionVersionId") Long definitionVersionId,
            @Param("nodeId") Long nodeId);

    @Update("UPDATE workflow_version_node_resolver_binding SET "
            + "binding_order=#{entity.bindingOrder},resolver_code=#{entity.resolverCode},"
            + "resolver_version=#{entity.resolverVersion},resolver_contract_hash=#{entity.resolverContractHash},"
            + "strategy_type=#{entity.strategyType},resolver_mode=#{entity.resolverMode},"
            + "target_type=#{entity.targetType},role_code=#{entity.roleCode},"
            + "organization_scope_type=#{entity.organizationScopeType},organization_id=#{entity.organizationId},"
            + "effective_time_policy=#{entity.effectiveTimePolicy},"
            + "binding_schema_version=#{entity.bindingSchemaVersion},binding_hash=#{entity.bindingHash},"
            + "updated_by=#{entity.updatedBy},updated_time=#{entity.updatedTime},version=version+1 "
            + "WHERE id=#{entity.id} AND deleted=0 AND version=#{expectedVersion}")
    int updateActive(
            @Param("entity") WorkflowVersionNodeResolverBindingEntity entity,
            @Param("expectedVersion") int expectedVersion);

    @Update("UPDATE workflow_version_node_resolver_binding SET "
            + "deleted=1,delete_token=id,updated_by=#{updatedBy},updated_time=CURRENT_TIMESTAMP(3),"
            + "version=version+1 WHERE id=#{id} AND deleted=0 AND version=#{expectedVersion}")
    int logicalDelete(
            @Param("id") Long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("updatedBy") String updatedBy);
}
