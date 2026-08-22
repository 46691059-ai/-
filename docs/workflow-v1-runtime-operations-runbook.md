# Workflow V1.0 运行治理手册

## 1. 适用范围与当前状态

本手册适用于 Workflow V1.0 内部工程基线。当前唯一可执行业务 Resolver 为 `EXPLICIT_USER_V1`；`ROLE_DIRECTORY_V1` 保持 `PREPARED / NON_EXECUTABLE`，`ROLE_RUNTIME` 保持 `DISABLED`。真实 Directory、DataScope、平台/业务 SoD 等外部能力尚未完成 TEST/PREPROD 验收，不得以 Fake、Stub、Mock 或 Sandbox 结果替代。

## 2. Runtime 组件

运行链由 Definition/Version、Instance、NodeExecution、Task、Assignment、Candidate Pool、Claim、Activation、Binding、Admission、Realtime Eligibility、Evidence/Audit 组成。Domain 负责规则，Application 负责事务编排，Port 定义边界，Infrastructure 提供持久化与外部能力适配。

## 3. 正常运行边界

- `EXPLICIT_USER_V1`：`ACTIVE`，维持 USER + DIRECT 与 Legacy 行为。
- `ROLE_DIRECTORY_V1`：只能参与设计、Proposal、证据和治理校验；不得创建真实 ROLE Task、Pool 或 Claim。
- Feature Flag：`workflow.role-runtime.claim-enabled=false`。
- Canary：企业、定义、版本和节点范围默认全部为空，不得解释为全量。
- Kill Switch：默认 `STOP_NEW_AND_CLAIM`，阻止新的运行行为，但不删除或改写历史事实。

## 4. 故障处置矩阵

| 场景 | 首要动作 | 禁止动作 | 保全证据 |
|---|---|---|---|
| Directory 不可用/超时 | Fail Closed，保持 ROLE 不可执行 | fallback 到 USER、手选默认负责人 | 请求标识、契约/Revision、错误分类、时间 |
| Admission 失败 | 阻断后续执行 | 跳过 Admission、修改历史 Snapshot | Admission、Slot、Evidence、Event |
| Eligibility 失败 | 阻断 Claim | 重建 Pool、跳过 DataScope/SoD | 27 项 Validator 与 10 项 Capability Evidence |
| Claim 失败 | 保持 Task/Pool 一致，按幂等键查询原结果 | 手工改 assignee/status | Claim、Claim Audit、CAS/锁冲突信息 |
| 并发冲突 | 接受单赢家结果，失败方重读状态 | 反复无界重试 | 幂等键、版本号、事务错误码 |
| Hash 漂移 | 立即阻断并核对 Canonical 版本 | 重算覆盖历史 Hash | stored/computed hash、输入证据、算法版本 |
| Revision 漂移 | 阻断新执行并重新走批准流程 | 刷新历史 Pool/Snapshot | Directory Revision、effectiveAt、证据根 |
| 数据库异常 | 停止新写入，确认事务回滚和 Flyway 状态 | repair/baseline/手改 history | DB 日志、Flyway history、事务标识 |

## 5. Evidence 与 append-only 原则

Activation、Runtime Binding、Admission、Realtime Eligibility、Candidate、Claim 与 Audit 证据均为历史事实。数据库约束与 Trigger 保护的 append-only 对象不得 `UPDATE` 或 `DELETE`；状态变化通过追加新记录表达。禁止手工修复 Hash、Contract、Revision、Evidence Root、Approval 或 Snapshot。发生错误时隔离输入并追加审计事件，不覆盖原记录。

Kill Switch 只阻止新的启动、分配或 Claim 行为，不得删除历史 Admission、Evidence、CandidatePool、Claim、Audit、Runtime Binding 或 Activation 证据。

## 6. 恢复流程

1. 立即保持/切换 Kill Switch 为 `STOP_NEW_AND_CLAIM`，确认 ROLE Runtime 仍为 `DISABLED`。
2. 冻结事故窗口，记录应用版本、Schema 版本、Flyway history、配置引用与时间范围；禁止读取或复制 Secret 明文。
3. 校验 Migration SHA、Canonical 版本和 Evidence 链，区分代码、数据库、外部能力与数据问题。
4. 对幂等重试仅使用原幂等键；先查询既有结果，禁止生成替代 Task/Pool。
5. 数据库恢复遵循备份恢复或前向修复流程。Canonical Migration 不修改，Flyway 不自动 repair。
6. 只有外部能力、Security、RACI、Canary、监控与回滚门禁全部通过，才能由独立发布流程评估启用；本基线不得自行启用 ROLE Runtime。

## 7. 监控与告警建议

监控 Directory 延迟/错误率、Admission/Eligibility 拒绝率、Hash/Revision 漂移、Claim CAS 冲突、死锁/锁超时、证据写入失败、幂等冲突及 Kill Switch 状态。任何 Fail Open、fallback、证据缺口或状态越权均按 P0 处理。
