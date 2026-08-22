# Workflow V1 Release Whitespace Exception Manifest

治理日期：2026-08-22（Asia/Shanghai）

治理原则：`HISTORICAL IMMUTABILITY > COSMETIC WHITESPACE CLEANUP`

本清单只批准下列 19 个已冻结资产当前既有的 `new blank line at EOF.`。批准粒度为精确路径、精确行号和精确 warning 类型；不使用任何通配例外。清单不关闭 `git diff --cached --check`，不授权修改、格式化或重新生成任何历史资产。

## 1. Explicit approved exceptions

| path | line | warningType | assetType | immutableStatus | originalSHA256 | exceptionType | approvedRationale |
|---|---:|---|---|---|---|---|---|
| `database/migration/archive/failed-candidates/V2.6.14__create_role_runtime_binding_persistence_foundation__failed_bcbcbb6b.sql` | 408 | `NEW_BLANK_LINE_AT_EOF` | FAILED_CANDIDATE_ARCHIVE | HISTORICAL_EVIDENCE_IMMUTABLE | `27d1f6ebd330e2416d11756dd1c3e32a5676cf51eee12b00a6c0c815334cffa2` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 失败候选用于证明候选演进和失败事实；清理会改变历史证据摘要。 |
| `database/migration/archive/failed-candidates/V2.6.15__create_role_runtime_execution_admission_persistence__failed_405fb7fc.sql` | 395 | `NEW_BLANK_LINE_AT_EOF` | FAILED_CANDIDATE_ARCHIVE | HISTORICAL_EVIDENCE_IMMUTABLE | `246c6a8d42e1ea36538213d6421882fc1d2b9acc1f478f4ca843f027bbe8ea56` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 失败候选用于证明候选演进和失败事实；清理会改变历史证据摘要。 |
| `database/migration/mysql/V2.6.10__harden_role_runtime_persistence_integrity.sql` | 116 | `NEW_BLANK_LINE_AT_EOF` | CANONICAL_MIGRATION | CANONICAL_IMMUTABLE | `2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6` | APPROVED_CANONICAL_EOF_BLANK_LINE | 已冻结并纳入 SHA/Flyway 治理；禁止因 cosmetic whitespace 改变内容、checksum 或历史。 |
| `docs/workflow-center-domain-design.md` | 326 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `824c25af1decddcad62955cadb8e0407fc021c4cb13cf09dc6ca9da0663ce2ab` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已引用的冻结领域设计；非当前控制文档、配置、脚本或源代码。 |
| `docs/workflow-investment-integration-v2-design.md` | 232 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `f4fa57bb5d9a83e2d7e623cdb230c4eab14d22849b54055fc3b82635f8f3d6b0` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已引用的冻结集成设计；非当前控制文档、配置、脚本或源代码。 |
| `docs/workflow-role-resolver-governance-design.md` | 225 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `c4925c64f62412f40fe6fa9ece6c7ca533aeeda416f19bee5a4940978d9eb3fa` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已冻结 Resolver 治理证据；不得为 cosmetic cleanup 改写。 |
| `docs/workflow-role-runtime-activation-architecture-design.md` | 418 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `9e9b21183cf81a3adc14a3d27949adf23a6f8b64efa0d95d374f2587f4451140` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已冻结 Activation 架构证据；不得为 cosmetic cleanup 改写。 |
| `docs/workflow-role-runtime-execution-admission-v2615-final-approval-integrity-governance-design.md` | 327 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `6543ac5d7369e04e2d23a4dcffd052f2eca11c9998c5dedc289ad84d45157ec9` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已引用的 V2.6.15 完整性治理证据；不得改写。 |
| `docs/workflow-role-runtime-persistence-governance-design.md` | 348 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `6181ead1eb9677cb9d36057175a2dd27e01937d98cbc682f0e696d34df516670` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已冻结持久化治理设计；不得为 cosmetic cleanup 改写。 |
| `docs/workflow-role-runtime-persistence-implementation-report.md` | 341 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `bf369caf846017bc29e67107e54174bc6beaa00f659bc83b6507f9f9d7f0474a` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已引用的实施证据；不得为 cosmetic cleanup 改写。 |
| `docs/workflow-role-runtime-persistence-v2610-forward-fix-implementation-report.md` | 216 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `24cafc2c081866ee8465900a700860fa35e8a7e328a7514e5c7a66777a0d3f62` | APPROVED_HISTORICAL_EOF_BLANK_LINE | V2.6.10 Forward Fix 历史证据；与 canonical Migration 审计链关联。 |
| `docs/workflow-role-runtime-persistence-v269-validation-report.md` | 190 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `c490d0d2369f97baa27222587ccb46457ea5ca86b1e3cacb987ba77f962442d1` | APPROVED_HISTORICAL_EOF_BLANK_LINE | V2.6.9 失败/验收演进证据；不得改写历史结论。 |
| `docs/workflow-v1-release-baseline-manifest.md` | 71 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `73e743872b718b2c9b737ab6bf13ad6f6507b54a1a366d359fe05ef6b77deb22` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 上一阶段冻结时点的基线证据；本清单作为后续显式例外补充，不改写原记录。 |
| `docs/workflow-v1-role-runtime-canary-activation-procedure.md` | 25 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `b8c71818c8b7e6080168f5784114ee8fa5a2df0b4467dffe9e8f47df5ed36aa5` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已冻结 Canary 操作证据；当前仍未授权 Canary。 |
| `docs/workflow-v1-role-runtime-canary-go-no-go-checklist.md` | 44 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `4c4ca259a1e5e3bc00e165107948fa9ff8f3db1c85852c533e598424463620f9` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已冻结 Canary Gate 证据；当前仍未授权 Canary。 |
| `docs/workflow-v1-role-runtime-canary-monitoring-acceptance-matrix.md` | 35 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `6f64de5a970a1cf06ae2d58f47027012865ed619169706a3fa8b6c0455e4ed5d` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已冻结监控验收证据；非机器解析配置。 |
| `docs/workflow-v1-role-runtime-canary-release-change-pack.md` | 48 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `41fcd853508065dfd2325a17f1285d10fc8c904e03c6517a1952bca755ebd24a` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已冻结 Release change pack 证据；当前仍未授权执行。 |
| `docs/workflow-v1-role-runtime-canary-rollback-runbook.md` | 37 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `da16b13cb4fefecd5b3dd6e38537e1d0582909971a85f0cb229a7cb5f2ebbac8` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已冻结回滚运行证据；非脚本或机器配置。 |
| `docs/workflow-v1-role-runtime-canary-scope-template.md` | 48 | `NEW_BLANK_LINE_AT_EOF` | HISTORICAL_EVIDENCE_DOC | HISTORICAL_EVIDENCE_IMMUTABLE | `4efee84788710ec444fd1f2d7d394fd7703597197cccb72d914210e57505471a` | APPROVED_HISTORICAL_EOF_BLANK_LINE | 已冻结 Canary scope 证据；当前 scope 未授权。 |

## 2. Classification summary

| classification | count | governance |
|---|---:|---|
| CANONICAL_MIGRATION | 1 | 内容与 SHA 必须保持；禁止 repair、格式化或重算 checksum |
| FAILED_CANDIDATE_ARCHIVE | 2 | `HISTORICAL_EVIDENCE_IMMUTABLE`；不在 Flyway canonical scan root |
| HISTORICAL_EVIDENCE_DOC | 16 | 已引用历史证据；不是当前新增控制文档、配置、脚本或源码 |
| CURRENT_RELEASE_ASSET | 0 | 不允许任何例外；必须严格 whitespace clean |

## 3. Gate contract

- Gate A：对全部非例外 staged 文件运行严格 whitespace 检查，必须 0 warning。
- Gate B：完整执行 `git diff --cached --check`，实际 warning 必须与上表 19 项按路径、行号、类型精确相等。
- Canonical protection：V2.6.10 SHA-256 必须为 `2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6`，且工作树不得存在相对 index 的修改。
- 任一新增、第20项、缺失项、行号漂移、类型漂移、重复项或 SHA 漂移均立即失败。

批准结果只表示 Release Commit Gate 可以接受这 19 项历史 warning；不表示关闭 Git whitespace 检查，也不授权今后的新增资产复用例外。
