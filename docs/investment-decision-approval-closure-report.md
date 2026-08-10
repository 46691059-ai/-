# Sprint 2-3.4 投资决策审批闭环增强报告

项目：县域国企数字化运营治理平台
Sprint：2-3.4
日期：2026-08-08
结论：**代码实现及自动化测试通过，V2.4.8 等待真实 MySQL 验收**

## 1. 实现范围

本 Sprint 在现有 Investment、Workflow Adapter 和 V2.4.7 Inbox/Outbox 基础上完成：

- Workflow 审批通过、审批驳回、撤回和异常事件处理；
- Inbox 事件 ID 去重、绑定一致性校验和严格事件序号校验；
- 投资决策标准状态机；
- 附条件批准后的条件创建、整改、提交、复核和关闭；
- 阻断条件及重大风险归档门禁；
- 三项新增 RBAC 权限；
- V2.4.8 增量 Migration 候选资产。

未实现 Workflow 引擎，未创建 Workflow 数据库，未修改 V2.4.0 至 V2.4.7 历史 Migration。

## 2. 修改文件清单

### 新增后端文件

- `domain/model/DecisionWorkflowEventPolicy.java`
- `domain/model/DecisionCondition.java`
- `domain/repository/DecisionConditionRepository.java`
- `application/command/CreateDecisionConditionCommand.java`
- `application/command/SubmitConditionRectificationCommand.java`
- `application/command/ReviewDecisionConditionCommand.java`
- `application/service/InvestmentDecisionConditionService.java`
- `application/service/InvestmentDecisionClosureService.java`
- `infrastructure/persistence/entity/DecisionConditionEntity.java`
- `infrastructure/persistence/entity/DecisionConditionActionEntity.java`
- `infrastructure/persistence/mapper/DecisionConditionMapper.java`
- `infrastructure/persistence/mapper/DecisionConditionActionMapper.java`
- `infrastructure/persistence/DecisionConditionRepositoryImpl.java`
- `controller/InvestmentDecisionConditionController.java`

### 修改后端文件

- `InvestmentDecisionCase.java`：冻结标准状态机及历史状态兼容映射。
- `InvestmentDecisionApplicationService.java`：提交状态统一为 `SUBMITTED`。
- `InvestmentDecisionRepositoryImpl.java`：历史状态映射及终态时间、结果写入。
- `WorkflowInboxService.java`：完整事件分类、去重、序号、异常和状态机处理。
- `InvestmentDecisionController.java`：增加归档入口。
- `InvestmentPermissions.java`：增加审批闭环权限。

### 测试文件

- 新增 `InvestmentDecisionApprovalStateMachineTest.java`。
- 新增 `InvestmentDecisionApprovalClosureTest.java`。
- 更新 `InvestmentDecisionWorkflowTest.java`。
- 更新 `InvestmentPermissionContractTest.java`。

### 数据库和治理文件

- 新增 `V2.4.8__enhance_investment_decision_approval_closure.sql`。
- 更新 `SHA256SUMS`。
- 更新 `migration-inventory.yml`。
- 更新 Migration README。

## 3. Workflow 事件处理

| Workflow 事件 | 投资决策状态 | Workflow 本地绑定状态 |
| --- | --- | --- |
| `PROCESS_STARTED` / `APPROVAL_STARTED` | `IN_APPROVAL` | `RUNNING` |
| `APPROVAL_APPROVED` | `APPROVED` | `COMPLETED` |
| `APPROVAL_REJECTED` | `REJECTED` | `COMPLETED` |
| `PROCESS_COMPLETED + APPROVED` | `APPROVED` | `COMPLETED` |
| `PROCESS_COMPLETED + APPROVED_WITH_CONDITIONS` | `APPROVED` | `COMPLETED` |
| `PROCESS_COMPLETED + REJECTED` | `REJECTED` | `COMPLETED` |
| `PROCESS_WITHDRAWN` | `WITHDRAWN` | `WITHDRAWN` |
| `PROCESS_CANCELLED` | `WITHDRAWN` | `CANCELLED` |
| `PROCESS_EXCEPTION` / `WORKFLOW_EXCEPTION` | 不伪造审批结果 | `TERMINATED` |

异常和未知事件写入 Inbox `FAILED`，记录受长度限制的失败码，并推进绑定事件水位；不会将异常解释为批准或驳回。

### Inbox 安全规则

1. `event_id` 已存在：返回 `DUPLICATE`，不重复更新状态。
2. Workflow 实例、决策、快照和 `attempt_no` 必须全部匹配。
3. `sequence <= last_event_sequence`：按迟到或重复事件忽略。
4. `sequence > last_event_sequence + 1`：返回 `BUFFERED`，不占用 Inbox 幂等键，等待 Workflow 在前序事件补齐后重试。
5. 只有连续事件才允许进入状态机。

## 4. 投资决策状态机

标准状态：

```text
DRAFT -> SUBMITTED -> IN_APPROVAL -> APPROVED -> ARCHIVED
                       |
                       +-> REJECTED
                       +-> WITHDRAWN

WITHDRAWN -> SUBMITTED
```

Controller 不提供直接修改审批状态接口。`APPROVED`、`REJECTED` 和 `WITHDRAWN` 只由 Workflow Inbox 事件驱动；归档由独立应用服务在条件和风险门禁通过后执行。

历史数据库状态通过只读兼容映射转换为标准领域状态，不回写、不伪造历史事实。

## 5. 附条件批准闭环

复用既有：

- `investment_decision_condition`
- `investment_decision_condition_action`

未创建重复条件表。

条件状态流转：

```text
OPEN -> IN_PROGRESS -> SUBMITTED -> VERIFIED -> CLOSED
                           |
                           +-> REJECTED -> IN_PROGRESS/SUBMITTED
```

规则：

- 只有 Workflow 已批准的决策可以创建附加条件；
- 条件来源节点必须属于当前决策；
- 整改责任组织和责任用户必须有效；
- 每次状态变化写入不可覆盖的条件动作记录；
- 历史复核信息不会在关闭动作中被覆盖；
- 阻断条件未关闭时禁止决策归档。

## 6. 风险门禁

归档前同时检查：

1. 是否存在未关闭的阻断性条件；
2. 决策风险快照是否为 `MAJOR/HIGH/CRITICAL` 且风险门禁未达到 `PASSED/NOT_REQUIRED`；
3. 是否存在风险等级为 `MAJOR/HIGH/CRITICAL` 且尚未关闭的附条件整改任务。

任一条件成立均拒绝归档。只有 `APPROVED` 决策可以进入 `ARCHIVED`。

## 7. 数据库变化

V2.4.8 不新增业务表，仅执行：

- 重建 `chk_inv_decision_approval_status`，增加：
  - `SUBMITTED`
  - `IN_APPROVAL`
  - `ARCHIVED`
- 保留历史状态值以兼容存量记录；
- 增加三项 RBAC 权限、按钮节点和 `SUPER_ADMIN` 幂等授权。

新增权限：

- `investment:decision:approve`
- `investment:decision:condition`
- `investment:decision:archive`

V2.4.8 当前状态：

- `asset_status: CANDIDATE`
- `execution_status: NOT_EXECUTED`
- SHA-256：`eb3f6fe13dff6c9cb717000293ca6aeb188fd080ac8387297f50b630be845f81`

本 Sprint 未连接真实 MySQL 执行 V2.4.8，也未改变 V2.4.7 已冻结资产。

## 8. API 列表

### 投资决策

| 方法 | API | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/investment/decisions` | `investment:decision:create` | 创建决策 |
| POST | `/api/investment/decisions/{id}/submit` | `investment:decision:submit` | 提交 Workflow |
| POST | `/api/investment/decisions/{id}/withdraw` | `investment:decision:withdraw` | 请求 Workflow 撤回 |
| GET | `/api/investment/decisions/{id}/status` | `investment:decision:view` | 查询状态 |
| GET | `/api/investment/decisions/{id}/workflow` | `investment:decision:view` | 查询 Workflow |
| GET | `/api/investment/decisions/{id}/tasks` | `investment:decision:view` | 查询审批任务 |
| POST | `/api/investment/decisions/{id}/archive` | `investment:decision:archive` | 门禁校验后归档 |

### 附条件整改

| 方法 | API | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/investment/decisions/{id}/conditions` | `investment:decision:condition` | 创建条件 |
| GET | `/api/investment/decisions/{id}/conditions` | `investment:decision:view` | 查询条件 |
| POST | `/api/investment/decision-conditions/{id}/start` | `investment:decision:condition` | 开始整改 |
| POST | `/api/investment/decision-conditions/{id}/submit` | `investment:decision:condition` | 提交整改 |
| POST | `/api/investment/decision-conditions/{id}/review` | `investment:decision:condition` | 复核整改 |
| POST | `/api/investment/decision-conditions/{id}/close` | `investment:decision:condition` | 关闭条件 |

`investment:decision:approve` 用于 Workflow 审批任务授权，不提供本地直接改状态接口。

## 9. 测试结果

- Java：21.0.12；
- Maven 编译：通过；
- Spring Boot 上下文：通过；
- 后端全量测试：181 项通过，0 失败，0 错误；
- Migration SHA-256 清单：14/14 匹配。

新增覆盖：

- 重复 Workflow 事件；
- 乱序 Workflow 事件；
- 审批通过事件；
- Workflow 异常事件；
- 决策合法和非法状态流转；
- 条件创建归属及责任人校验；
- 整改、提交、复核和关闭；
- 重大风险阻断归档；
- 无风险决策正常归档；
- RBAC 权限常量和应用服务注解。

## 10. 剩余风险

1. V2.4.8 尚未完成空库和 V2.4.7 升级路径的真实 MySQL/Flyway 验收，不能晋级不可变资产。
2. 乱序事件依赖 Workflow 在收到 `BUFFERED` 后重试；如需平台主动重放，Inbox 必须通过更高 Migration 保存完整脱敏事件载荷并增加重放 Worker。
3. `investment:decision:approve` 当前用于 Workflow 任务授权，尚未实现任务办理代理接口；审批结果仍只能由 Workflow 回调确认。
4. 重大风险门禁当前使用稳定风险快照与引用；Risk 中心实时核验接口尚未接入。
5. 条件动作已形成业务留痕，但向统一 Audit 中心异步转发仍需后续接入。
6. 达梦和人大金仓对 V2.4.8 CHECK 约束语法需要独立适配与验收。
