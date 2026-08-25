-- Workflow V1 RC2-S1: version-level resolver binding and release manifest foundation.
-- CANDIDATE / NOT_EXECUTED. Requires immutable V2.6.20.
-- This migration does not enable ROLE Runtime, Canary, Candidate Pool, Claim, or Admission.
USE enterprise_platform;

ALTER TABLE workflow_version
    ADD COLUMN resolver_binding_model VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'LEGACY_USER_ONLY'
        COMMENT 'LEGACY_USER_ONLY/VERSION_RESOLVER_BINDING_CAPABLE'
        AFTER content_hash_algorithm,
    ADD COLUMN resolver_binding_manifest_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Published version resolver binding manifest SHA-256'
        AFTER resolver_binding_model,
    ADD COLUMN resolver_binding_count INT NOT NULL DEFAULT 0
        COMMENT 'Number of active version-level resolver bindings'
        AFTER resolver_binding_manifest_hash,
    ADD COLUMN resolver_binding_canonical_version VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Version binding manifest canonical contract'
        AFTER resolver_binding_count,
    ADD CONSTRAINT chk_workflow_version_resolver_binding_model CHECK (
        resolver_binding_model IN ('LEGACY_USER_ONLY', 'VERSION_RESOLVER_BINDING_CAPABLE')
    ),
    ADD CONSTRAINT chk_workflow_version_resolver_binding_snapshot CHECK (
        (
            resolver_binding_model = 'LEGACY_USER_ONLY'
            AND resolver_binding_manifest_hash IS NULL
            AND resolver_binding_count = 0
            AND resolver_binding_canonical_version IS NULL
        )
        OR
        (
            resolver_binding_model = 'VERSION_RESOLVER_BINDING_CAPABLE'
            AND (
                (
                    status = 'DRAFT'
                    AND (
                        (
                            resolver_binding_manifest_hash IS NULL
                            AND resolver_binding_count = 0
                            AND resolver_binding_canonical_version IS NULL
                        )
                        OR
                        (
                            resolver_binding_manifest_hash IS NOT NULL
                            AND resolver_binding_manifest_hash REGEXP '^[0-9a-f]{64}$'
                            AND resolver_binding_count > 0
                            AND resolver_binding_canonical_version IS NOT NULL
                            AND resolver_binding_canonical_version =
                                'VERSION_RESOLVER_BINDING_MANIFEST_V1'
                        )
                    )
                )
                OR
                (
                    status IN ('PUBLISHED', 'RETIRED')
                    AND resolver_binding_manifest_hash IS NOT NULL
                    AND resolver_binding_manifest_hash REGEXP '^[0-9a-f]{64}$'
                    AND resolver_binding_count > 0
                    AND resolver_binding_canonical_version IS NOT NULL
                    AND resolver_binding_canonical_version =
                        'VERSION_RESOLVER_BINDING_MANIFEST_V1'
                )
            )
        )
    );

ALTER TABLE workflow_version_release
    ADD COLUMN resolver_binding_model VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin
        NOT NULL DEFAULT 'LEGACY_USER_ONLY'
        COMMENT 'Resolver binding model frozen at publication'
        AFTER content_hash_algorithm,
    ADD COLUMN resolver_binding_manifest_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Resolver binding manifest SHA-256 frozen at publication'
        AFTER resolver_binding_model,
    ADD COLUMN resolver_binding_count INT NOT NULL DEFAULT 0
        COMMENT 'Resolver binding count frozen at publication'
        AFTER resolver_binding_manifest_hash,
    ADD COLUMN resolver_binding_canonical_version VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Resolver binding canonical contract frozen at publication'
        AFTER resolver_binding_count,
    ADD CONSTRAINT chk_workflow_release_resolver_binding_model CHECK (
        resolver_binding_model IN ('LEGACY_USER_ONLY', 'VERSION_RESOLVER_BINDING_CAPABLE')
    ),
    ADD CONSTRAINT chk_workflow_release_resolver_binding_snapshot CHECK (
        (
            resolver_binding_model = 'LEGACY_USER_ONLY'
            AND resolver_binding_manifest_hash IS NULL
            AND resolver_binding_count = 0
            AND resolver_binding_canonical_version IS NULL
        )
        OR
        (
            resolver_binding_model = 'VERSION_RESOLVER_BINDING_CAPABLE'
            AND resolver_binding_manifest_hash IS NOT NULL
            AND resolver_binding_manifest_hash REGEXP '^[0-9a-f]{64}$'
            AND resolver_binding_count > 0
            AND resolver_binding_canonical_version IS NOT NULL
            AND resolver_binding_canonical_version =
                'VERSION_RESOLVER_BINDING_MANIFEST_V1'
        )
    );

CREATE TABLE workflow_version_node_resolver_binding (
    id BIGINT NOT NULL COMMENT 'Version-level node resolver binding ID',
    definition_id BIGINT NOT NULL COMMENT 'Owning Workflow definition ID',
    definition_version_id BIGINT NOT NULL COMMENT 'Owning Workflow version ID',
    node_id BIGINT NOT NULL COMMENT 'Node owned by definition_version_id',
    binding_order INT NOT NULL COMMENT 'Stable semantic order within the node, starting at 1',
    resolver_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_contract_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    strategy_type VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolver_mode VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_type VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    organization_scope_type VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    organization_id BIGINT NULL COMMENT 'Required only for FIXED_ORG; logical Organization reference',
    effective_time_policy VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_schema_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_version_node_binding_order (
        definition_version_id, node_id, binding_order, delete_token
    ),
    UNIQUE KEY uk_workflow_version_node_binding_owner (
        id, definition_version_id, node_id
    ),
    KEY idx_workflow_version_node_binding_version (
        definition_version_id, deleted, binding_order
    ),
    KEY idx_workflow_version_node_binding_resolver (
        resolver_code, resolver_version, deleted
    ),
    KEY idx_workflow_version_node_binding_scope (
        definition_id, role_code, organization_scope_type, organization_id, deleted
    ),
    CONSTRAINT fk_workflow_version_node_binding_version
        FOREIGN KEY (definition_id, definition_version_id)
        REFERENCES workflow_version(definition_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_workflow_version_node_binding_node
        FOREIGN KEY (definition_version_id, node_id)
        REFERENCES workflow_node(version_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_version_node_binding_order CHECK (binding_order >= 1),
    CONSTRAINT chk_workflow_version_node_binding_resolver CHECK (
        resolver_code = 'ROLE_DIRECTORY'
        AND resolver_version = 'ROLE_DIRECTORY_V1'
        AND strategy_type = 'ROLE'
        AND resolver_mode = 'CANDIDATE_POOL'
        AND target_type = 'ROLE'
    ),
    CONSTRAINT chk_workflow_version_node_binding_role_code CHECK (
        REGEXP_LIKE(role_code, '^[A-Z][A-Z0-9_]{2,99}$', 'c')
    ),
    CONSTRAINT chk_workflow_version_node_binding_org_scope CHECK (
        (
            organization_scope_type = 'FIXED_ORG'
            AND organization_id IS NOT NULL
            AND organization_id > 0
        )
        OR
        (
            organization_scope_type = 'INSTANCE_BUSINESS_ORG'
            AND organization_id IS NULL
        )
    ),
    CONSTRAINT chk_workflow_version_node_binding_effective_time CHECK (
        effective_time_policy = 'NODE_ACTIVATED_AT'
    ),
    CONSTRAINT chk_workflow_version_node_binding_schema CHECK (
        binding_schema_version = 'VERSION_NODE_RESOLVER_BINDING_V1'
    ),
    CONSTRAINT chk_workflow_version_node_binding_hashes CHECK (
        resolver_contract_hash REGEXP '^[0-9a-f]{64}$'
        AND binding_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_workflow_version_node_binding_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_workflow_version_node_binding_optimistic CHECK (version >= 0),
    CONSTRAINT chk_workflow_version_node_binding_delete_token CHECK (
        (deleted = 0 AND delete_token = 0)
        OR (deleted = 1 AND delete_token = id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='DRAFT-owned node resolver configuration; immutable after Version publication';

CREATE TABLE workflow_version_resolver_binding_manifest (
    id BIGINT NOT NULL COMMENT 'Immutable resolver binding manifest ID',
    definition_id BIGINT NOT NULL COMMENT 'Owning Workflow definition ID',
    definition_version_id BIGINT NOT NULL COMMENT 'Published Workflow version ID',
    canonical_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_count INT NOT NULL,
    manifest_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    released_by BIGINT NOT NULL COMMENT 'Publisher user ID',
    released_time DATETIME(3) NOT NULL COMMENT 'Publication transaction time',
    created_by VARCHAR(64) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted SMALLINT NOT NULL DEFAULT 0,
    delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_version_binding_manifest_version (
        definition_id, definition_version_id
    ),
    KEY idx_workflow_version_binding_manifest_hash (manifest_hash),
    KEY idx_workflow_version_binding_manifest_release (released_time, definition_id),
    CONSTRAINT fk_workflow_version_binding_manifest_version
        FOREIGN KEY (definition_id, definition_version_id)
        REFERENCES workflow_version(definition_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_workflow_version_binding_manifest_canonical CHECK (
        canonical_version = 'VERSION_RESOLVER_BINDING_MANIFEST_V1'
    ),
    CONSTRAINT chk_workflow_version_binding_manifest_count CHECK (binding_count > 0),
    CONSTRAINT chk_workflow_version_binding_manifest_hash CHECK (
        manifest_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_workflow_version_binding_manifest_release CHECK (
        released_by > 0
    ),
    CONSTRAINT chk_workflow_version_binding_manifest_immutable CHECK (
        deleted = 0 AND delete_token = 0 AND version = 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only publication manifest for Version-level resolver bindings';

DELIMITER $$

CREATE TRIGGER trg_workflow_version_node_binding_insert_guard
BEFORE INSERT ON workflow_version_node_resolver_binding
FOR EACH ROW
BEGIN
    DECLARE matched_version BIGINT DEFAULT 0;
    DECLARE owner_status VARCHAR(20) DEFAULT NULL;
    SELECT COUNT(*), MAX(status)
      INTO matched_version, owner_status
      FROM workflow_version
     WHERE definition_id = NEW.definition_id
       AND id = NEW.definition_version_id
       AND deleted = 0;
    IF matched_version <> 1 OR owner_status <> 'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'WORKFLOW_VERSION_BINDING_REQUIRES_DRAFT_VERSION';
    END IF;
END$$

CREATE TRIGGER trg_workflow_version_node_binding_update_guard
BEFORE UPDATE ON workflow_version_node_resolver_binding
FOR EACH ROW
BEGIN
    DECLARE matched_version BIGINT DEFAULT 0;
    DECLARE owner_status VARCHAR(20) DEFAULT NULL;
    IF NEW.id <> OLD.id
       OR NEW.definition_id <> OLD.definition_id
       OR NEW.definition_version_id <> OLD.definition_version_id
       OR NEW.node_id <> OLD.node_id THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'WORKFLOW_VERSION_BINDING_OWNER_IMMUTABLE';
    END IF;
    SELECT COUNT(*), MAX(status)
      INTO matched_version, owner_status
      FROM workflow_version
     WHERE definition_id = OLD.definition_id
       AND id = OLD.definition_version_id
       AND deleted = 0;
    IF matched_version <> 1 OR owner_status <> 'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'WORKFLOW_VERSION_BINDING_IMMUTABLE';
    END IF;
END$$

CREATE TRIGGER trg_workflow_version_node_binding_delete_guard
BEFORE DELETE ON workflow_version_node_resolver_binding
FOR EACH ROW
BEGIN
    DECLARE matched_version BIGINT DEFAULT 0;
    DECLARE owner_status VARCHAR(20) DEFAULT NULL;
    SELECT COUNT(*), MAX(status)
      INTO matched_version, owner_status
      FROM workflow_version
     WHERE definition_id = OLD.definition_id
       AND id = OLD.definition_version_id
       AND deleted = 0;
    IF matched_version <> 1 OR owner_status <> 'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'WORKFLOW_VERSION_BINDING_IMMUTABLE';
    END IF;
END$$

CREATE TRIGGER trg_workflow_version_binding_manifest_insert_guard
BEFORE INSERT ON workflow_version_resolver_binding_manifest
FOR EACH ROW
BEGIN
    DECLARE matched_version BIGINT DEFAULT 0;
    DECLARE owner_status VARCHAR(20) DEFAULT NULL;
    DECLARE owner_model VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE owner_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE owner_count INT DEFAULT NULL;
    DECLARE owner_canonical VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE actual_binding_count INT DEFAULT 0;

    SELECT COUNT(*), MAX(status), MAX(resolver_binding_model),
           MAX(resolver_binding_manifest_hash), MAX(resolver_binding_count),
           MAX(resolver_binding_canonical_version)
      INTO matched_version, owner_status, owner_model,
           owner_hash, owner_count, owner_canonical
      FROM workflow_version
     WHERE definition_id = NEW.definition_id
       AND id = NEW.definition_version_id
       AND deleted = 0;

    SELECT COUNT(*) INTO actual_binding_count
      FROM workflow_version_node_resolver_binding
     WHERE definition_id = NEW.definition_id
       AND definition_version_id = NEW.definition_version_id
       AND deleted = 0;

    IF matched_version <> 1
       OR owner_status <> 'DRAFT'
       OR owner_model <> 'VERSION_RESOLVER_BINDING_CAPABLE'
       OR NOT (owner_hash <=> NEW.manifest_hash)
       OR owner_count <> NEW.binding_count
       OR NOT (owner_canonical <=> NEW.canonical_version)
       OR actual_binding_count <> NEW.binding_count THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'WORKFLOW_VERSION_BINDING_MANIFEST_INVALID';
    END IF;
END$$

CREATE TRIGGER trg_workflow_version_binding_manifest_no_update
BEFORE UPDATE ON workflow_version_resolver_binding_manifest
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
        MESSAGE_TEXT = 'WORKFLOW_VERSION_BINDING_MANIFEST_APPEND_ONLY';
END$$

CREATE TRIGGER trg_workflow_version_binding_manifest_no_delete
BEFORE DELETE ON workflow_version_resolver_binding_manifest
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
        MESSAGE_TEXT = 'WORKFLOW_VERSION_BINDING_MANIFEST_APPEND_ONLY';
END$$

CREATE TRIGGER trg_workflow_version_release_binding_insert_guard
BEFORE INSERT ON workflow_version_release
FOR EACH ROW
BEGIN
    DECLARE matched_version BIGINT DEFAULT 0;
    DECLARE published_status VARCHAR(20) DEFAULT NULL;
    DECLARE published_content_hash VARCHAR(128) DEFAULT NULL;
    DECLARE published_model VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE published_manifest_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE published_binding_count INT DEFAULT NULL;
    DECLARE published_canonical VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    DECLARE matching_manifest BIGINT DEFAULT 0;

    SELECT COUNT(*), MAX(status), MAX(content_hash), MAX(resolver_binding_model),
           MAX(resolver_binding_manifest_hash), MAX(resolver_binding_count),
           MAX(resolver_binding_canonical_version)
      INTO matched_version, published_status, published_content_hash, published_model,
           published_manifest_hash, published_binding_count, published_canonical
      FROM workflow_version
     WHERE definition_id = NEW.definition_id
       AND id = NEW.published_version_id
       AND deleted = 0;

    IF matched_version <> 1
       OR published_status <> 'PUBLISHED'
       OR BINARY published_content_hash <> BINARY NEW.content_hash
       OR BINARY published_model <> BINARY NEW.resolver_binding_model
       OR NOT (published_manifest_hash <=> NEW.resolver_binding_manifest_hash)
       OR published_binding_count <> NEW.resolver_binding_count
       OR NOT (published_canonical <=> NEW.resolver_binding_canonical_version) THEN
        SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
            MESSAGE_TEXT = 'WORKFLOW_VERSION_RELEASE_BINDING_SNAPSHOT_MISMATCH';
    END IF;

    IF NEW.resolver_binding_model = 'VERSION_RESOLVER_BINDING_CAPABLE' THEN
        SELECT COUNT(*) INTO matching_manifest
          FROM workflow_version_resolver_binding_manifest
         WHERE definition_id = NEW.definition_id
           AND definition_version_id = NEW.published_version_id
           AND manifest_hash = NEW.resolver_binding_manifest_hash
           AND binding_count = NEW.resolver_binding_count
           AND canonical_version = NEW.resolver_binding_canonical_version
           AND deleted = 0;
        IF matching_manifest <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
                MESSAGE_TEXT = 'WORKFLOW_VERSION_RELEASE_MANIFEST_MISSING';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_workflow_version_release_no_update
BEFORE UPDATE ON workflow_version_release
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
        MESSAGE_TEXT = 'WORKFLOW_VERSION_RELEASE_APPEND_ONLY';
END$$

CREATE TRIGGER trg_workflow_version_release_no_delete
BEFORE DELETE ON workflow_version_release
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
        MESSAGE_TEXT = 'WORKFLOW_VERSION_RELEASE_APPEND_ONLY';
END$$

DELIMITER ;
