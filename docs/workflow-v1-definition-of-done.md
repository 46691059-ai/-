# Workflow V1.0 Definition of Done

## 1. 术语

`WORKFLOW_V1_INTERNAL_ENGINEERING_COMPLETE` 表示代码库内可验证的架构、数据库资产、测试、运行安全和文档已经收口；它不等于 ROLE 生产启用，也不表示外部 Directory、DataScope、SoD 或 Audit 已验收。

## 2. 内部工程完成门禁

| Gate | 判定 |
|---|---|
| Architecture | PASS：Domain 纯净，依赖方向正确，无隐藏 ROLE 执行路径 |
| Compile | PASS：Java 21 |
| Spring Context | PASS |
| Full Tests | PASS |
| Migration SHA | PASS：38/38 |
| Canonical Inventory | PASS：生产者、消费者、持久化与测试可追溯 |
| Legacy Regression | PASS：Legacy、USER、DIRECT 无退化 |
| Runtime Safety | PASS：ROLE 默认禁用、无 fallback/skip/auto-enable |
| Production Dependency Scan | PASS：生产源码不直接依赖 Fake/Stub/Test fixture |
| PII/Secret Scan | PASS：未发现 Directory PII/Secret 固化 |
| Diff Integrity | PASS：`git diff --check` |
| Migration Governance | PASS：无未登记、重复或 V2.6.17 Migration |
| Runtime State | PASS：EXPLICIT USER ACTIVE；ROLE PREPARED/NON_EXECUTABLE |

全部满足时状态为：

```
WORKFLOW_V1_INTERNAL_ENGINEERING_COMPLETE
ROLE_EXTERNAL_VALIDATION_PENDING
ROLE_RUNTIME_DISABLED
```

## 3. 外部未完成项

真实 Directory 连通、认证/TLS、Contract handshake、Historical/Revision/Complete/Pagination/SLA、DataScope、平台/业务 SoD、External Audit、Feature Flag/Canary/Kill Switch 生产控制源、Security Approval 与 RACI 均属于独立外部门禁。当前为 `EXTERNAL_DEPENDENCY`，不得由内部 Fake/Sandbox 测试替代。

## 4. 阻断规则

- P0：立即标记 `WORKFLOW_V1_INTERNAL_ENGINEERING_BLOCKED`。
- 涉及 Canonical、历史 Migration、Runtime 语义、历史数据或外部 Capability 的 P1：记录 `INTERNAL_CLOSURE_BLOCKER`，不得在收口 Sprint 扩大修改范围。
- 纯内部、无语义变化的 P2/P3 可修复并完整回归。

## 5. 明确非 DoD

本 DoD 不允许推导出 `ROLE_RUNTIME_READY_FOR_PRODUCTION`、`ROLE_RUNTIME_ENABLED`、`ROLE_DIRECTORY_ACTIVE` 或 `PRODUCTION_READY`。这些状态必须经过 External、TEST/PREPROD、Canary 与 Production Enablement 四个独立门禁。
