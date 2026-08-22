# V2.5.4候选Migration排序规则修复与组合验收报告

Sprint：2-3.7-WF2.5.5
日期：2026-08-10
结论：**PASS**

## 1. 修复边界

仅修改尚未晋级的`V2.5.4__govern_rbac_menu_unique_key.sql`。V2.5.0至V2.5.3、Workflow/Investment业务代码和数据库业务逻辑均未修改，也未创建V2.5.5。

## 2. 排序规则修复

以下临时映射字符串列显式声明：

```text
CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
```

- `expected_menu_type`
- `expected_menu_name`
- `expected_path`
- `expected_permission`
- `menu_code`
- `assertion_name`

两个临时表同时显式声明默认字符集与排序规则；永久`sys_menu.menu_code`在ADD和MODIFY阶段也显式声明同一规则，不再依赖Schema默认collation。

## 3. 摘要变化

```text
修复前：c93111bd75795604837f019ffc7df48f23635fb0d0d7e69a4f1e984bc483b644
修复后：dea9284682001d3e3ab8a667a4ec863d5e66a33a61771628f64044ce004768a7
```

V2.5.3 SHA-256仍为：

```text
6d6904da8028830cdc8fa7b9125994d37d2c916a09277ae9d4526f5646144e26
```

正式目录20个Migration与`SHA256SUMS`匹配20/20。

## 4. 静态检查

- `RbacMenuUniqueMigrationContractTest`：5/5通过；
- 两个临时表均显式声明字符集/排序规则；
- 所有参与菜单映射比较的临时字符串列均为`utf8mb4_0900_ai_ci`；
- `flyway_checksum`保持`null`。

## 5. 真实MySQL组合验收

| 项目 | Fresh | Upgrade |
|---|---|---|
| MySQL | 8.4.9 | 8.4.9 |
| 起点 | V2.0.0基线 | V2.0.0基线后迁移至V2.5.2 |
| V2.5.3 | 成功一次 | 成功一次 |
| V2.5.4 | 成功一次 | 成功一次 |
| strict validate | PASS | PASS |
| 第二次migrate | no-op | no-op |
| 失败History | 0 | 0 |

观察到的Flyway checksum：

```text
V2.5.3: -393819095
V2.5.4:  861538991
```

## 6. 结构与数据结果

- `menu_code VARCHAR(128) NOT NULL`，字符集`utf8mb4`、排序规则`utf8mb4_0900_ai_ci`；
- 唯一索引`uk_sys_menu_code(menu_code, delete_token)`存在；
- `chk_sys_menu_code_format`存在且`ENFORCED=YES`；
- 菜单71条、不同ID 71、非空有效编码71、重复编码0；
- Workflow稳定菜单编码10项；
- Workflow权限9项，全部启用且唯一；
- SUPER_ADMIN拥有9项权限和10项菜单；
- 其他角色新增Workflow授权0。

## 7. 负向与兼容测试

Fresh和Upgrade均验证：

- 重复活动`menu_code`：MySQL 1062拒绝；
- 非法`menu_code`格式：MySQL 3819拒绝；
- 相同`menu_code`配合不同非零`delete_token`：允许；
- 测试残留数据：0。

## 8. Schema指纹

两条路径目标态规范化结构均为5070行，SHA-256一致：

```text
90365d2714002a12aaad61dc54eb43dc1911233420687cf9afee15f6b7ba7dd9
```

## 9. 资产状态

```text
V2.5.3: CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED
V2.5.4: CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED
flyway_checksum: null
```

首次组合验收的MySQL 1267失败记录继续保留，不覆盖、不删除。
