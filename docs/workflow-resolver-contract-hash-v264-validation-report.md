# V2.6.4 Resolver Contract Hash Collation 验收报告

## 1. 验收结论

结论：**PASS**。

V2.6.4通过Fresh、V2.6.2连续升级和V2.6.3失败环境修复三条真实MySQL 8.4.9/Flyway 13.0.0隔离验收路径。V2.6.3及更早Migration、Investment和Resolver Version Routing业务逻辑均未修改，未实现ROLE/POSITION/ORG。

## 2. 修改文件

- `database/migration/mysql/V2.6.4__fix_resolver_contract_hash_collation.sql`
- `database/migration/mysql/SHA256SUMS`
- `database/flyway/migration-inventory.yml`
- `database/migration/mysql/README.md`
- `database/flyway/scripts/validate-v264.ps1`
- `docs/workflow-resolver-contract-hash-v264-validation-report.md`

## 3. Migration变化

V2.6.4执行两项增量治理：

1. 使用二进制比较识别V2.6.3错误接受的合法大写/混合大小写十六进制Hash，并规范化为小写；SHA-256值本身不变。
2. 将 `workflow_instance.resolver_contract_hash` 修改为 `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin`。

V2.6.3原有完整性、线性实例绑定和Hash正则CHECK保持不变；切换为 `ascii_bin` 后，小写正则获得大小写敏感语义。

## 4. Flyway结果

环境：MySQL Community Server 8.4.9、Flyway Community Edition 13.0.0。三套实例均为本次新建、仅绑定 `127.0.0.1` 的一次性实例，验收后已停止；未连接生产、托管或未知数据库。

| 路径 | 执行结果 | History | Validate | 二次Migrate |
|---|---|---:|---|---|
| Fresh V2.0.0 → V2.6.4 | 成功执行26个版本化Migration | 27成功/0失败 | 通过 | no-op |
| V2.6.2 → V2.6.3 → V2.6.4 | 仅执行后两个版本 | 27成功/0失败 | 通过 | no-op |
| V2.6.3失败环境 → V2.6.4 | 仅执行V2.6.4 | 27成功/0失败 | 通过 | no-op |

- V2.6.4 SHA-256：`0bb017ea621980e150870bb9bac3dd48863afbc2b63b33654ab6344aced9bb55`
- V2.6.4 Flyway checksum：`-1847492777`
- V2.6.3 SHA及SQL内容保持不变。

## 5. Hash字段变化

| 属性 | V2.6.3 | V2.6.4 |
|---|---|---|
| 类型 | `CHAR(64)` | `VARCHAR(64)` |
| 字符集 | `utf8mb4` | `ascii` |
| 排序规则 | `utf8mb4_general_ci` | `ascii_bin` |
| 默认值 | `NULL` | `NULL` |
| 可空 | 是 | 是 |

最终 `workflow_instance` 仍为41字段、13索引、17个CHECK；未新增业务表或改变Resolver路由数据模型。

## 6. Schema Fingerprint

三条路径最终指纹完全一致：

- 完整Schema：`08d44c91fdfa7e9f8ebef843288c06b5144e3aeb99d0c337aa4a186c68f48442`
- Workflow Schema：`04fe532fbe94322dac9d3f347290a682581cd777e715167d2d97bd87e78a478f`

一次性证据目录：`C:\Users\WUKONG\AppData\Local\Temp\enterprise-v264-validation-20260811-120408\evidence`。

## 7. 兼容与负向测试

| 用例 | 结果 |
|---|---|
| 合法小写SHA-256 | 接受，PASS |
| 64位全大写SHA-256 | MySQL 3819拒绝，PASS |
| 混合大小写SHA-256 | MySQL 3819拒绝，PASS |
| 长度错误 | MySQL 3819拒绝，PASS |
| 非Hex字符 | MySQL 3819拒绝，PASS |
| V2.6.3已存全大写Hash | V2.6.4规范化为小写，PASS |
| Legacy实例NULL Binding | 保持兼容，PASS |

## 8. 资产状态

- V2.6.4：`CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- V2.6.3：作为V2.6.4不可变前置资产，状态为 `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED_WITH_V264`
- V2.6.3独立验收失败历史继续保留，禁止脱离V2.6.4单独发布。

## 9. 剩余风险

- V2.6.4已在MySQL 8.4.9验证，尚未执行达梦、人大金仓兼容性验收。
- 发布编排必须保证V2.6.3与V2.6.4连续执行，禁止将V2.6.3作为发布终点。
- 本次只修复存储与数据库约束，不增加Resolver类型或动态路由能力。
