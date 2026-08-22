-- Sprint 2-3.7-WF4.2.3: forward-only Claim aggregate integrity governance.
-- Requires V2.6.7. Does not repair, delete, infer, or backfill invalid business data.
USE enterprise_platform;

-- Phase A: fail on dirty data before the first permanent DDL statement.
CREATE TEMPORARY TABLE tmp_v268_integrity_guard (
    violation_code VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    violation_count BIGINT NOT NULL,
    PRIMARY KEY (violation_code),
    CONSTRAINT chk_tmp_v268_no_violation CHECK (violation_count = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'CLAIM_NODE_EXECUTION_MISSING_OR_CROSS_INSTANCE', COUNT(*)
FROM workflow_task_claim c
LEFT JOIN workflow_node_execution ne
  ON ne.id = c.node_execution_id AND ne.instance_id = c.instance_id
WHERE ne.id IS NULL;

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'CLAIM_TASK_OWNER_MISMATCH', COUNT(*)
FROM workflow_task_claim c
LEFT JOIN workflow_task t
  ON t.id = c.task_id
 AND t.instance_id = c.instance_id
 AND t.node_execution_id = c.node_execution_id
WHERE t.id IS NULL;

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'CLAIM_POOL_OWNER_MISMATCH', COUNT(*)
FROM workflow_task_claim c
LEFT JOIN workflow_task_candidate_pool p
  ON p.id = c.candidate_pool_id
 AND p.task_id = c.task_id
 AND p.instance_id = c.instance_id
 AND p.node_execution_id = c.node_execution_id
WHERE p.id IS NULL;

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'CLAIM_MEMBER_OWNER_MISMATCH', COUNT(*)
FROM workflow_task_claim c
LEFT JOIN workflow_task_candidate_member m
  ON m.id = c.candidate_member_id
 AND m.pool_id = c.candidate_pool_id
 AND m.task_id = c.task_id
 AND m.instance_id = c.instance_id
 AND m.candidate_user_id = c.candidate_user_id
WHERE m.id IS NULL;

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'SUCCESS_AUDIT_CLAIM_MISSING', COUNT(*)
FROM workflow_task_claim_audit a
LEFT JOIN workflow_task_claim c ON c.id = a.claim_id
WHERE a.result = 'SUCCESS' AND (a.claim_id IS NULL OR c.id IS NULL);

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'SUCCESS_AUDIT_OWNER_MISMATCH', COUNT(*)
FROM workflow_task_claim_audit a
JOIN workflow_task_claim c ON c.id = a.claim_id
WHERE a.result = 'SUCCESS'
  AND (
       NOT (a.task_id <=> c.task_id)
    OR NOT (a.instance_id <=> c.instance_id)
    OR NOT (a.node_execution_id <=> c.node_execution_id)
    OR NOT (a.candidate_pool_id <=> c.candidate_pool_id)
    OR NOT (a.candidate_member_id <=> c.candidate_member_id)
    OR NOT (a.claimant_user_id <=> c.candidate_user_id)
    OR NOT (a.operator_user_id <=> c.operator_user_id)
  );

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'SUCCESS_AUDIT_EVIDENCE_INVALID', COUNT(*)
FROM workflow_task_claim_audit a
WHERE a.result = 'SUCCESS'
  AND (
       a.event_type <> 'CLAIM'
    OR a.frozen_eligibility_hash IS NULL
    OR a.realtime_eligibility_result IS NULL
    OR a.rbac_result IS NULL
    OR a.data_scope_result IS NULL
    OR a.sod_result IS NULL
    OR a.task_status_before IS NULL
    OR a.task_status_after IS NULL
    OR a.pool_status_before IS NULL
    OR a.pool_status_after IS NULL
  );

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'DUPLICATE_ACTIVE_CLAIM', COUNT(*)
FROM (
    SELECT task_id
    FROM workflow_task_claim
    WHERE status = 'CLAIMED' AND active_token = 0 AND deleted = 0
    GROUP BY task_id
    HAVING COUNT(*) > 1
) duplicate_claims;

-- Phase B: parent candidate keys required by the composite ownership foreign keys.
ALTER TABLE workflow_task
    ADD UNIQUE KEY uk_workflow_task_claim_owner (id, instance_id, node_execution_id);

ALTER TABLE workflow_task_candidate_pool
    ADD UNIQUE KEY uk_workflow_candidate_pool_claim_owner (
        id, task_id, instance_id, node_execution_id
    );

ALTER TABLE workflow_task_claim
    ADD UNIQUE KEY uk_workflow_task_claim_audit_owner (
        id, task_id, instance_id, node_execution_id,
        candidate_pool_id, candidate_member_id, candidate_user_id, operator_user_id
    );

-- Phase C: Claim must belong to one Task/Instance/NodeExecution/Pool/Member aggregate.
ALTER TABLE workflow_task_claim
    ADD CONSTRAINT fk_workflow_task_claim_task_execution
        FOREIGN KEY (task_id, instance_id, node_execution_id)
        REFERENCES workflow_task(id, instance_id, node_execution_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_workflow_task_claim_node_execution
        FOREIGN KEY (instance_id, node_execution_id)
        REFERENCES workflow_node_execution(instance_id, id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_workflow_task_claim_pool_execution
        FOREIGN KEY (candidate_pool_id, task_id, instance_id, node_execution_id)
        REFERENCES workflow_task_candidate_pool(id, task_id, instance_id, node_execution_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- Phase D: every redundant SUCCESS Audit owner field is constrained to the same Claim fact.
ALTER TABLE workflow_task_claim_audit
    ADD CONSTRAINT fk_workflow_task_claim_audit_owner
        FOREIGN KEY (
            claim_id, task_id, instance_id, node_execution_id,
            candidate_pool_id, candidate_member_id, claimant_user_id, operator_user_id
        )
        REFERENCES workflow_task_claim (
            id, task_id, instance_id, node_execution_id,
            candidate_pool_id, candidate_member_id, candidate_user_id, operator_user_id
        )
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- Phase E: SUCCESS is a complete, append-only Claim fact. Rejected attempts remain
-- in the platform security audit and are not forged as successful Claim events.
ALTER TABLE workflow_task_claim_audit
    ADD CONSTRAINT chk_workflow_task_claim_audit_success_owner CHECK (
        result <> 'SUCCESS'
        OR (
            event_type = 'CLAIM'
            AND claim_id IS NOT NULL
            AND task_id IS NOT NULL
            AND instance_id IS NOT NULL
            AND node_execution_id IS NOT NULL
            AND candidate_pool_id IS NOT NULL
            AND candidate_member_id IS NOT NULL
            AND claimant_user_id IS NOT NULL
            AND operator_user_id IS NOT NULL
            AND claimant_user_id = operator_user_id
            AND frozen_eligibility_hash IS NOT NULL
            AND realtime_eligibility_result IS NOT NULL
            AND CHAR_LENGTH(TRIM(realtime_eligibility_result)) > 0
            AND rbac_result IS NOT NULL AND CHAR_LENGTH(TRIM(rbac_result)) > 0
            AND data_scope_result IS NOT NULL AND CHAR_LENGTH(TRIM(data_scope_result)) > 0
            AND sod_result IS NOT NULL AND CHAR_LENGTH(TRIM(sod_result)) > 0
            AND task_status_before IS NOT NULL
            AND task_status_after IS NOT NULL
            AND pool_status_before IS NOT NULL
            AND pool_status_after IS NOT NULL
        )
    );

-- Database-level append-only defence. Repository/API immutability and restricted
-- application grants remain mandatory; these triggers are not the sole protection.
CREATE TRIGGER trg_workflow_task_claim_audit_no_update
BEFORE UPDATE ON workflow_task_claim_audit
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Workflow Claim audit is append-only';

CREATE TRIGGER trg_workflow_task_claim_audit_no_delete
BEFORE DELETE ON workflow_task_claim_audit
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Workflow Claim audit is append-only';

-- Phase F: assert that every permanent governance object is present and enabled.
INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'FINAL_REQUIRED_KEYS_MISSING', ABS(3 - COUNT(*))
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND (
       (table_name = 'workflow_task' AND index_name = 'uk_workflow_task_claim_owner')
    OR (table_name = 'workflow_task_candidate_pool'
        AND index_name = 'uk_workflow_candidate_pool_claim_owner')
    OR (table_name = 'workflow_task_claim'
        AND index_name = 'uk_workflow_task_claim_audit_owner')
  )
  AND seq_in_index = 1;

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'FINAL_REQUIRED_FOREIGN_KEYS_MISSING', ABS(4 - COUNT(*))
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN (
      'fk_workflow_task_claim_task_execution',
      'fk_workflow_task_claim_node_execution',
      'fk_workflow_task_claim_pool_execution',
      'fk_workflow_task_claim_audit_owner'
  );

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'FINAL_SUCCESS_CHECK_MISSING', ABS(1 - COUNT(*))
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name = 'workflow_task_claim_audit'
  AND constraint_name = 'chk_workflow_task_claim_audit_success_owner'
  AND constraint_type = 'CHECK';

INSERT INTO tmp_v268_integrity_guard (violation_code, violation_count)
SELECT 'FINAL_APPEND_ONLY_TRIGGERS_MISSING', ABS(2 - COUNT(*))
FROM information_schema.triggers
WHERE trigger_schema = DATABASE()
  AND trigger_name IN (
      'trg_workflow_task_claim_audit_no_update',
      'trg_workflow_task_claim_audit_no_delete'
  );

DROP TEMPORARY TABLE tmp_v268_integrity_guard;
