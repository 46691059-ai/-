# V2.5.3 + V2.5.4 Workflow RBAC组合真实MySQL验收报告

项目：《县域国企数字化运营治理平台》
Sprint：2-3.7-WF2.5.4
验收日期：2026-08-10
最终结论：**FAIL / BLOCKED / NOT PROMOTED**

## 1. 验收环境

| 项目 | 版本或配置 |
|---|---|
| MySQL | MySQL Community Server 8.4.9，Win64 x86_64 |
| Flyway | 13.0.0（Maven Plugin） |
| Java | Eclipse Temurin 21.0.12 LTS |
| Maven | 3.9.9 |
| Fresh端口 | 127.0.0.1:33780 |
| Upgrade端口 | 127.0.0.1:33781 |
| 数据目录 | 本Sprint专用一次性目录 |
| 生产连接 | 未连接 |

两个MySQL实例均由全新`--initialize-insecure`数据目录启动，仅监听本机。业务基线由当前仓库`database/mysql`权威初始化资产加载，Flyway使用正式扫描目录的只读副本。

## 2. Fresh路径

Fresh路径完成V2.0.0基线登记，并连续成功执行V2.1.0至V2.5.3。执行V2.5.4时失败：

```text
SQL State  : HY000
Error Code : 1267
Message    : Illegal mix of collations
             (utf8mb4_0900_ai_ci,IMPLICIT)
             and (utf8mb4_general_ci,IMPLICIT)
             for operation '='
Location   : V2.5.4__govern_rbac_menu_unique_key.sql
Line       : 108
```

History结果：V2.5.3成功一次，V2.5.4失败一次，无其他失败记录。由于存在失败History，迁移后严格`validate`失败，第二次`migrate`也被失败记录阻断，未达到no-op。

## 3. Upgrade路径

Upgrade路径先从V2.0.0迁移至V2.5.2，阶段验收通过；随后按顺序执行V2.5.3、V2.5.4：

- V2.5.3成功一次；
- V2.5.4在与Fresh相同的第108行、相同MySQL 1267错误处失败一次；
- 严格`validate`失败；
- 第二次`migrate`被失败History阻断，不是no-op。

两条路径复现一致，排除偶发执行和升级顺序差异。

## 4. Flyway checksum

| Migration | Flyway checksum | 执行结果 |
|---|---:|---|
| V2.5.3 | `-393819095` | Fresh/Upgrade均成功 |
| V2.5.4 | `-1504168058` | Fresh/Upgrade均失败 |

## 5. sys_menu结构

V2.5.4失败发生在永久DDL之前，两条路径均确认：

| 检查项 | 结果 |
|---|---|
| `menu_code`字段 | 不存在 |
| `(menu_code, delete_token)`唯一约束 | 不存在 |
| menu_code格式CHECK | 不存在 |
| V2.5.4永久DDL部分落地 | 否 |

排序规则核查：

- Schema默认排序规则：`utf8mb4_general_ci`；
- `sys_menu`表排序规则：`utf8mb4_0900_ai_ci`；
- V2.5.4临时映射表未显式声明排序规则，继承Schema默认值；
- 临时映射字段与`sys_menu`字符串字段在指纹等值比较时发生冲突。

## 6. 71条映射结果

V2.5.3执行后两条路径均有71条菜单、71个不同ID。V2.5.4在映射指纹预检阶段失败，因此：

- 71条显式映射未回填`menu_code`；
- 原`menu_id`、父子关系、路径和名称未被V2.5.4修改；
- 未发生按名称、路径或ID自动推断；
- 本项验收结论为**FAIL / NOT APPLIED**。

## 7. Workflow 10项菜单

V2.5.3成功生成10项Workflow M/C/B菜单，Fresh和Upgrade数量均为10，未产生第二套Workflow菜单。由于`menu_code`字段未落地，以下稳定编码无法完成数据库验收：

```text
workflow
workflow.definition
workflow.definition.create
workflow.definition.edit
workflow.definition.publish
workflow.instance
workflow.instance.start
workflow.task
workflow.task.approve
workflow.task.withdraw
```

结论：菜单数据存在，但稳定业务键治理未完成。

## 8. Workflow 9项权限

两条路径均验证：

- Workflow有效权限：9；
- 不同`permission_code`：9；
- 停用权限：0。

权限清单：

```text
workflow:manage
workflow:definition:view
workflow:definition:create
workflow:definition:edit
workflow:definition:publish
workflow:view
workflow:start
workflow:approve
workflow:withdraw
```

## 9. SUPER_ADMIN授权

| 检查项 | 结果 |
|---|---:|
| SUPER_ADMIN Workflow权限 | 9 |
| SUPER_ADMIN Workflow菜单 | 10 |
| 其他角色新增Workflow权限 | 0 |
| 其他角色新增Workflow菜单 | 0 |

V2.5.3的RBAC初始化结果本身符合预期，但组合链仍因V2.5.4失败而不可晋级。

## 10. 重复menu_code负向测试

未进入。`menu_code`字段和唯一约束尚未创建，无法在目标结构上验证重复活动`menu_code`由数据库拒绝。本项状态：**BLOCKED BY V2.5.4**。

## 11. 未知菜单阻断测试

未进入专项Schema验证。正常数据集已在永久DDL前因排序规则错误失败，继续构造未知菜单无法证明设计门禁本身有效，只会被更早的同一P0错误遮蔽。本项状态：**NOT REACHED**。

## 12. 重复映射阻断测试

未进入。显式映射指纹比较在断言执行前发生排序规则错误，无法有效验证重复映射门禁。本项状态：**NOT REACHED**。

## 13. 逻辑删除兼容测试

未进入。复合唯一约束`(menu_code, delete_token)`未落地，无法执行活动/逻辑删除组合的真实数据库测试。本项状态：**NOT REACHED**。

## 14. Schema fingerprint

失败态只读结构指纹覆盖tables、columns、indexes、foreign keys、CHECK constraints和views：

| 指纹 | Fresh | Upgrade | 结果 |
|---|---|---|---|
| 规范化行数 | 5066 | 5066 | 一致 |
| Schema SHA-256 | `1fc7a9ef19ebc768b0d5e0b3fd5f0c43b0d5c4131d367ffc0f3d43175e3fd70f` | 同左 | 一致 |
| sys_menu结构SHA-256 | `ad15fc65a83cab70d40cf64fa7f9da579bb250f66b37d4d18ab5ffb0a17361e4` | 同左 | 一致 |

该一致性仅证明两条路径停在相同失败态，不代表目标V2.5.4 Schema验收通过。

## 15. 历史资产哈希

重新计算正式扫描目录全部20个SQL，`SHA256SUMS`匹配20/20。重点资产：

```text
V2.5.3  6d6904da8028830cdc8fa7b9125994d37d2c916a09277ae9d4526f5646144e26
V2.5.4  c93111bd75795604837f019ffc7df48f23635fb0d0d7e69a4f1e984bc483b644
```

V2.4.0至V2.4.9、V2.5.0至V2.5.4均无摘要漂移，Migration SQL未被修改。

## 16. 临时环境清理

验收完成后停止Fresh和Upgrade临时MySQL进程，确认33780、33781端口关闭，并将本Sprint一次性数据目录、运行时副本和测试数据移入Windows回收站（可恢复）。未连接或修改任何开发、测试、预生产、生产及未知数据库。

## 17. 资产状态

组合验收未满足晋级条件，不修改Inventory、README或SHA256SUMS：

```text
V2.5.3: CANDIDATE / NOT_EXECUTED / flyway_checksum: null
V2.5.4: CANDIDATE / NOT_EXECUTED / flyway_checksum: null
```

V2.5.3单版本历史验收曾因菜单稳定键缺失失败；本次正式链`V2.5.3 -> V2.5.4`又因V2.5.4排序规则不兼容失败。两次FAIL记录均保留。

## 18. 最终结论与处理建议

最终结论：**FAIL / BLOCKED / NOT PROMOTED**。

P0阻断：V2.5.4临时映射表与`sys_menu`使用不同排序规则，导致正常Fresh和Upgrade路径均无法迁移。

下一步必须另行评审修复策略。由于V2.5.4尚未晋级且两条验收路径均未成功，建议由数据库治理负责人决定：

1. 是否允许修订候选V2.5.4，在临时映射字符串列或比较表达式上显式使用与`sys_menu`兼容的排序规则；
2. 或废弃当前候选并以新的Migration版本提供修复，但不得让失败的V2.5.4进入正式扫描链；
3. 修复后重新执行完整Fresh、V2.5.2 Upgrade、strict validate、二次no-op、四类负向测试及双路径目标态指纹。

在上述问题关闭前，禁止将V2.5.3或V2.5.4标记为`CANONICAL_IMMUTABLE`或`EPHEMERAL_MYSQL8_VALIDATED`。
