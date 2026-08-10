-- Approved platform lifecycle templates for project types 01 and 02.
-- Published versions and stage definitions are immutable after this migration.
USE enterprise_platform;

START TRANSACTION;

INSERT INTO project_lifecycle_template (
    id, template_code, template_name, project_type, org_id, description, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT 2101001, 'INVESTMENT_STANDARD', '标准投资项目生命周期', '01', NULL,
       '投资机会、可研、尽调、决策、实施、投后、退出、归档', 'ENABLED',
       CURRENT_TIMESTAMP(3), 'MIGRATION_V2_1_3', CURRENT_TIMESTAMP(3),
       'MIGRATION_V2_1_3', 0, 0, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM project_lifecycle_template
    WHERE template_code = 'INVESTMENT_STANDARD' AND deleted = 0
);

INSERT INTO project_lifecycle_template (
    id, template_code, template_name, project_type, org_id, description, status,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT 2101002, 'DELIVERY_STANDARD', '标准中标项目生命周期', '02', NULL,
       '商机、投标、合同、实施、验收、回款、利润复盘、归档', 'ENABLED',
       CURRENT_TIMESTAMP(3), 'MIGRATION_V2_1_3', CURRENT_TIMESTAMP(3),
       'MIGRATION_V2_1_3', 0, 0, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM project_lifecycle_template
    WHERE template_code = 'DELIVERY_STANDARD' AND deleted = 0
);

INSERT INTO project_lifecycle_template_version (
    id, template_id, version_no, version_name, status,
    effective_from, effective_to, content_checksum, source_version_id,
    change_summary, published_by, published_time,
    create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 2101101, 2101001, 1, 'V1', 'ACTIVE',
       CURRENT_TIMESTAMP(3), NULL,
       '4fcafe0015a4706b2dce5b122202e85734164194963cd3d2d7bf0286cc64cc63',
       NULL, '标准投资项目模板首次发布', 'MIGRATION_V2_1_3', CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3), 'MIGRATION_V2_1_3', CURRENT_TIMESTAMP(3),
       'MIGRATION_V2_1_3', 0, 0, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM project_lifecycle_template_version
    WHERE template_id = 2101001 AND version_no = 1 AND deleted = 0
);

INSERT INTO project_lifecycle_template_version (
    id, template_id, version_no, version_name, status,
    effective_from, effective_to, content_checksum, source_version_id,
    change_summary, published_by, published_time,
    create_time, create_by, update_time, update_by,
    deleted, delete_token, remark, version
)
SELECT 2101102, 2101002, 1, 'V1', 'ACTIVE',
       CURRENT_TIMESTAMP(3), NULL,
       'f2ac099c2d0a14c183a63efd0319a4cc2bffc2c870df11acb01cd689fc0f3db0',
       NULL, '标准中标项目模板首次发布', 'MIGRATION_V2_1_3', CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3), 'MIGRATION_V2_1_3', CURRENT_TIMESTAMP(3),
       'MIGRATION_V2_1_3', 0, 0, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM project_lifecycle_template_version
    WHERE template_id = 2101002 AND version_no = 1 AND deleted = 0
);

INSERT INTO project_lifecycle_stage_template (
    id, template_version_id, stage_code, stage_name, stage_order,
    required_flag, allow_skip, progress_weight, planned_duration_days,
    auto_start, approval_required, approval_scene_code, completion_mode, description,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT s.id, 2101101, s.stage_code, s.stage_name, s.stage_order,
       1, 0, 12.5000, NULL, s.auto_start, s.approval_required,
       s.approval_scene_code, 'MANUAL', NULL,
       CURRENT_TIMESTAMP(3), 'MIGRATION_V2_1_3', CURRENT_TIMESTAMP(3),
       'MIGRATION_V2_1_3', 0, 0, NULL, 0
FROM (
    SELECT 2101201 id, 'OPPORTUNITY' stage_code, '投资机会' stage_name, 1 stage_order, 1 auto_start, 0 approval_required, NULL approval_scene_code
    UNION ALL SELECT 2101202, 'FEASIBILITY', '可行性研究', 2, 0, 0, NULL
    UNION ALL SELECT 2101203, 'DUE_DILIGENCE', '尽职调查', 3, 0, 0, NULL
    UNION ALL SELECT 2101204, 'DECISION', '投资决策', 4, 0, 1, 'INVESTMENT_DECISION'
    UNION ALL SELECT 2101205, 'IMPLEMENTATION', '投资实施', 5, 0, 1, 'INVESTMENT_PAYMENT'
    UNION ALL SELECT 2101206, 'POST_INVESTMENT', '投后管理', 6, 0, 0, NULL
    UNION ALL SELECT 2101207, 'EXIT', '投资退出', 7, 0, 1, 'INVESTMENT_EXIT'
    UNION ALL SELECT 2101208, 'ARCHIVE', '投资项目归档', 8, 0, 0, NULL
) s
WHERE NOT EXISTS (
    SELECT 1 FROM project_lifecycle_stage_template t
    WHERE t.template_version_id = 2101101 AND t.stage_code = s.stage_code AND t.deleted = 0
);

INSERT INTO project_lifecycle_stage_template (
    id, template_version_id, stage_code, stage_name, stage_order,
    required_flag, allow_skip, progress_weight, planned_duration_days,
    auto_start, approval_required, approval_scene_code, completion_mode, description,
    create_time, create_by, update_time, update_by, deleted, delete_token, remark, version
)
SELECT s.id, 2101102, s.stage_code, s.stage_name, s.stage_order,
       1, 0, 12.5000, NULL, s.auto_start, s.approval_required,
       s.approval_scene_code, 'MANUAL', NULL,
       CURRENT_TIMESTAMP(3), 'MIGRATION_V2_1_3', CURRENT_TIMESTAMP(3),
       'MIGRATION_V2_1_3', 0, 0, NULL, 0
FROM (
    SELECT 2101301 id, 'OPPORTUNITY' stage_code, '客户与商机' stage_name, 1 stage_order, 1 auto_start, 0 approval_required, NULL approval_scene_code
    UNION ALL SELECT 2101302, 'BID', '投标管理', 2, 0, 1, 'PROJECT_BID'
    UNION ALL SELECT 2101303, 'CONTRACT', '合同签订', 3, 0, 1, 'PROJECT_CONTRACT'
    UNION ALL SELECT 2101304, 'IMPLEMENTATION', '项目实施', 4, 0, 0, NULL
    UNION ALL SELECT 2101305, 'ACCEPTANCE', '项目验收', 5, 0, 1, 'PROJECT_ACCEPTANCE'
    UNION ALL SELECT 2101306, 'COLLECTION', '应收与回款', 6, 0, 0, NULL
    UNION ALL SELECT 2101307, 'PROFIT_REVIEW', '利润复盘', 7, 0, 1, 'PROJECT_PROFIT_REVIEW'
    UNION ALL SELECT 2101308, 'ARCHIVE', '中标项目归档', 8, 0, 0, NULL
) s
WHERE NOT EXISTS (
    SELECT 1 FROM project_lifecycle_stage_template t
    WHERE t.template_version_id = 2101102 AND t.stage_code = s.stage_code AND t.deleted = 0
);

INSERT INTO project_lifecycle_template_active (
    template_id, template_version_id, activated_by, activated_time, version
)
SELECT 2101001, 2101101, 'MIGRATION_V2_1_3', CURRENT_TIMESTAMP(3), 0
WHERE NOT EXISTS (
    SELECT 1 FROM project_lifecycle_template_active WHERE template_id = 2101001
);

INSERT INTO project_lifecycle_template_active (
    template_id, template_version_id, activated_by, activated_time, version
)
SELECT 2101002, 2101102, 'MIGRATION_V2_1_3', CURRENT_TIMESTAMP(3), 0
WHERE NOT EXISTS (
    SELECT 1 FROM project_lifecycle_template_active WHERE template_id = 2101002
);

COMMIT;
