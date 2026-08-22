# V2.6.15 ROLE Runtime Execution Admission Persistence Validation Report

## 1. 最终结论

`FAIL / BLOCKED / NOT_PROMOTED`。

V2.6.15在Fresh和V2.6.14 Upgrade两条真实路径均因MySQL `6125`失败：`fk_role_admission_slot_candidate`引用`workflow_role_binding_candidate_snapshot(id,snapshot_id,delete_token)`，但父表没有该精确列序的唯一键。依据验收规则，后续依赖完整Schema的业务矩阵不得继续，也不得伪造PASS。

## 2. 环境版本

- MySQL Community Server：8.4.9，本机回环一次性实例
- Flyway：13.0.0
- Java：21.0.12
- Maven：3.9.9
- 生产、托管或未知数据库连接：0

## 3. Fresh结果

- V2.0.0基线至V2.6.14：PASS
- V2.6.15：FAIL，SQLSTATE `HY000`，错误码`6125`
- Flyway失败历史：1行
- strict validate、二次migrate no-op：因失败历史存在而不具备执行前提，未伪造结果

## 4. Upgrade结果

V2.6.14成功后单独执行V2.6.15，结果与Fresh一致：错误码`6125`，仅V2.6.15失败。未执行repair、未改history、未baseline绕过。

## 5. Flyway checksum

真实记录：`1424227542`。Inventory保留`flyway_checksum: null`作为未晋级候选字段，并新增`observed_flyway_checksum`记录本次失败证据。

## 6. SHA-256

V2.6.15保持：`405fb7fc3bc928fc84643bcbf5583cd9f9555dad8209ad6b4d962529ce969887`。治理清单逐文件复算：`37/37 PASS`。V2.6.14 SHA保持不变。

## 7. Schema Fingerprint

不可生成“成功最终Schema”Fingerprint。Fresh和Upgrade均停在相同失败点并形成同类部分安装，但部分Schema不是合格目标Schema，不可替代验收Fingerprint。

## 8. Candidate Snapshot增强结果

ALTER已被MySQL隐式提交，十个未来证据字段真实出现，包括`directory_result_hash`、`definition_id`、`definition_version_id`、`node_id`和`node_binding_hash`。未执行任何UPDATE/backfill/inference；当前验收库无历史Candidate夹具，故历史行NULL语义不宣称已完整验证。

## 9. Guard结果

部分安装场景在V2.6.14后预建目标表，V2.6.15临时Guard以CHECK violation拒绝Migration。

## 10. 零永久DDL结果

Guard失败前后：Candidate新增列`0 -> 0`，其余三个目标表`0 -> 0`，Admission Trigger `0 -> 0`。该单项PASS。

正常Migration失败路径不是Guard失败：MySQL隐式提交导致部分DDL残留，这是阻断性发布风险。

## 11. 四表结构核查

失败后实际存在：Admission、Evidence、Event三表；不存在Slot。部分对象统计：3表、107列、64条information_schema索引行、6个FK、16个CHECK、0个Admission Trigger。目标四表结构不完整，FAIL。

## 12. Admission强归属

Admission/Evidence/Event已创建部分FK，但Slot父键不满足MySQL唯一键要求，完整强归属链未形成。负向矩阵未执行，状态`BLOCKED_BY_MIGRATION_FAILURE`。

## 13. 28项Evidence

未执行真实链路测试。Evidence表存在，但Slot和Trigger缺失，无法形成符合冻结设计的完整Admission事务，状态`NOT_RUN_DUE_TO_P0`。

## 14. Capability Evidence

未执行，原因同上。不得把Java领域测试替代真实MySQL验收。

## 15. Evidence Root Hash

Java专项回归PASS；真实数据库最终Decision/Evidence Root约束未执行，状态`BLOCKED`。

## 16. Feature Flag

真实持久化链路未执行，状态`BLOCKED`。

## 17. Canary

真实持久化链路未执行，状态`BLOCKED`。

## 18. Kill Switch

真实持久化链路未执行，状态`BLOCKED`。

## 19. Revision Fence

Candidate字段已出现，但R1/H1正负向Admission测试未执行，状态`BLOCKED`。

## 20. Definition/Node证据

Candidate ALTER的FK创建成功；完整Admission跨Definition/Version/Node负向矩阵未执行，状态`BLOCKED`。

## 21. Slot CAS

FAIL：Slot表未创建，无法执行CAS单赢家。

## 22. 并发

四类双Session并发测试未执行，因为Slot不存在。不得宣称单赢家。

## 23. 幂等

Admission唯一键已在部分表中出现，但完整事务链未安装；真实幂等验收未执行。

## 24. Event状态机

Event表存在，但Insert Guard Trigger尚未创建；真实状态链验收未执行。

## 25. Append-only

FAIL/未形成：Admission、Evidence、Event六个UPDATE/DELETE Trigger均未创建。

## 26. 事务回滚

未执行应用数据库事务矩阵。Migration自身已证明DDL不能作为单事务回滚：失败后保留三表和Candidate字段。

## 27. 合法Persistence链路

未形成。没有创建WorkflowInstance、NodeExecution、Task、CandidatePool或Claim。

## 28. Legacy/USER兼容

应用回归PASS；`EXPLICIT_USER_V1`仍ACTIVE，`ROLE_DIRECTORY_V1`仍PREPARED/NON_EXECUTABLE。真实Upgrade库没有构造历史Candidate数据，因此“历史Candidate字段保持NULL”仅由无回填SQL和空数据结果支持，不提升为完整数据库验收PASS。

## 29. 应用回归

- Java 21 compile/Spring Boot Context：PASS
- 后端全量测试：474通过，0失败，0错误，0跳过
- V2.6.15 Migration Contract、Admission专项、Domain纯净、Production Dependency Scan：PASS
- Controller/API新增：0
- Investment修改：0
- ROLE Runtime Enabled：false

## 30. Migration资产状态

- V2.6.15：`CANDIDATE / EPHEMERAL_MYSQL8_VALIDATION_FAILED`
- 不晋级`CANONICAL_IMMUTABLE`
- V2.6.14保持Canonical状态
- 未创建V2.6.16

## 31. 临时环境清理

Fresh、Upgrade、Guard及首次路径探测的mysqld进程均已shutdown；端口36511—36513、36521—36523无监听；两组一次性数据目录均已删除，不可恢复。

## 32. 剩余风险

1. Slot Candidate复合FK缺少父唯一键，是当前P0。
2. 正常Migration失败会留下部分DDL；候选重设计必须考虑MySQL隐式提交和失败恢复策略。
3. Evidence完整性、Capability、Hash、事件、append-only、事务和并发矩阵尚未到达可执行状态。
4. 未来修复不得通过repair、history编辑、baseline或out-of-order掩盖失败。

## 33. 下一步建议

启动独立治理Sprint，对未晋级V2.6.15候选进行设计评审：优先为Candidate提供与Slot FK精确匹配的稳定唯一Owner Key，或调整Slot FK使用现有`uk_role_binding_snapshot_owner`完整列；同时设计失败候选归档/重建和部分DDL恢复策略。任何SQL修复必须在新任务明确授权后进行，然后重新执行全部WF5.18.1矩阵。不得进入WF5.19或ROLE Runtime。
