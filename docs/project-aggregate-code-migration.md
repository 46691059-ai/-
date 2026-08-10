# Project 聚合代码迁移说明

> Sprint：2-0.6
> 策略：渐进式迁移，兼容优先

## 1. 当前落地范围

Sprint 2-0.6 建立领域分层骨架；Sprint 2-0.7 已迁移第一条完整链路：

- `domain`：Project、生命周期、阶段快照、任务、成员模型及仓储端口；
- `application`：项目创建和详情查询进入新应用服务；
- `infrastructure`：实现聚合仓储、详情读仓储和ID生成适配器；
- Controller 仅将创建和详情切到 Application；其余接口保持旧链路。

领域模型当前只承载项目创建和详情重建，不承载修改、删除、阶段流转、任务和成员命令。

## 2. 兼容链路

```text
POST /projects、GET /projects/{id}
  -> ProjectApplicationService
  -> ProjectRepository / ProjectDetailQueryRepository
  -> 现有Mapper

其他Project接口
  -> ProjectLifecycleService
  -> 现有Mapper
```

详情查询继续先经过带 `@DataScope` 的 `ProjectAccessPolicy`，聚合重建后再次校验最新组织与创建人字段。创建在 Application 事务中原子保存项目、8个阶段和经理成员。

## 3. 禁止事项

迁移完成前禁止：

1. 删除 `ProjectLifecycleService` 或 `ProjectLifecycleServiceImpl`；
2. Controller 直接改用未完成的领域仓储；
3. 新增绕过 `ProjectAccessPolicy` 的项目或子资源入口；
4. 为领域模型增加 MyBatis Plus、Spring Web 或数据库注解；
5. 为适配领域模型而直接修改现有数据库；
6. 同一用例同时由新旧链路重复写入。

## 4. 后续迁移顺序

1. 为现有行为补齐特征测试；
2. 实现 `ProjectRepository` 的 MyBatis 适配器和显式转换器；
3. 先迁移只读详情，再迁移项目创建；
4. 待生命周期 V2 migration 落地后迁移阶段初始化与流转；
5. 分别迁移任务和成员命令；
6. 每迁移一个用例，只切换一个 Controller 入口并运行完整回归；
7. 全部入口稳定后再评审旧 Service 的废弃与删除。

## 5. 切换门槛

任何用例切换到新 Application/Domain 链路前必须满足：

- REST 请求和响应兼容；
- 功能权限、`@DataScope` 和 `ProjectAccessPolicy` 测试通过；
- 乐观锁、逻辑删除和审计字段行为一致；
- MySQL 集成测试及现有 Project 回归测试通过；
- 不产生新旧链路双写；
- 具备按用例回退到旧服务的开关或明确回滚步骤。
