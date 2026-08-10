-- Registers existing projects as LEGACY without inferring template provenance.
-- This script is idempotent and never changes project or stage runtime state.
USE enterprise_platform;

START TRANSACTION;

INSERT INTO project_lifecycle_instance (
    id, project_id, source_type, template_id, template_version_id,
    template_code_snapshot, template_name_snapshot, version_no_snapshot,
    version_name_snapshot, template_checksum, snapshot_checksum,
    progress_policy_snapshot, snapshot_status, status,
    initialized_by, initialized_time,
    create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT
    p.id, p.id, 'LEGACY', NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL,
    'LEGACY_EQUAL', 'BUILDING',
    CASE
        WHEN p.status = 'COMPLETED' THEN 'COMPLETED'
        WHEN p.status = 'CANCELLED' THEN 'CANCELLED'
        ELSE 'ACTIVE'
    END,
    'MIGRATION_V2_1_2', CURRENT_TIMESTAMP(3),
    p.create_time, p.create_by, CURRENT_TIMESTAMP(3), 'MIGRATION_V2_1_2',
    0, 0, '历史项目生命周期来源不可证明，按LEGACY登记', 0
FROM project_info p
WHERE p.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM project_lifecycle_instance i
      WHERE i.project_id = p.id AND i.deleted = 0
  );

INSERT INTO project_lifecycle_stage_snapshot (
    id, lifecycle_instance_id, project_id, source_stage_template_id,
    stage_code, stage_name, stage_order,
    progress_weight, weight_source, required_flag, allow_skip,
    approval_required, approval_scene_code, completion_mode,
    planned_duration_days, condition_snapshot_status, definition_checksum,
    create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT
    s.id, i.id, s.project_id, NULL,
    s.stage_code, s.stage_name, s.stage_order,
    NULL, 'UNCONFIRMED', NULL, NULL,
    NULL, NULL, NULL, NULL, 'UNAVAILABLE',
    SHA2(CONCAT_WS('|', s.project_id, s.stage_code, s.stage_name, s.stage_order), 256),
    s.create_time, s.create_by, CURRENT_TIMESTAMP(3), 'MIGRATION_V2_1_2',
    0, 0, 'LEGACY阶段定义快照，不推断模板、权重或条件', 0
FROM project_stage s
JOIN project_lifecycle_instance i
  ON i.project_id = s.project_id
 AND i.source_type = 'LEGACY'
 AND i.deleted = 0
WHERE s.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM project_lifecycle_stage_snapshot ss
      WHERE ss.id = s.id
  );

UPDATE project_stage s
SET s.lifecycle_instance_id = (
        SELECT ss.lifecycle_instance_id
        FROM project_lifecycle_stage_snapshot ss
        WHERE ss.id = s.id AND ss.project_id = s.project_id AND ss.deleted = 0
    ),
    s.stage_snapshot_id = (
        SELECT ss.id
        FROM project_lifecycle_stage_snapshot ss
        WHERE ss.id = s.id AND ss.project_id = s.project_id AND ss.deleted = 0
    ),
    s.update_time = s.update_time
WHERE s.deleted = 0
  AND (s.lifecycle_instance_id IS NULL OR s.stage_snapshot_id IS NULL)
  AND EXISTS (
      SELECT 1
      FROM project_lifecycle_stage_snapshot ss
      WHERE ss.id = s.id AND ss.project_id = s.project_id AND ss.deleted = 0
  );

UPDATE project_lifecycle_instance i
SET i.snapshot_checksum = SHA2(CONCAT_WS('|',
        'LEGACY', i.project_id,
        (SELECT COUNT(*) FROM project_lifecycle_stage_snapshot ss
         WHERE ss.lifecycle_instance_id = i.id AND ss.deleted = 0),
        (SELECT COALESCE(SUM(ss.stage_order), 0)
         FROM project_lifecycle_stage_snapshot ss
         WHERE ss.lifecycle_instance_id = i.id AND ss.deleted = 0)
    ), 256),
    i.snapshot_status = CASE
        WHEN (SELECT COUNT(*) FROM project_lifecycle_stage_snapshot ss
              WHERE ss.lifecycle_instance_id = i.id AND ss.deleted = 0) > 0
         AND (SELECT COUNT(*) FROM project_lifecycle_stage_snapshot ss
              WHERE ss.lifecycle_instance_id = i.id AND ss.deleted = 0)
             = (SELECT COUNT(*) FROM project_stage s
                WHERE s.project_id = i.project_id AND s.deleted = 0)
        THEN 'READY'
        ELSE 'INVALID'
    END,
    i.update_time = CURRENT_TIMESTAMP(3),
    i.update_by = 'MIGRATION_V2_1_2'
WHERE i.source_type = 'LEGACY' AND i.deleted = 0;

UPDATE project_lifecycle_instance
SET snapshot_status = 'INVALID',
    update_time = CURRENT_TIMESTAMP(3),
    update_by = 'MIGRATION_V2_1_2'
WHERE source_type = 'LEGACY'
  AND deleted = 0
  AND snapshot_status = 'BUILDING';

COMMIT;

-- Acceptance queries: every result must be zero.
SELECT COUNT(*) AS legacy_template_reference_errors
FROM project_lifecycle_instance
WHERE source_type = 'LEGACY'
  AND (template_id IS NOT NULL OR template_version_id IS NOT NULL
       OR template_code_snapshot IS NOT NULL OR version_no_snapshot IS NOT NULL);

SELECT COUNT(*) AS unlinked_legacy_stages
FROM project_stage s
JOIN project_lifecycle_instance i ON i.project_id = s.project_id
WHERE s.deleted = 0 AND i.source_type = 'LEGACY'
  AND (s.lifecycle_instance_id IS NULL OR s.stage_snapshot_id IS NULL);
