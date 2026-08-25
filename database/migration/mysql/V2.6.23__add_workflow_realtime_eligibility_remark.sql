-- Workflow V1 RC2-S7: close the audited-entity remark mapping contract only.
-- Fail-fast by design: target tables and the frozen predecessor shape must exist.

ALTER TABLE workflow_role_realtime_eligibility_capability_evidence
    ADD COLUMN remark VARCHAR(500) NULL AFTER delete_token;

ALTER TABLE workflow_role_realtime_eligibility_event
    ADD COLUMN remark VARCHAR(500) NULL AFTER delete_token;

ALTER TABLE workflow_role_realtime_eligibility_validator_evidence
    ADD COLUMN remark VARCHAR(500) NULL AFTER delete_token;
