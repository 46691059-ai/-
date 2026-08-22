# V2.6.5 Multi-Resolver Binding真实MySQL/Flyway验收报告

> Sprint：2-3.7-WF3.9.1
> 结论：`PASS`
> 资产状态：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`

## 1. 验收环境

| 项目 | 值 |
|---|---|
| 数据库 | MySQL Community Server 8.4.9 |
| Migration执行器 | Flyway Community Edition 13.0.0 |
| 网络 | 仅绑定127.0.0.1的隔离一次性实例 |
| 基线 | 2.0.0 |
| 目标 | 2.6.5 |
| 生产连接 | 未使用 |

验收使用新建数据目录和独立端口，不复用历史Schema，不连接系统MySQL服务。

## 2. Fresh结果

- 权威链迁移至V2.6.5：PASS；
- 成功history行：28（含baseline记录），失败行：0；
- strict validate：PASS；
- 第二次migrate：`No migration necessary`；
- V2.6.5执行次数：1。

## 3. Upgrade结果

- 先构造V2.6.4完成库，再执行V2.6.5：PASS；
- Upgrade阶段只执行V2.6.5一次；
- 成功history行：28，失败行：0；
- strict validate和第二次migrate no-op：PASS；
- 与Fresh Flyway checksum一致。

## 4. V2.6.5校验值

- Flyway checksum：`-588626998`
- SHA-256：`3d516374333f31554409d5d5c8f7ebaddf7ebd21dbc24916d3dc48c5b8337caf`

## 5. 三张表结构核查

| 表 | 字段 | CHECK | 外键 | 唯一约束（不含PK） |
|---|---:|---:|---:|---:|
| workflow_instance_resolver_binding_set | 18 | 7 | 2 | 2 |
| workflow_instance_resolver_binding | 21 | 9 | 1 | 2 |
| workflow_node_resolver_binding_snapshot | 26 | 9 | 3 | 2 |

三表共65个字段、25个CHECK和6个外键。全部CHECK在
`information_schema.table_constraints.ENFORCED`中为`YES`。三表均包含主键、
普通索引、审计字段、`deleted`、`delete_token`和乐观锁`version`。

## 6. Binding Set验证

测试Instance仅生成一个活动Binding Set；Manifest Hash为64位小写十六进制。
重复活动Binding Set被唯一键拒绝。测试实例最终计数为Binding Set 1条。

## 7. Resolver Binding验证

同一实例写入两个测试Stub契约：`RESOLVER_A/A_V1`和`RESOLVER_B/B_V1`。
两者均保存strategy、mode、contract hash和rule hash。未实现或启用
ROLE/POSITION/ORG Resolver。

## 8. Node Binding验证

- NODE_A精确引用RESOLVER_A；
- NODE_B精确引用RESOLVER_B；
- Instance、Definition Version、Node、Binding Set和Resolver复合外键一致；
- 不存在Resolver、跨Instance及跨Version引用均被MySQL 1452拒绝。

## 9. 运行链路验证

数据链路执行结果：

```text
NODE_A -> RESOLVER_A -> assignee 701 -> COMPLETED
NODE_B -> RESOLVER_B -> assignee 702 -> ACTIVE
```

Task、NodeExecution、AssignmentSnapshot和Node Resolver Binding一致。Java
运行契约测试同时确认后续Task优先读取冻结Node Binding，不重新路由或fallback。

## 10. Legacy兼容验证

专项回归16项全部通过：

- SINGLE_NODE_LEGACY保持原路径；
- 旧MULTI_NODE_LINEAR_V1无Node Binding时使用精确Instance Binding；
- 新Multi-Resolver实例缺少Node Binding时阻断；
- 未补造历史Node Binding。

## 11. Hash验证

Domain测试确认相同语义及不同输入顺序产生相同Manifest Hash；Resolver Version、
Contract Hash、Node Code、Target或Rule变化会改变Hash。数据库拒绝大写、非法长度
和非小写十六进制Hash。

## 12. 外键负向测试

以下场景全部拒绝：不存在Instance、不存在Binding Set、不存在Resolver Binding、
不存在Node、跨Instance引用、跨Version引用。

## 13. CHECK负向测试

非法状态、strategy、resolver mode、contract hash、manifest hash、delete token和
负数version全部触发MySQL 3819。25个CHECK均为ENFORCED。

## 14. 唯一约束测试

重复活动Binding Set、重复Resolver业务键、重复Instance+Node Binding全部触发
MySQL 1062。

## 15. 并发与事务测试

两个独立MySQL会话使用同一企业和幂等键并发写完整启动聚合。一个事务成功，
另一个事务被`uk_workflow_instance_idempotency`以1062拒绝并回滚。最终记录数：

```text
Instance=1, BindingSet=1, ResolverBinding=1, NodeBinding=2,
NodeExecution=1, Task=1, AssignmentSnapshot=1
```

另行注入Binding Hash失败，连接终止后Instance及后续记录均为0，事务回滚PASS。

## 16. Schema Fingerprint

| 范围 | SHA-256 |
|---|---|
| 完整Schema | `f40975793a306178dd70cf46bed8d5fe81a638850c9d84163e63af5dea5e8448` |
| Workflow Schema | `8d41324ab001a054ab933514b246bd2147db18b3efd15524839330dae3bdf2c4` |
| Binding三表 | `8204b041daa011ddab60f9a687f9364e2feb55985a832e44247c702e14a2bcc4` |

Fresh与Upgrade三个范围完全一致。

## 17. 历史Migration摘要

V2.5.0—V2.6.5逐文件重新计算，全部与`SHA256SUMS`一致。V2.6.5 SQL未修改，
其SHA-256保持`3d516374333f31554409d5d5c8f7ebaddf7ebd21dbc24916d3dc48c5b8337caf`。

## 18. 临时环境清理

所有隔离mysqld进程均已停止；临时数据目录在提取证据后删除。不保留数据库服务、
测试账号、明文密钥或开放端口。

## 19. 最终结论

`PASS`。V2.6.5满足Fresh、Upgrade、strict validate、no-op、Schema一致性、结构、
数据链路、Legacy、Hash、外键、CHECK、唯一性、并发和事务验收条件，资产晋级为：

```text
CANONICAL_IMMUTABLE
EPHEMERAL_MYSQL8_VALIDATED
```

本报告不代表生产部署；未进入Candidate Pool、Claim或ROLE/POSITION/ORG实现。
