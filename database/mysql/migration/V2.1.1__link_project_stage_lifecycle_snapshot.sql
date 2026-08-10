-- Adds nullable Lifecycle V2 links to the existing runtime stage table.
-- Columns remain nullable so LEGACY backfill can be validated before constraints are tightened.
USE enterprise_platform;

ALTER TABLE project_stage
    ADD COLUMN lifecycle_instance_id BIGINT NULL;

ALTER TABLE project_stage
    ADD COLUMN stage_snapshot_id BIGINT NULL;

CREATE INDEX idx_project_stage_lifecycle
    ON project_stage (lifecycle_instance_id, deleted, stage_order);

CREATE UNIQUE INDEX uk_project_stage_snapshot
    ON project_stage (stage_snapshot_id, delete_token);

ALTER TABLE project_stage
    ADD CONSTRAINT fk_project_stage_lifecycle_instance FOREIGN KEY (lifecycle_instance_id)
        REFERENCES project_lifecycle_instance(id);

ALTER TABLE project_stage
    ADD CONSTRAINT fk_project_stage_snapshot FOREIGN KEY (stage_snapshot_id, project_id)
        REFERENCES project_lifecycle_stage_snapshot(id, project_id);
