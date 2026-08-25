-- Read-only verification for RC2_CONTROLLED_CANARY_FIXTURE_V1.
-- The persisted revision effective_from was created from the fixture manifest contract.
SET @RC2_CANARY_DIRECTORY_EFFECTIVE_AT := (
    SELECT effective_from
      FROM approval_role_revision
     WHERE id=990305
       AND enterprise_id='990001'
       AND organization_id=990101
       AND role_code='RC1_TEST_CANARY_APPROVER'
       AND revision=1
);

SELECT 'DIRECTORY_EFFECTIVE_AT' AS check_name,
       @RC2_CANARY_DIRECTORY_EFFECTIVE_AT AS actual
UNION ALL SELECT 'REVISION_HASH_EFFECTIVE_AT', @RC2_CANARY_DIRECTORY_EFFECTIVE_AT
UNION ALL SELECT 'DIRECTORY_QUERY_EFFECTIVE_AT', @RC2_CANARY_DIRECTORY_EFFECTIVE_AT;

SELECT 'TEST_ORG_COUNT' AS check_name, COUNT(*) AS actual FROM sys_org
 WHERE id=990101 AND org_code='RC2_TEST_CANARY_ORG' AND status=1 AND deleted=0
UNION ALL SELECT 'TEST_USER_COUNT', COUNT(*) FROM sys_user
 WHERE id IN (990201,990202) AND username LIKE 'RC2_TEST_CANARY_USER_%'
   AND status=1 AND locked_until>CURRENT_TIMESTAMP(3) AND deleted=0
UNION ALL SELECT 'APPROVAL_ROLE_COUNT', COUNT(*) FROM approval_role
 WHERE id=990301 AND enterprise_id='990001' AND role_code='RC1_TEST_CANARY_APPROVER'
   AND status='ACTIVE' AND deleted=0
UNION ALL SELECT 'VALID_ROLE_ASSIGNMENT_COUNT', COUNT(*) FROM approval_role_assignment
 WHERE id IN (990303,990304) AND enterprise_id='990001' AND organization_id=990101
   AND role_code='RC1_TEST_CANARY_APPROVER' AND status='ACTIVE' AND deleted=0
   AND effective_from<=CAST(@RC2_CANARY_DIRECTORY_EFFECTIVE_AT AS DATETIME(3))
   AND effective_to>CAST(@RC2_CANARY_DIRECTORY_EFFECTIVE_AT AS DATETIME(3))
UNION ALL SELECT 'REVISION_HEAD_COUNT', COUNT(*) FROM approval_role_revision_head
 WHERE id=990302 AND enterprise_id='990001' AND organization_id=990101
   AND role_code='RC1_TEST_CANARY_APPROVER' AND current_revision=1
   AND current_result_hash REGEXP '^[0-9a-f]{64}$' AND deleted=0
UNION ALL SELECT 'REVISION_COUNT', COUNT(*) FROM approval_role_revision
 WHERE id=990305 AND enterprise_id='990001' AND organization_id=990101
   AND role_code='RC1_TEST_CANARY_APPROVER' AND revision=1
   AND result_hash REGEXP '^[0-9a-f]{64}$'
UNION ALL SELECT 'DEFINITION_COUNT', COUNT(*) FROM workflow_definition
 WHERE id=990401 AND enterprise_id=990001
   AND definition_code='RC2_TEST_CANARY_ROLE_APPROVAL' AND status='ACTIVE' AND deleted=0
UNION ALL SELECT 'PUBLISHED_VERSION_COUNT', COUNT(*) FROM workflow_version
 WHERE id=990402 AND definition_id=990401 AND status='PUBLISHED'
   AND resolver_binding_model='VERSION_RESOLVER_BINDING_CAPABLE' AND deleted=0
UNION ALL SELECT 'ROLE_BOUND_NODE_COUNT', COUNT(*) FROM workflow_version_node_resolver_binding
 WHERE id=990405 AND definition_id=990401 AND definition_version_id=990402
   AND node_id=990404 AND resolver_code='ROLE_DIRECTORY'
   AND resolver_version='ROLE_DIRECTORY_V1' AND role_code='RC1_TEST_CANARY_APPROVER'
   AND organization_scope_type='FIXED_ORG' AND organization_id=990101
   AND effective_time_policy='NODE_ACTIVATED_AT' AND deleted=0
UNION ALL SELECT 'MANIFEST_COUNT', COUNT(*) FROM workflow_version_resolver_binding_manifest
 WHERE id=990406 AND definition_id=990401 AND definition_version_id=990402
   AND binding_count=1 AND manifest_hash REGEXP '^[0-9a-f]{64}$'
UNION ALL SELECT 'VERSION_RELEASE_COUNT', COUNT(*) FROM workflow_version_release
 WHERE id=990407 AND definition_id=990401 AND published_version_id=990402 AND deleted=0
UNION ALL SELECT 'RUNTIME_INSTANCE_COUNT', COUNT(*) FROM workflow_instance
 WHERE definition_id=990401 AND version_id=990402 AND deleted=0
UNION ALL SELECT 'RUNTIME_TASK_COUNT', COUNT(*) FROM workflow_task t
 JOIN workflow_instance i ON i.id=t.instance_id WHERE i.definition_id=990401 AND t.deleted=0
UNION ALL SELECT 'CANDIDATE_POOL_COUNT', COUNT(*) FROM workflow_task_candidate_pool
 WHERE instance_id IN (SELECT id FROM workflow_instance WHERE definition_id=990401)
UNION ALL SELECT 'CLAIM_COUNT', COUNT(*) FROM workflow_task_claim
 WHERE task_id IN (SELECT t.id FROM workflow_task t JOIN workflow_instance i ON i.id=t.instance_id
                    WHERE i.definition_id=990401)
UNION ALL SELECT 'ADMISSION_COUNT', COUNT(*) FROM workflow_role_runtime_execution_admission
 WHERE definition_id=990401 AND definition_version_id=990402;
