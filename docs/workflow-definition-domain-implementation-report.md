# Workflow 定义域基础代码实现报告

## 1. 实现范围

Sprint 2-3.7-WF2.2 已实现 Workflow 定义域的纯 Java 领域模型、MyBatis Plus 持久化适配、应用服务、REST 接口和安全契约测试。本次不包含流程实例、审批任务、审批动作及流程发布行为。

## 2. 分层与调用链

```text
REST Controller
  -> WorkflowDefinitionApplicationService
    -> Domain Repository Port
      -> MyBatis Repository Adapter
        -> Mapper
          -> workflow_definition / workflow_version / workflow_node
```

Domain 层仅依赖 Java 标准库，不依赖 Spring、MyBatis、持久化 Entity。V2.5.0 使用 `created_* / updated_*` 审计列，Workflow 模块为此建立独立持久化审计基类，没有复用平台旧的 `create_* / update_*` 映射。

## 3. 业务能力

- 创建流程定义：同企业内流程编码唯一，新定义状态为 `DRAFT`。
- 查询流程定义：一次返回定义、版本及节点，节点批量加载，避免逐版本重复查询。
- 创建版本：对定义加行锁后生成递增版本号；可选择源版本并复制节点快照。
- 节点维护：仅允许修改 `DRAFT` 版本；节点编码和顺序必须唯一；原节点逻辑删除后原子替换。
- 发布能力：本 Sprint 只冻结权限码，不实现发布状态机或发布接口。

## 4. 权限

- `workflow:definition:view`
- `workflow:definition:create`
- `workflow:definition:edit`
- `workflow:definition:publish`

Controller 与 Application Service 均声明权限边界。由于 V2.5.1、V2.5.2 已规划为运行域 Migration，RBAC 种子数据应在后续 V2.5.3 Migration 一次性落地；本 Sprint 未跳号创建 Migration。

## 5. 数据库变化

无。未修改 V2.5.0，也未修改 V2.4.0-V2.4.9。代码严格映射已验收的三张表。

## 6. API

| Method | Path | Authority | Purpose |
|---|---|---|---|
| POST | `/workflow/definitions` | `workflow:definition:create` | 创建流程定义 |
| GET | `/workflow/definitions/{definitionId}` | `workflow:definition:view` | 查询定义、版本和节点 |
| POST | `/workflow/definitions/{definitionId}/versions` | `workflow:definition:edit` | 创建草稿版本 |
| PUT | `/workflow/definitions/{definitionId}/versions/{versionId}/nodes` | `workflow:definition:edit` | 原子替换草稿版本节点 |

## 7. 测试覆盖

- Domain：模型不变量、QUORUM 阈值、框架依赖隔离。
- Repository：领域/Entity 映射、节点逻辑删除后插入顺序。
- Entity mapping：表名、逻辑删除、乐观锁及 V2.5 审计字段。
- Permission：权限集合不可变，接口权限声明匹配。
- Transaction：命令事务、只读查询、持久化失败回滚。

## 8. 剩余风险与下一步

1. 权限码尚未写入 `sys_menu`，需按既定版本链在 V2.5.3 落地并进行真实 MySQL 验收。
2. 发布权限已预留，但发布所需内容哈希、冻结校验和状态流转尚未实现。
3. 当前 Repository 测试为隔离测试；应在下一阶段补充基于真实 MySQL V2.5.0 的持久化集成测试。
4. 流程运行域应继续按 V2.5.1、V2.5.2 顺序实施，不能由定义域直接承担审批运行职责。
