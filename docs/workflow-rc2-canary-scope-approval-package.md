# Workflow V1 RC2 Canary Scope Approval Readiness & Approval Package

## 1. 结论与非授权声明

本包绑定 `workflow-v1.0.0-rc2`，用于 RC2 Canary Scope 人工审批前的只读治理审计。当前结论为：

- `DECISION=PENDING_HUMAN_APPROVAL`
- `CANARY_SCOPE_MODEL_SUFFICIENT=NO`
- `CANARY_GOVERNANCE_MODEL_GAP=YES`
- `CANARY_APPROVAL_PACKAGE_READY=NO`
- `ROLE_RUNTIME=DISABLED`
- `CANARY_AUTHORIZED=NO`
- `CANARY_ENABLED=NO`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`

本文不是授权、启用或运行指令。不得依据本文写入治理控制、切换 Feature Flag、改变 Kill Switch、启动 ROLE Runtime，或创建 Instance、Task、Candidate Pool、Claim、Admission、Realtime Eligibility Evidence。

## 2. Release Baseline

| 项目 | 冻结值 | 审计结果 |
| --- | --- | --- |
| Release tag | `workflow-v1.0.0-rc2` | 本地 annotated tag 存在 |
| Release commit | `740bee63e79a2744eb06ff693b17e7ed3fdf9375` | tag target 与 HEAD 均匹配 |
| RC1 tag target | `bf70c588752f8b32c27f3112e2ddb5e8803b4db6` | 未变化 |
| Structural canonical | `RC2_SCHEMA_STRUCTURAL_CANONICAL_V2` | 冻结 |
| Structural fingerprint | `92ea9f4d006233f672b22c3c335663c04121dbb652ce657aac07d7b04518171d` | 冻结 |
| Migration SHA | `45/45_PASS` | 沿用已完成的 RC2 Release Gate 证据 |
| Fresh / Upgrade | `PASS / PASS` | 结构一致 |

`RC2_TAG_BASELINE_MATCH=YES`。本次审计以 RC2 tag 对应源码为准，不把后续 working tree 文件纳入 Canary 基线。

## 3. 唯一 Scope Identity

本次唯一允许进入后续人工审批的 Proposed Scope 如下。所有字段均为精确匹配；禁止通配符、`ALL`、空值或以 `NULL` 表示全局。

| 维度 | 冻结值 |
| --- | --- |
| enterpriseId | `990001` |
| organizationId | `990101` |
| definitionId | `990401` |
| definitionVersionId | `990402` |
| nodeId | `990404` |
| roleCode | `RC1_TEST_CANARY_APPROVER` |
| role-bound node count | `1` |
| status | `PROPOSED_NOT_ENABLED` |

不得扩大到其他企业、组织、定义、版本、节点或审批角色。

## 4. Directory Identity 与证据边界

| 项目 | 冻结值/状态 |
| --- | --- |
| directoryRevision | `1` |
| directoryCandidateCount | `2` |
| candidate userId count | `2`，不在本文披露具体 ID |
| roleCode | `RC1_TEST_CANARY_APPROVER` |
| organizationId | `990101` |
| Directory Result Hash | 当前审批输入未提供可冻结的具体值，必须在后续审批前补齐并校验 |

RC2 Fixture Contract 能冻结 revision、人数、有效时间及角色/组织标识，但本审批输入没有给出 Directory Result Hash。不得使用候选人数或 revision 替代 Directory Result Hash。

## 5. 当前治理模型审计

### 5.1 权威对象与读取规则

Canary 的当前持久化控制权威是 `workflow_role_runtime_governance_control`。它采用 append-only 记录、`control_type + scope_key + config_version + delete_token` 唯一键、有效时间窗口与证据 Hash；UPDATE/DELETE 由数据库 Trigger 拒绝。`RoleRuntimeGovernanceControlStore` 按 `control_type + scope_key` 读取生效期内最高 `config_version`，缺失或读取异常时上层路径拒绝执行。

当前 Claim Gate 的 Canary 键为：

```text
CANARY|{enterpriseId}|DEF:{definitionId}|VER:{definitionVersionId}|NODE:{nodeId}
```

Realtime 与 commit-time 路径使用等价的 enterprise/definition/version/node 业务范围键。`scope_key` 虽是自由字符串，但生产读取代码不会构造包含 `organizationId` 和 `roleCode` 的 Canary 键，因此不能仅靠写入一个更长键获得运行时强制校验。

### 5.2 十项审计回答

| 问题 | 当前 RC2 事实 | 结论 |
| --- | --- | --- |
| Canary Authorization 权威持久化对象 | `workflow_role_runtime_governance_control` 的 `CANARY` 控制记录 | 存在 |
| Canary Scope 表达 | enterprise + definition + version + node 的 `scope_key` | 仅四维 |
| 六维 Scope 支持 | organizationId、roleCode 未进入 Canary gate key | 不支持 |
| Authorized 与 Enabled 独立 | `CANARY/ALLOW` 同时是运行时放行条件；无独立 Canary approval 状态被该 gate 消费 | 未分离 |
| ROLE Runtime 与 Canary 独立 | `workflow.role-runtime.claim-enabled` 默认 false，且 Canary 还需 `ALLOW` | 已分离 |
| Kill Switch 覆盖 | Claim 前、Realtime capability、commit-time 均检查；未发现其在 Role Directory prepare、Task/Candidate Pool 创建入口的统一前置检查 | 覆盖不完整 |
| 完整 Activation Gate | 存在 ROLE Activation approval domain/persistence，但未与 Canary 六维 Scope 控制读取形成完整生产闭环 | 不存在 |
| Fail Closed | Claim/Realtime/commit-time 对缺失、过期、拒绝或异常控制均拒绝 | 已存在，但仅覆盖当前键模型 |
| Scope 越界检测 | 四维键漂移有测试；organizationId/roleCode 越界不能由 Canary gate 拒绝 | 不完整 |
| Scope 撤销/禁用 | 可追加更高版本 `DENY` 或恢复 Kill Switch；禁止原位 UPDATE/DELETE | 存在 append-only 控制能力 |

### 5.3 状态模型复用边界

现有 `RoleRuntimeActivationStatus` 已定义 `DRAFT → ELIGIBLE → APPROVED` 及 `BLOCKED / REJECTED / REVOKED`，并明确禁止由该聚合进入 `ENABLED`。后续不得创建第二套平行审批状态机。

本 Canary 包对现有语义的映射为：

```text
PROPOSED                 = 尚未形成有效的三方 Activation approval
APPROVED_NOT_ENABLED     = 复用 RoleRuntimeActivationStatus.APPROVED
ENABLED                  = 独立 Canary governance control + 独立 ROLE runtime enablement 均通过
SUSPENDED / REVOKED      = append-only DENY / REVOKED 证据，不删除历史记录
```

当前只有 `PROPOSED / PENDING_HUMAN_APPROVAL`。由于生产 Canary gate 未消费包含六维 Scope 的批准证据，不能晋级为 `APPROVED_NOT_ENABLED`。

## 6. Approval Preconditions

| 分类 | 条件 | 当前结果 |
| --- | --- | --- |
| Release | remote RC2 tag、target、RC1 immutable、45/45 SHA、结构指纹冻结 | PASS |
| Schema | Fresh、RC1→RC2 Upgrade、结构一致、Mapping Checker | PASS |
| Runtime safety | Runtime disabled、Canary off、Kill Switch safe | PASS |
| Scope | 1 个 ROLE node、2 个候选、唯一推荐 Scope | PASS（证据层） |
| Scope enforcement | 六个维度均进入生产 Canary gate | FAIL |
| Directory | role/org/revision/count 匹配 | PASS（Fixture 证据层） |
| Directory hash | 可冻结并在激活时精确校验的 Result Hash | INCOMPLETE |
| Authorization split | Canary approval 与 Canary enablement 独立 | FAIL |
| Security | secret/PII finding 为 0 | PASS |
| Rollback | 可 append-only deny、关闭 Runtime、恢复 Kill Switch、保留 Evidence | PASS（设计层） |
| Rollback enforcement | Kill Switch 在 new runtime、Task、Pool、Claim、Admission 全入口统一生效 | FAIL |

因此 `APPROVAL_PRECONDITIONS_COMPLETE=NO`。

## 7. 后续 Activation Gate

后续 Repair Sprint 必须建立单一、原子、fail-closed 的激活 Gate，并按固定顺序校验：

1. RC2 release tag 与 commit 精确匹配；
2. structural canonical version 与 fingerprint 精确匹配；
3. 45 项 Migration SHA 无漂移；
4. 六维 Scope 与批准证据逐字段精确匹配；
5. Directory revision、candidate count、Directory Result Hash 精确匹配；
6. resolver binding hash、manifest hash、content hash 精确匹配；
7. Role Activation 三方批准证据完整且未撤销；
8. Canary authorization 已批准但尚未启用；
9. Runtime enablement、Canary enablement 为独立动作；
10. Feature Flag、Kill Switch、Canary、SoD、Audit 均满足；
11. Fixture 无 partial state，运行对象计数为 0；
12. schema/migration 无漂移。

任何缺失、歧义、过期、Hash 漂移或查询异常均为 `FAIL_CLOSED`；禁止 fallback、默认全局 Scope、自动降级或改用 EXPLICIT_USER。

该设计本身完整，`ACTIVATION_GATE_DESIGN=PASS`；但当前 RC2 代码尚未实现完整 Gate。

## 8. Scope Escape 负向矩阵

| # | 单变量变更 | 预期 |
| --- | --- | --- |
| 1 | enterpriseId ≠ `990001` | DENY |
| 2 | organizationId ≠ `990101` | DENY |
| 3 | definitionId ≠ `990401` | DENY |
| 4 | definitionVersionId ≠ `990402` | DENY |
| 5 | nodeId ≠ `990404` | DENY |
| 6 | roleCode ≠ `RC1_TEST_CANARY_APPROVER` | DENY |
| 7 | directoryRevision ≠ `1` | DENY |
| 8 | candidateCount ≠ `2` | DENY |
| 9 | resolver binding hash 漂移 | DENY |
| 10 | manifest hash 漂移 | DENY |
| 11 | content hash 漂移 | DENY |
| 12 | releaseTag ≠ `workflow-v1.0.0-rc2` | DENY |
| 13 | releaseCommit ≠ `740bee63e79a2744eb06ff693b17e7ed3fdf9375` | DENY |
| 14 | structuralFingerprint 漂移 | DENY |

所有场景均禁止 `WARN_AND_CONTINUE`。矩阵设计完整，`SCOPE_ESCAPE_NEGATIVE_MATRIX=PASS`；其中 organizationId、roleCode 两项在当前生产 Canary key 下尚不能强制执行，是阻断本次批准的核心缺口。

## 9. Kill Switch 与 Rollback

异常发生后的第一动作必须是恢复或保持：

```text
KILL_SWITCH=STOP_NEW_AND_CLAIM
```

受控回退顺序：

1. 追加更高 `config_version` 的 Kill Switch/Canary deny 控制，禁止原位修改；
2. 关闭独立 ROLE Runtime enablement；
3. 阻断 new ROLE runtime、Task/Pool 激活、Claim 与 Admission；
4. 将 Canary Scope 标记为 SUSPENDED 或 REVOKED；
5. 保留 Activation、Admission、Realtime Eligibility、Claim 与外部审计 Evidence；
6. 完成运行对象盘点与人工处置，不以 DELETE 历史证据作为回滚手段。

`KILL_SWITCH_ROLLBACK_DESIGN=PASS`。但当前 Kill Switch 消费点不能证明覆盖 Task/Candidate Pool 创建入口；修复前不得执行 Canary。

## 10. Approval Decision Record

| 字段 | 值 |
| --- | --- |
| Decision | `PENDING_HUMAN_APPROVAL` |
| Package readiness | `NO` |
| Blocking severity | `P0` |
| Runtime authorization | `NO` |
| Canary authorization | `NO` |
| Canary enablement | `NO` |

阻断项：

1. Canary Scope 权威键缺少 `organizationId` 与 `roleCode`；
2. Canary approval 与 Canary enablement 未形成两个独立且被生产 Gate 消费的状态；
3. Directory Result Hash 尚未作为本 Scope 的冻结审批输入；
4. Kill Switch 尚未证明在 new runtime、Task 与 Candidate Pool 创建入口统一生效；
5. 现有 Role Activation approval evidence 未与六维 Canary Scope 控制形成原子桥接。

## 11. 验证结果

- 定向测试共执行 25 项：23 通过、1 跳过、1 失败。
- 可重复失败项：`PlatformSoDGovernanceTest.threeVersionedRulesMustAllAllowAndVersionDriftMustFailClosed`；实际返回 `INDETERMINATE / platform SoD unavailable`，符合运行时 fail-closed 结果，但不符合该测试期望的 `PASS`。本任务未修改 RC2 tag 上的测试或生产代码。
- `ConfiguredRoleClaimRuntimeGateTest`、`WorkflowRoleRuntimeActivationGateTest`、`RoleRuntimeProductionCapabilityBundleTest`、`Rc2ControlledCanaryFixtureContractTest` 全部通过；数据库型 Fixture Seeder 测试按既有条件跳过。
- 审批包 trailing whitespace 为 0，`git diff --check` 通过。

该定向回归失败进一步阻断审批包晋级；不得将 fail-closed 的 `INDETERMINATE` 解释为 Canary 可运行。

## 12. Next-Step Activation Boundary

后续应单独建立 Repair Sprint，只处理 Canary 治理模型缺口：定义六维不可歧义 Scope canonical、将 approval evidence 与 enablement 分离、让所有 Runtime 入口消费同一 fail-closed Gate，并补齐 14 项负向测试及 Kill Switch 全入口测试。完成真实数据库和应用回归前，不得请求人工批准，更不得进入 Canary Activation。

本次未修改 Production Code、Migration、数据库、RC1/RC2 tag，也未触碰 RC1 TEST 数据库。
