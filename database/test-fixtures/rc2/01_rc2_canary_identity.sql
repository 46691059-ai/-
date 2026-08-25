-- RC2_CONTROLLED_CANARY_FIXTURE_V1 / TEST_ONLY / NON_PRODUCTION.
-- Executed only by the guarded Application Fixture Seeder inside one outer transaction.
-- No password below is a credential: both users are locked and the marker is not a valid encoded password.
INSERT INTO sys_org (
    id, org_code, org_name, org_type, parent_id, leader_id, tree_path, tree_level,
    sort_no, status, create_by, update_by, deleted, delete_token, remark, version
) VALUES (
    990101, 'RC2_TEST_CANARY_ORG', 'RC2 TEST Canary Organization', 'PROJECT_TEAM',
    NULL, NULL, '/', 1, 990, 1, 'RC2_TEST_FIXTURE', 'RC2_TEST_FIXTURE',
    0, 0, 'TEST_ONLY; NON_PRODUCTION', 0
);

INSERT INTO sys_user (
    id, username, password, real_name, phone, email, org_id, employee_id, status,
    token_version, login_fail_count, locked_until, create_by, update_by,
    deleted, delete_token, remark, version
) VALUES
(
    990201, 'RC2_TEST_CANARY_USER_01', '!RC2_TEST_NO_LOGIN_HASH!',
    'RC2 TEST CANARY USER 01', NULL, NULL, 990101, NULL, 1,
    2147483647, 0, '9999-12-31 23:59:59.999', 'RC2_TEST_FIXTURE', 'RC2_TEST_FIXTURE',
    0, 0, 'TEST_ONLY; LOGIN_LOCKED', 0
),
(
    990202, 'RC2_TEST_CANARY_USER_02', '!RC2_TEST_NO_LOGIN_HASH!',
    'RC2 TEST CANARY USER 02', NULL, NULL, 990101, NULL, 1,
    2147483647, 0, '9999-12-31 23:59:59.999', 'RC2_TEST_FIXTURE', 'RC2_TEST_FIXTURE',
    0, 0, 'TEST_ONLY; LOGIN_LOCKED', 0
);
