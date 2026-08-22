# Workflow任务处理与状态流转基础能力实施报告

项目：《县域国企数字化运营治理平台》
Sprint：2-3.7-WF2.4
日期：2026-08-10
状态：**IMPLEMENTED / V2.5.2_CANDIDATE_NOT_EXECUTED**

## 1. 实现边界

本次只扩展Workflow独立限界上下文，没有修改Investment代码，也没有修改V2.5.0、V2.5.1或V2.4.0—V2.4.9历史Migration。V2.5.2仅作为候选资产登记，未连接数据库执行，不能视为真实MySQL验收结果。

本阶段实现单节点Workflow Lite的批准、驳回和发起人撤回，不包含多节点推进、会签计算、条件网关、转办、加签或通用BPM能力。

## 2. 数据库变化

候选Migration：`V2.5.2__create_workflow_task_action.sql`

新增表：`workflow_task_action`

主要内容：

- 任务、实例、操作人及操作组织关联；
- `APPROVE`、`REJECT`、`WITHDRAW`三种动作；
- 动作意见、动作时间、TraceId；
- `idempotency_key`与`request_hash`防止重复请求语义漂移；
- 创建/更新审计字段、逻辑删除、`delete_token`、乐观锁版本；
- 动作编号和任务幂等键唯一约束；
- 任务、实例外键以及动作类型、操作人、逻辑删除、版本CHECK约束。

候选SHA-256：

```text
00ad19ad16dec01228f5ec45eb48ad0f8a9f887b4dfe27c797c4b5e56f508302
```

治理状态：`CANDIDATE / NOT_EXECUTED`。

## 3. 状态流转

| 动作 | 任务前置状态 | 任务结果 | 实例前置状态 | 实例结果 |
|---|---|---|---|---|
| APPROVE | PENDING/CLAIMED | APPROVED | RUNNING | APPROVED |
| REJECT | PENDING/CLAIMED | REJECTED | RUNNING | REJECTED |
| WITHDRAW | PENDING/CLAIMED | CANCELLED | RUNNING | WITHDRAWN |

批准和驳回必须出现在任务的`allowed_actions`快照中。已明确分配的任务只能由`assignee_user_id`处理。撤回只能由流程发起人执行，并要求当前节点`withdraw_allowed=true`。终态任务或非运行中实例的再次流转会被拒绝。

## 4. 事务、幂等与并发

Application Service在同一事务中执行：

1. 校验任务、实例、数据范围和操作人；
2. 校验动作幂等键及请求哈希；
3. 执行任务和实例领域状态机；
4. 使用V2.5.1的`version`字段乐观更新任务、实例；
5. 写入不可变动作记录。

动作写入失败会回滚任务和实例更新。同一任务、同一幂等键、同一请求返回原动作；相同幂等键承载不同请求时拒绝。并发更新影响行数为0时返回并发冲突，不覆盖已提交结果。

## 5. API列表

| Method | URL | 权限 | 功能 |
|---|---|---|---|
| POST | `/workflow/tasks/{id}/approve` | `workflow:approve` | 批准任务 |
| POST | `/workflow/tasks/{id}/reject` | `workflow:approve` | 驳回任务 |
| POST | `/workflow/tasks/{id}/withdraw` | `workflow:withdraw` | 发起人撤回任务 |

请求体：

```json
{
  "comment": "审批意见",
  "idempotencyKey": "客户端生成的唯一动作键"
}
```

## 6. 测试结果

- Java：OpenJDK 21.0.12；
- Maven：3.9.9；
- Workflow专项测试：28通过，0失败；
- 后端完整测试：215通过，0失败，0错误，0跳过；
- Spring Boot上下文：通过；
- 状态机、非法重复流转：通过；
- 动作幂等与语义冲突保护：通过；
- 事务回滚：通过；
- 乐观锁并发冲突：通过；
- Controller权限契约：通过；
- Entity/Table映射：通过；
- Migration SHA清单：通过。

测试上下文中的H2未加载全部正式Migration，启动映射检查仍会输出既有`TABLE_NOT_FOUND`警告；该警告不影响本次单元与上下文测试结论，也不构成V2.5.2真实数据库验收证据。

## 7. 剩余风险与下一步

1. V2.5.2尚未进行Fresh及V2.5.1 Upgrade真实MySQL验收，不能晋级为不可变资产。
2. Workflow RBAC数据库初始化仍按既定版本链留给V2.5.3；当前只完成代码权限契约。
3. 未实现候选人解析、抢签、会签、多节点推进和任务转办。
4. 当前批准/驳回会终结单节点Workflow Lite实例；多节点生命周期必须由后续运行编排能力实现。
5. 建议下一Sprint执行V2.5.2真实MySQL验收，核查表结构、外键、CHECK、唯一约束、checksum和Fresh/Upgrade指纹一致性。
