# Workflow 版本发布机制实现报告

## 1. Sprint 结论

Sprint 2-3.7-WF2.7 已完成 Workflow Lite 版本发布机制的数据库候选与后端实现。实现保持
Workflow 独立限界上下文，未修改 Investment 代码，未修改 V2.4.0—V2.5.4，未引入多节点、
会签、条件路由、自动选人或完整 BPM 能力。

V2.5.5 当前状态为 `CANDIDATE / NOT_EXECUTED`，未执行真实 MySQL Migration。

## 2. 现状审计与最小改造结论

既有 `workflow_version` 已具备版本号、DRAFT/PUBLISHED/RETIRED、内容哈希、发布人、发布时间、
生效区间、来源版本和乐观锁字段；`workflow_definition.current_version_id` 已具备当前版本指针。
现有缺口是：

1. 缺少独立、不可变的版本发布切换审计事实；
2. `workflow_instance` 已冻结 definition/version/versionNo，但未冻结版本内容哈希；
3. Repository 只有新增能力，没有带旧状态和乐观锁版本的条件更新；
4. Application 层没有正式发布用例和版本详情接口。

因此最小 V2.5.5 同时新增发布审计表，并补充实例内容哈希快照，不扩展其他业务表。

## 3. 修改文件清单

### 后端新增

- `application/command/PublishWorkflowVersionCommand.java`
- `domain/model/WorkflowVersionRelease.java`
- `domain/repository/WorkflowVersionReleaseRepository.java`
- `domain/service/WorkflowVersionContentHasher.java`
- `infrastructure/persistence/entity/WorkflowVersionReleaseEntity.java`
- `infrastructure/persistence/mapper/WorkflowVersionReleaseMapper.java`
- `infrastructure/persistence/WorkflowVersionReleaseRepositoryImpl.java`

### 后端修改

- `domain/model/WorkflowDefinition.java`
- `domain/model/WorkflowVersion.java`
- `domain/model/WorkflowInstance.java`
- `domain/repository/WorkflowDefinitionRepository.java`
- `domain/repository/WorkflowVersionRepository.java`
- `application/service/WorkflowDefinitionApplicationService.java`
- `application/service/WorkflowRuntimeApplicationService.java`
- `interfaces/rest/WorkflowDefinitionController.java`
- `infrastructure/persistence/entity/WorkflowInstanceEntity.java`
- `infrastructure/persistence/WorkflowEntityMapper.java`
- Definition/Version Mapper 与 Repository Adapter

### 数据库与治理资产

- `database/migration/mysql/V2.5.5__implement_workflow_version_release.sql`
- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/SHA256SUMS`
- `database/migration/mysql/README.md`

### 测试

- 新增发布 Application、发布仓储、Migration 契约测试；
- 扩展领域状态机、内容哈希、权限/API、事务回滚、实体映射和实例快照测试。

## 4. 数据库变化

### workflow_instance

新增 `definition_content_hash_snapshot VARCHAR(128) NOT NULL`。Migration 先从实例绑定的
`workflow_version.content_hash` 确定性回填；任何无法关联到发布证据的历史实例都会使 NOT NULL
收紧失败，不伪造哈希。增加 64 位小写 SHA-256 CHECK。

### workflow_version_release

新增不可变发布事实表，记录 definition、旧版本、新版本、新版本号、内容哈希、发布人、发布组织、
发布时间、traceId 和非敏感校验摘要。通过复合外键保证旧/新版本属于同一定义，通过唯一约束保证
一个版本只有一条发布事实。表约束禁止逻辑删除和后续版本更新。

## 5. 状态机与冻结规则

版本状态仅允许：

```text
DRAFT -> PUBLISHED -> RETIRED
```

不存在逆向转换。DRAFT 可维护节点；PUBLISHED 和 RETIRED 均不可通过节点维护入口修改。
从历史版本创建新草稿时生成新版本 ID、新节点 ID 和定义内递增版本号，来源版本不变。

## 6. 发布校验与 Hash 规则

发布前验证定义状态、版本归属、DRAFT 状态、期望乐观锁版本、组织管理范围、Schema 版本和节点结构。
当前 Workflow Lite 只允许恰好一个启用的 `APPROVAL + SINGLE` 节点，且顺序必须为 1；该限制用于
明确拒绝多节点能力，不代表已实现多节点推进。

SHA-256 使用确定性长度前缀编码，节点按 `nodeOrder + nodeCode` 排序。哈希包含定义稳定业务语义、
Schema 和全部节点执行配置；排除数据库 ID、创建/更新时间、审计人和乐观锁版本。同一语义跨 ID
复制可得到相同哈希。

## 7. 原子发布与并发控制

固定锁顺序为：

```text
WorkflowDefinition -> target DRAFT Version -> previous current Version
```

同一事务内依次执行：校验、旧版 PUBLISHED→RETIRED、目标 DRAFT→PUBLISHED、更新
`current_version_id`、追加发布审计。版本和定义更新都带旧状态与期望乐观锁版本；任一步返回 0 行
即按并发冲突失败并整体回滚。两个基于同一期望 Definition 版本的并发发布请求只有一个能完成。

## 8. 实例绑定规则

新实例只能从 Definition 明确的 `currentVersionId` 解析有效 PUBLISHED 版本，且校验生效时间窗口；
实例永久冻结 definitionId、versionId、definitionCode、versionNo 和 contentHash。版本切换不会批量
修改历史实例，RETIRED 版本也不会影响其既有实例继续查询和审计。

## 9. API 与权限

| 方法 | 路径 | 权限 | 用途 |
|---|---|---|---|
| POST | `/workflow/definitions/{definitionId}/versions` | `workflow:definition:edit` | 新建/复制草稿版本 |
| POST | `/workflow/definitions/{definitionId}/versions/{versionId}/publish` | `workflow:definition:publish` | 原子发布版本 |
| GET | `/workflow/definitions/{definitionId}/versions/{versionId}` | `workflow:definition:view` | 查询版本和节点快照 |

发布请求必须提供 Definition 与 Version 的期望乐观锁版本。发布权限仅表示版本治理能力，同时仍校验
定义所属组织范围；它不等于具体 Workflow Task 的审批权。

## 10. 测试结果

- Java：21.0.12
- Maven：3.9.9
- `mvn test`：239 tests，0 failures，0 errors，0 skipped
- Spring Boot 上下文：通过
- SHA256SUMS 全资产校验：通过，无历史 Migration 摘要漂移
- V2.5.5 静态 Migration 契约：通过
- 真实 MySQL/Flyway migrate：按 Sprint 约束未执行

测试覆盖状态机、稳定哈希、草稿发布、旧版本退役、错误节点门禁、乐观锁冲突、事务回滚、发布审计、
实例哈希快照、API 权限、Repository 映射和历史回归。

## 11. V2.5.5 资产状态

- 文件：`V2.5.5__implement_workflow_version_release.sql`
- SHA-256：`50efcb230b2cba2e7a7600fdfc3e229f05a9e85877194cf9f1eed0875d52605e`
- asset_status：`CANDIDATE`
- execution_status：`NOT_EXECUTED`
- flyway_checksum：`null`

## 12. 剩余风险与下一步建议

1. V2.5.5 尚未经过 Fresh 与 V2.5.4 Upgrade 的真实 MySQL 8 验收；历史实例若缺少真实发布哈希，
   Upgrade 会按设计阻断，需要先形成数据治理报告。
2. Workflow 内部启动接口当前通过 Definition 的明确 currentVersionId 绑定版本；Investment 的
   `definitionKey + definitionVersion` 严格适配仍保持冻结，未在本 Sprint 修改。
3. 哈希对规则配置原始文本敏感；当前写入规范应继续保证 JSON 配置规范化，后续可增加独立
   canonical JSON 契约而不改变本次状态机。
4. 下一 Sprint 建议仅执行 V2.5.5 真实 MySQL Fresh/Upgrade 验收、checksum、结构指纹与负向约束
   测试；验收通过前不得晋级为 `CANONICAL_IMMUTABLE`。
