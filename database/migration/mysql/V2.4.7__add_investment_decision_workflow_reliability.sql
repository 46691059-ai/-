-- Sprint 2-3.3: local Workflow integration reliability and decision RBAC metadata.
-- Workflow remains external; these tables store only delivery and idempotency state.
USE enterprise_platform;

CREATE TABLE investment_workflow_outbox (
    id BIGINT NOT NULL,
    decision_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    payload_json TEXT NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3) NULL,
    last_error_code VARCHAR(100) NULL,
    published_time DATETIME(3) NULL,
    trace_id VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0, delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_workflow_outbox_key (idempotency_key, event_type, delete_token),
    KEY idx_inv_workflow_outbox_dispatch (status, next_retry_time, create_time, deleted),
    KEY idx_inv_workflow_outbox_decision (decision_id, create_time, deleted),
    CONSTRAINT fk_inv_workflow_outbox_decision FOREIGN KEY (decision_id) REFERENCES investment_decision(id),
    CONSTRAINT fk_inv_workflow_outbox_binding FOREIGN KEY (binding_id) REFERENCES investment_workflow_binding(id),
    CONSTRAINT chk_inv_workflow_outbox_type CHECK (event_type IN ('START_WORKFLOW','WITHDRAW_WORKFLOW')),
    CONSTRAINT chk_inv_workflow_outbox_status CHECK (status IN ('PENDING','PROCESSING','PUBLISHED','FAILED','DEAD')),
    CONSTRAINT chk_inv_workflow_outbox_retry CHECK (retry_count >= 0),
    CONSTRAINT chk_inv_workflow_outbox_deleted CHECK (deleted IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资决策Workflow事务发件箱';

CREATE TABLE investment_workflow_inbox (
    id BIGINT NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    workflow_instance_id VARCHAR(100) NOT NULL,
    decision_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    snapshot_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    event_sequence BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    process_status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(100) NULL,
    received_time DATETIME(3) NOT NULL,
    processed_time DATETIME(3) NULL,
    trace_id VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), create_by VARCHAR(64) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), update_by VARCHAR(64) NULL,
    deleted SMALLINT NOT NULL DEFAULT 0, delete_token BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inv_workflow_inbox_event (event_id, delete_token),
    UNIQUE KEY uk_inv_workflow_inbox_sequence (workflow_instance_id, event_sequence, delete_token),
    KEY idx_inv_workflow_inbox_buffer (process_status, received_time, deleted),
    KEY idx_inv_workflow_inbox_decision (decision_id, received_time, deleted),
    CONSTRAINT fk_inv_workflow_inbox_decision FOREIGN KEY (decision_id) REFERENCES investment_decision(id),
    CONSTRAINT fk_inv_workflow_inbox_binding FOREIGN KEY (binding_id) REFERENCES investment_workflow_binding(id),
    CONSTRAINT fk_inv_workflow_inbox_snapshot FOREIGN KEY (decision_id, snapshot_id) REFERENCES investment_decision_snapshot(decision_id,id),
    CONSTRAINT chk_inv_workflow_inbox_attempt CHECK (attempt_no > 0),
    CONSTRAINT chk_inv_workflow_inbox_sequence CHECK (event_sequence > 0),
    CONSTRAINT chk_inv_workflow_inbox_status CHECK (process_status IN ('PROCESSED','DUPLICATE','BUFFERED','FAILED')),
    CONSTRAINT chk_inv_workflow_inbox_deleted CHECK (deleted IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资决策Workflow幂等收件箱';

START TRANSACTION;
INSERT INTO sys_permission (id,permission_name,permission_code,permission_type,module_code,status,create_time,create_by,update_time,update_by,deleted,delete_token,remark,version)
SELECT s.id,s.name,s.code,'BUTTON','investment',1,CURRENT_TIMESTAMP(3),'system',CURRENT_TIMESTAMP(3),'system',0,0,'Sprint 2-3.3 投资决策权限',0
FROM (
 SELECT 2741 id,'投资决策查看' name,'investment:decision:view' code
 UNION ALL SELECT 2742,'投资决策创建','investment:decision:create'
 UNION ALL SELECT 2743,'投资决策提交','investment:decision:submit'
 UNION ALL SELECT 2744,'投资决策撤回','investment:decision:withdraw'
) s WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.permission_code=s.code AND p.deleted=0);

INSERT INTO sys_menu (id,menu_name,parent_id,menu_type,path,component,permission,sort_no,visible,status,create_time,create_by,update_time,update_by,deleted,delete_token,remark,version)
SELECT s.id,s.name,parent.id,'B',NULL,NULL,s.permission,s.sort_no,0,1,CURRENT_TIMESTAMP(3),'system',CURRENT_TIMESTAMP(3),'system',0,0,'Sprint 2-3.3 投资决策按钮',0
FROM (
 SELECT 632 id,'决策查看' name,'investment:decision:view' permission,1 sort_no
 UNION ALL SELECT 633,'决策创建','investment:decision:create',2
 UNION ALL SELECT 634,'决策提交','investment:decision:submit',3
 UNION ALL SELECT 635,'决策撤回','investment:decision:withdraw',4
) s JOIN sys_menu parent ON parent.path='/investment/decisions' AND parent.menu_type='C' AND parent.deleted=0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.parent_id=parent.id AND m.permission=s.permission AND m.deleted=0);

INSERT INTO sys_role_permission (id,role_id,permission_id,create_time,create_by,update_time,update_by,deleted,delete_token,remark,version)
SELECT 940000000+r.id*10000+p.id,r.id,p.id,CURRENT_TIMESTAMP(3),'system',CURRENT_TIMESTAMP(3),'system',0,0,'超级管理员投资决策权限',0
FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('investment:decision:view','investment:decision:create','investment:decision:submit','investment:decision:withdraw') AND p.deleted=0
WHERE r.role_code='SUPER_ADMIN' AND r.deleted=0 AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id AND rp.deleted=0);

INSERT INTO sys_role_menu (id,role_id,menu_id,create_time,create_by,update_time,update_by,deleted,delete_token,remark,version)
SELECT 950000000+r.id*10000+m.id,r.id,m.id,CURRENT_TIMESTAMP(3),'system',CURRENT_TIMESTAMP(3),'system',0,0,'超级管理员投资决策菜单',0
FROM sys_role r JOIN sys_menu m ON m.id IN (632,633,634,635) AND m.deleted=0
WHERE r.role_code='SUPER_ADMIN' AND r.deleted=0 AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.deleted=0);
COMMIT;
