# V2.6.14 Activation Evidence Forward Fix 治理报告

## 1. 最终结论

**BLOCKED / V2.6.14 NOT CREATED / ROLE_RUNTIME_DISABLED**

本 Sprint 要求“不修改 V2.6.13，同时通过 V2.6.14 修复”。该组合在当前真实状态下不可执行：

1. Flyway 按版本顺序先执行 V2.6.13；
2. V2.6.13 在创建 `role_runtime_activation_approval` 时以 MySQL `6125` 失败；
3. Flyway 写入 `success = 0` 的失败 history 并停止；
4. V2.6.14 不会被调度，因此无法修复前一版本的建表失败。

这与“低版本已成功执行、仅验收发现完整性不足”的正常 Forward Fix 不同。创建不可达的
V2.6.14 会制造虚假治理状态，因此本次未创建 SQL、未登记候选、未声称 Hardened。

## 2. 修改文件清单

- `docs/workflow-role-runtime-activation-evidence-v2614-forward-fix-implementation-report.md`
- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/README.md`

未修改任何 Migration SQL，未新增测试代码或业务代码。

## 3. V2.6.14 设计状态

| 项目 | 结果 |
|---|---|
| 文件 | 未创建 |
| 原因 | Flyway 无法越过失败的 V2.6.13 |
| Migration 状态 | `BLOCKED_NOT_CREATED` |
| 数据库执行 | 未执行 |
| V2.6.13 SHA-256 | 保持 `7ec6ec6441006dc7f114b92adfc60efdfb2cc8f66bda3a6a7a52d9fcbf3168e6` |
| ROLE Runtime | `DISABLED` |

## 4. 前向可执行性证据

WF5.10.1 已在 MySQL Community Server 8.4.9 / Flyway 13.0.0 的隔离环境验证：

- Fresh：V2.6.13 失败；
- V2.6.12 Upgrade：V2.6.13 失败；
- V2.6.8 Forward：V2.6.9–V2.6.12 成功，V2.6.13 失败；
- 三条路径 Flyway checksum：`1627294290`；
- 三条路径均留下 `role_runtime_activation_request` 单表和失败 history 行；
- V2.6.14 在任一路径都不可能执行。

## 5. 外键治理审计

V2.6.13 父表声明：

`UNIQUE (activation_id, delete_token)`

但 Approval/Evidence 子表设计引用：

`FOREIGN KEY (activation_id) REFERENCES request(activation_id)`

安全的候选修正应统一采用完整复合所有权键：

- Approval：`(activation_id, delete_token)` → Request 同名复合键；
- Evidence：`(activation_id, delete_token)` → Request 同名复合键；
- Evidence 新增 `approval_id`，并外键引用 Approval 主键；
- 必要时通过复合键同时证明 Approval 与 Request 属于同一 Activation。

不建议仅增加 `UNIQUE(activation_id)` 来绕过错误，因为这会形成与平台逻辑删除唯一键规范
并存的第二套身份语义。

## 6. Evidence 与 Hash 治理审计

当前 V2.6.13 的 Approval 表没有 `activation_hash / contract_hash / binding_hash`，Evidence 表
也只有通用 `evidence_hash`。因此用户要求的三项等值校验无法由 V2.6.14 简单增加 Trigger
完成，必须先冻结明确的结构契约：

- Approval 保存 Activation、Contract、Binding 三项批准快照；
- Evidence 保存对应三项引用值以及 `approval_id`；
- `BEFORE INSERT` Trigger 校验 Approval 存在且三项 Hash 完全一致；
- 所有 Hash 使用 `ASCII/ascii_bin` 和小写 SHA-256 格式约束；
- Approval/Evidence 保持 UPDATE、DELETE 全阻断。

## 7. Guard 与 DDL 顺序审计

要求的“Guard 失败无永久对象残留”必须在首个永久 DDL 前完成。当前 V2.6.13 已证明 MySQL
DDL 自动提交会留下 Request 表。因此修订后的候选必须：

1. 用 Temporary Guard 一次性检查全部前置结构与历史数据；
2. Guard 成功后才创建任何目标表；
3. 将可在 DDL 前计算的一致性检查全部前移；
4. 对无法事务化的建表失败定义明确、人工审批的恢复 Runbook；
5. 禁止自动修复、删除历史数据或修改 Flyway history。

在目标表从未成功创建的 Fresh 路径中，“孤立 Request/Approval/Evidence 历史数据”不存在；
这些检查适用于受控恢复路径，不能代替 V2.6.13 本身可执行性的修复。

## 8. 测试结果

本 Sprint 没有可执行的 V2.6.14，因此未新增名为
`V2614ActivationEvidenceIntegrityTest` 的伪实现测试，也没有声称以下项目通过：

- Fresh/Upgrade/Forward Migration；
- 外键 6125 关闭；
- Evidence 强关联；
- Hash 交叉一致性；
- Trigger、事务、并发与 Schema Fingerprint。

沿用的证据只有 WF5.10.1：应用全量测试 432 项通过、Migration SHA 35/35 匹配，但这些
结果不能使失败的 V2.6.13 或不可达的 V2.6.14 获得晋级资格。

## 9. 禁止的绕过方式

以下方式均未执行且不得作为修复：

- 自动或未审批 `flyway repair`；
- 手工将 V2.6.13 history 改为成功；
- `ignoreMigrationPatterns`、out-of-order 或跳过失败版本；
- baseline 到 2.6.13 伪装其已执行；
- 手工补建表后继续 V2.6.14；
- 删除失败证据。

这些方式会造成 Schema 与 Flyway history 失真。

## 10. 风险与解锁条件

当前 P0 风险：

1. V2.6.13 不可执行且留下部分 DDL；
2. V2.6.14 在 Flyway 顺序链中不可达；
3. Approval→Evidence 缺少强所有权关系；
4. Hash 等值治理所需字段契约尚不完整；
5. 失败恢复需要新的受控权限与 Runbook。

唯一安全的解锁方案是：明确授权修改**从未晋级、从未成功执行**的 V2.6.13 候选，修复其
父子复合外键与 Evidence/Hash 契约，重新计算 SHA-256，并从全新隔离 Schema 重跑完整验收。

在获得该授权前：

- V2.6.13 保持 `CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`；
- V2.6.14 保持 `BLOCKED_NOT_CREATED`；
- 最终状态不得标记 `ROLE_RUNTIME_ACTIVATION_EVIDENCE_HARDENED`；
- `ROLE_RUNTIME_DISABLED` 保持不变；
- 不进入 WF5.11、ROLE Task、Candidate Pool、Claim 或 Investment Integration。
