-- Organization/Governance Approval Role Directory V1.
-- Shared global Flyway chain; does not enable Workflow ROLE runtime.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE PROCEDURE guard_v2617_approval_role_directory()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_org') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ORG_DIR_PARENT_SYS_ORG_MISSING';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_user') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ORG_DIR_PARENT_SYS_USER_MISSING';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='sys_org' AND index_name='PRIMARY' AND column_name='id') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ORG_DIR_PARENT_SYS_ORG_KEY_MISSING';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='sys_user' AND index_name='PRIMARY' AND column_name='id') <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644, MESSAGE_TEXT='ORG_DIR_PARENT_SYS_USER_KEY_MISSING';
    END IF;
END$$
CALL guard_v2617_approval_role_directory()$$
DROP PROCEDURE guard_v2617_approval_role_directory$$
DELIMITER ;

CREATE TABLE approval_role (
    id BIGINT NOT NULL,
    enterprise_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role_name VARCHAR(200) NOT NULL,
    role_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PROCESS_APPROVAL_ROLE',
    organization_scope_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'BUSINESS_ORG',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    description VARCHAR(500) NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_role_business (enterprise_id,role_code,delete_token),
    KEY idx_approval_role_status (enterprise_id,status,deleted),
    CONSTRAINT chk_approval_role_code CHECK (REGEXP_LIKE(role_code,'^[A-Z][A-Z0-9_]{2,99}$','c')),
    CONSTRAINT chk_approval_role_type CHECK (role_type='PROCESS_APPROVAL_ROLE'),
    CONSTRAINT chk_approval_role_scope CHECK (organization_scope_type='BUSINESS_ORG'),
    CONSTRAINT chk_approval_role_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT chk_approval_role_delete CHECK ((deleted=0 AND delete_token=0) OR (deleted=1 AND delete_token=id)),
    CONSTRAINT chk_approval_role_version CHECK (version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Organization governance process approval role definition';

CREATE TABLE approval_role_revision_head (
    id BIGINT NOT NULL,
    enterprise_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    organization_id BIGINT NOT NULL,
    role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    current_revision BIGINT NOT NULL DEFAULT 0,
    current_result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_role_revision_head (enterprise_id,organization_id,role_code,delete_token),
    KEY idx_approval_role_revision_head_current (enterprise_id,organization_id,role_code,current_revision),
    CONSTRAINT fk_approval_role_revision_head_org FOREIGN KEY (organization_id) REFERENCES sys_org(id),
    CONSTRAINT chk_approval_role_revision_head_code CHECK (REGEXP_LIKE(role_code,'^[A-Z][A-Z0-9_]{2,99}$','c')),
    CONSTRAINT chk_approval_role_revision_head_revision CHECK (current_revision>=0),
    CONSTRAINT chk_approval_role_revision_head_hash CHECK ((current_revision=0 AND current_result_hash IS NULL) OR (current_revision>0 AND REGEXP_LIKE(current_result_hash,'^[0-9a-f]{64}$','c'))),
    CONSTRAINT chk_approval_role_revision_head_delete CHECK ((deleted=0 AND delete_token=0) OR (deleted=1 AND delete_token=id)),
    CONSTRAINT chk_approval_role_revision_head_version CHECK (version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Mutable CAS head for one enterprise-org-approval-role aggregate';

CREATE TABLE approval_role_assignment (
    id BIGINT NOT NULL,
    enterprise_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    organization_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    effective_to DATETIME(3) NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    source_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_system VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_reference VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    source_reference_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_priority INT NOT NULL DEFAULT 0,
    recorded_at DATETIME(3) NOT NULL,
    assignment_key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_role_assignment_key (assignment_key_hash,delete_token),
    KEY idx_approval_role_assignment_resolve (enterprise_id,organization_id,role_code,status,deleted,effective_from,effective_to),
    KEY idx_approval_role_assignment_overlap (enterprise_id,organization_id,role_id,user_id,source_system,source_reference,effective_from,effective_to,status,deleted),
    KEY idx_approval_role_assignment_user (user_id,status,deleted),
    CONSTRAINT fk_approval_role_assignment_role FOREIGN KEY (role_id) REFERENCES approval_role(id),
    CONSTRAINT fk_approval_role_assignment_org FOREIGN KEY (organization_id) REFERENCES sys_org(id),
    CONSTRAINT fk_approval_role_assignment_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT chk_approval_role_assignment_code CHECK (REGEXP_LIKE(role_code,'^[A-Z][A-Z0-9_]{2,99}$','c')),
    CONSTRAINT chk_approval_role_assignment_interval CHECK (effective_to IS NULL OR effective_from<effective_to),
    CONSTRAINT chk_approval_role_assignment_status CHECK (status IN ('ACTIVE','ENDED','CORRECTED')),
    CONSTRAINT chk_approval_role_assignment_source CHECK (source_type IN ('HR_ASSIGNMENT','GOVERNANCE_DECISION','MANUAL_GOVERNANCE_RECORD','IMPORT')),
    CONSTRAINT chk_approval_role_assignment_source_hash CHECK (REGEXP_LIKE(source_reference_hash,'^[0-9a-f]{64}$','c')),
    CONSTRAINT chk_approval_role_assignment_key_hash CHECK (REGEXP_LIKE(assignment_key_hash,'^[0-9a-f]{64}$','c')),
    CONSTRAINT chk_approval_role_assignment_priority CHECK (evidence_priority>=0),
    CONSTRAINT chk_approval_role_assignment_delete CHECK ((deleted=0 AND delete_token=0) OR (deleted=1 AND delete_token=id)),
    CONSTRAINT chk_approval_role_assignment_version CHECK (version>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Effective-dated approval role evidence assignment';

CREATE TABLE approval_role_revision (
    id BIGINT NOT NULL,
    enterprise_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    organization_id BIGINT NOT NULL,
    role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT NOT NULL,
    result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    change_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    change_reason VARCHAR(500) NOT NULL,
    effective_from DATETIME(3) NOT NULL,
    affected_from DATETIME(3) NULL,
    affected_to DATETIME(3) NULL,
    correction_reference VARCHAR(200) NULL,
    published_at DATETIME(3) NOT NULL,
    published_by VARCHAR(64) NOT NULL,
    previous_revision BIGINT NULL,
    previous_result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    create_by VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_by VARCHAR(64) NOT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_role_revision (enterprise_id,organization_id,role_code,revision),
    KEY idx_approval_role_revision_effective (enterprise_id,organization_id,role_code,effective_from,revision),
    CONSTRAINT fk_approval_role_revision_org FOREIGN KEY (organization_id) REFERENCES sys_org(id),
    CONSTRAINT chk_approval_role_revision_code CHECK (REGEXP_LIKE(role_code,'^[A-Z][A-Z0-9_]{2,99}$','c')),
    CONSTRAINT chk_approval_role_revision_positive CHECK (revision>0),
    CONSTRAINT chk_approval_role_revision_hash CHECK (REGEXP_LIKE(result_hash,'^[0-9a-f]{64}$','c')),
    CONSTRAINT chk_approval_role_revision_previous CHECK ((revision=1 AND previous_revision IS NULL AND previous_result_hash IS NULL) OR (revision>1 AND previous_revision=revision-1 AND REGEXP_LIKE(previous_result_hash,'^[0-9a-f]{64}$','c'))),
    CONSTRAINT chk_approval_role_revision_change CHECK (change_type IN ('ASSIGN','END','CORRECTION','ACTIVATE','DEACTIVATE','BATCH')),
    CONSTRAINT chk_approval_role_revision_correction CHECK ((change_type='CORRECTION' AND correction_reference IS NOT NULL AND affected_from IS NOT NULL) OR change_type<>'CORRECTION'),
    CONSTRAINT chk_approval_role_revision_affected CHECK (affected_to IS NULL OR (affected_from IS NOT NULL AND affected_from<affected_to)),
    CONSTRAINT chk_approval_role_revision_append_only CHECK (deleted=0 AND delete_token=0 AND version=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Append-only role-org aggregate revision ledger';

DELIMITER $$
CREATE TRIGGER trg_approval_role_assignment_insert_guard BEFORE INSERT ON approval_role_assignment FOR EACH ROW
BEGIN
    DECLARE v_revision BIGINT;
    DECLARE v_role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin;
    DECLARE v_role_enterprise VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin;
    SELECT current_revision INTO v_revision FROM approval_role_revision_head
      WHERE enterprise_id=NEW.enterprise_id AND organization_id=NEW.organization_id
        AND role_code=NEW.role_code AND deleted=0 FOR UPDATE;
    SELECT role_code,enterprise_id INTO v_role_code,v_role_enterprise FROM approval_role WHERE id=NEW.role_id AND deleted=0;
    IF BINARY v_role_code<>BINARY NEW.role_code OR BINARY v_role_enterprise<>BINARY NEW.enterprise_id THEN
      SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,MESSAGE_TEXT='APPROVAL_ROLE_ASSIGNMENT_ROLE_MISMATCH';
    END IF;
    IF EXISTS (SELECT 1 FROM approval_role_assignment
      WHERE enterprise_id=NEW.enterprise_id AND organization_id=NEW.organization_id
        AND role_id=NEW.role_id AND user_id=NEW.user_id AND source_system=NEW.source_system
        AND source_reference=NEW.source_reference AND status='ACTIVE' AND deleted=0
        AND (effective_to IS NULL OR NEW.effective_from<effective_to)
        AND (NEW.effective_to IS NULL OR effective_from<NEW.effective_to)) THEN
      SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,MESSAGE_TEXT='APPROVAL_ROLE_ASSIGNMENT_OVERLAP';
    END IF;
END$$

CREATE TRIGGER trg_approval_role_assignment_update_guard BEFORE UPDATE ON approval_role_assignment FOR EACH ROW
BEGIN
    IF NEW.id<>OLD.id OR BINARY NEW.enterprise_id<>BINARY OLD.enterprise_id OR NEW.organization_id<>OLD.organization_id
      OR NEW.role_id<>OLD.role_id OR BINARY NEW.role_code<>BINARY OLD.role_code OR NEW.user_id<>OLD.user_id
      OR NEW.effective_from<>OLD.effective_from OR BINARY NEW.source_type<>BINARY OLD.source_type
      OR BINARY NEW.source_system<>BINARY OLD.source_system OR BINARY NEW.source_reference<>BINARY OLD.source_reference
      OR BINARY NEW.source_reference_hash<>BINARY OLD.source_reference_hash OR NEW.evidence_priority<>OLD.evidence_priority
      OR NEW.recorded_at<>OLD.recorded_at OR BINARY NEW.assignment_key_hash<>BINARY OLD.assignment_key_hash
      OR NEW.deleted<>OLD.deleted OR NEW.delete_token<>OLD.delete_token OR NEW.version<>OLD.version+1
      OR OLD.status<>'ACTIVE' OR NEW.status NOT IN ('ENDED','CORRECTED')
      OR (NEW.status='ENDED' AND (NEW.effective_to IS NULL OR NEW.effective_to<=OLD.effective_from))
      OR (NEW.status='CORRECTED' AND NOT (NEW.effective_to<=>OLD.effective_to)) THEN
      SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,MESSAGE_TEXT='APPROVAL_ROLE_ASSIGNMENT_GOVERNED_UPDATE_ONLY';
    END IF;
END$$

CREATE TRIGGER trg_approval_role_assignment_delete_guard BEFORE DELETE ON approval_role_assignment FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,MESSAGE_TEXT='APPROVAL_ROLE_ASSIGNMENT_DELETE_FORBIDDEN'; END$$
CREATE TRIGGER trg_approval_role_revision_update_guard BEFORE UPDATE ON approval_role_revision FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,MESSAGE_TEXT='APPROVAL_ROLE_REVISION_APPEND_ONLY'; END$$
CREATE TRIGGER trg_approval_role_revision_delete_guard BEFORE DELETE ON approval_role_revision FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,MESSAGE_TEXT='APPROVAL_ROLE_REVISION_APPEND_ONLY'; END$$
CREATE TRIGGER trg_approval_role_revision_head_update_guard BEFORE UPDATE ON approval_role_revision_head FOR EACH ROW
BEGIN
    IF NEW.current_revision<>OLD.current_revision+1 OR NEW.version<>OLD.version+1
      OR NEW.current_result_hash IS NULL OR NOT REGEXP_LIKE(NEW.current_result_hash,'^[0-9a-f]{64}$','c')
      OR NEW.id<>OLD.id OR BINARY NEW.enterprise_id<>BINARY OLD.enterprise_id
      OR NEW.organization_id<>OLD.organization_id OR BINARY NEW.role_code<>BINARY OLD.role_code
      OR NEW.deleted<>OLD.deleted OR NEW.delete_token<>OLD.delete_token THEN
      SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,MESSAGE_TEXT='APPROVAL_ROLE_REVISION_HEAD_CAS_ONLY';
    END IF;
END$$
CREATE TRIGGER trg_approval_role_revision_head_delete_guard BEFORE DELETE ON approval_role_revision_head FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO=1644,MESSAGE_TEXT='APPROVAL_ROLE_REVISION_HEAD_DELETE_FORBIDDEN'; END$$
DELIMITER ;
