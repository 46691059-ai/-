# Workflow ROLE Runtime Production Capability Final Validation Report

## 1. Final Conclusion

**NO-GO / CAPABILITY FOUNDATION IMPLEMENTED / FINAL EXTERNAL E2E BLOCKED.**

WF-CAP-FINAL completed the in-repository Production Capability implementation, V2.6.18 database governance, isolated MySQL/Flyway validation, Spring startup and full regression. It did not fabricate the last external proof. No reachable TEST/PREPROD External Audit Sink, service credential, trusted TLS endpoint, or live Receipt contract instance was delivered to this workspace. Consequently the mandatory real chain cannot prove `Internal Audit -> External Delivery -> Receipt`, and the independent Canary enablement gate remains closed.

ROLE Runtime stayed disabled throughout. Investment and V2.6.15-V2.6.17 were not modified.

## 2. DataScope

`ProductionRealtimeCapabilityAdapters.DataScope` reuses the existing `DataPermissionService`; no parallel authorization system was created. It independently checks principal/candidate identity and existing ALL/ORG/ORG_AND_CHILDREN/CUSTOM/SELF results. DENY and provider exceptions fail closed. `ProductionRoleClaimCommitCapabilityGate` re-reads the current DataScope inside the short Claim transaction, closing Prepare-to-Commit drift.

## 3. Platform SoD

`ProductionRealtimeCapabilityAdapters.PlatformSoD` evaluates Workflow instance/action facts and blocks initiator self-approval and prior critical actor reuse. It emits deterministic, PII-minimized evidence and returns INDETERMINATE on unavailable facts. The existing transaction SoD policy remains an additional fence. Final admission is not granted because the requested organization-configured incompatible-node and governance-administrator production rule directory has not been proven against a live governed policy source.

## 4. Business SoD

The generic Business SoD port is backed by the shared versioned governance store, keyed by enterprise and opaque business scope/rule reference. Missing, DENY, or unavailable configuration fails closed. It has no Investment dependency and accepts no caller-selected approver identity. Database/contract behavior is verified; no external business-policy service is required for the controlled V1 provider.

## 5. External Audit

Claim success now atomically writes the existing internal immutable audit and `workflow_role_external_audit_outbox`. `ExternalAuditDispatcher` performs bounded `FOR UPDATE SKIP LOCKED` dispatch, CAS state transitions, retry/backoff/dead-letter classification, receipt verification and immutable receipt persistence. `JdkHttpsExternalAuditSinkClient` enforces HTTPS, service authentication and idempotency by audit event ID. The external endpoint itself was not provisioned, so delivery completion is blocked rather than simulated.

## 6. Feature Flag

The shared append-only control store provides versioned GLOBAL, ENTERPRISE and WORKFLOW_DEFINITION decisions. All levels must be present and ON. Missing/ambiguous/unavailable is OFF by fail-closed semantics. Runtime master enablement still defaults false.

## 7. Canary

Canary lookup is exact and versioned. The commit fence uses enterprise + definition + definition version + node; no wildcard/range/default-all path exists. Missing or mismatched configuration denies execution.

## 8. Kill Switch

GLOBAL, ENTERPRISE and DEFINITION_VERSION controls are re-read at commit. Only explicit ALLOW permits the transaction; missing data or STOP_NEW_ONLY / STOP_NEW_AND_CLAIM / FREEZE_ALL_PENDING denies the Claim path. The safe application default remains `STOP_NEW_AND_CLAIM` with runtime disabled.

## 9. Production Capability Bundle

`RoleRuntimeProductionCapabilityBundle` and its single factory assemble Directory, user/org status, DataScope, Platform SoD, Business SoD, audit availability, Feature Flag, Canary and Kill Switch. Startup construction rejects classpath adapters whose names indicate Fake/Stub/InMemory. Production adapters are metrics-decorated without changing domain ports. The factory does not register or activate ROLE_DIRECTORY_V1.

## 10. Directory

The previously delivered isolated TEST HTTPS -> provider -> MySQL -> `ProductionRoleDirectoryAdapter` evidence remains valid: 60/60 successful requests, P50 126.137 ms, P95 186.502 ms, P99 223.398 ms, contract/hash/revision/TLS/auth negative tests passed. Evidence: `D:\codex-validation-org-dir-2-20260820-180622`. This Sprint did not alter the frozen directory contract.

## 11. Candidate Freeze

Existing V2.6.6/V2.6.7 frozen-pool ownership, no-refresh, local NOT_CANDIDATE preflight and current membership recheck remain intact. Production capability wiring does not add candidates or rewrite historical pools. Full regression passed, but the complete live combined Directory-mutation scenario was not rerun because the final external audit chain is unavailable.

## 12. Realtime Eligibility

All 27 ordered Validator Evidence and 10 Capability Evidence contracts remain unchanged. Production ports supply real directory/system/DataScope/governance facts. DENY and INDETERMINATE fail closed. V2.6.16 evidence ownership, TTL, hash, single consumption and transaction rollback tests passed in regression.

## 13. E2E Claim

Internal Claim runtime, admission, evidence and rollback suites pass. The new commit fence executes after row locks and before mutation, detecting DataScope/SoD/Feature/Canary/Kill Switch drift. A full real TEST/PREPROD Claim ending in a live external receipt was **not executed**; claiming PASS would violate the Sprint's no-fabrication rule.

## 14. Audit Receipt

V2.6.18 persists `auditEventId`, external receipt ID, provider code/version, received time, payload hash, receipt hash and status. FK, uniqueness, checksum/hash checks and append-only triggers passed real MySQL validation. A live sink receipt is absent because no endpoint resource was supplied.

## 15. Failure Matrix

Code/contract coverage is fail closed for directory failure/partial/revision/hash, DataScope DENY/unavailable, SoD violation/unavailable, local audit failure, outbox failure, Feature OFF/unavailable, Canary mismatch, Kill Switch deny/unavailable, evidence expiry/hash/reuse and Claim conflict. Real MySQL additionally rejected control mutation, invalid hashes and orphan outbox/receipt rows. External sink auth/TLS/timeout behavior exists in the HTTPS adapter but cannot be promoted as live evidence without the endpoint.

## 16. Concurrency

Existing Claim single-winner/CAS/lock-order regression remains green, and V2.6.18 uses `SKIP LOCKED`, version CAS and unique event/claim keys. The Sprint did not produce the required combined live 10/25/50 run through an external receipt sink; therefore the final concurrency acceptance item remains blocked.

## 17. Configuration Drift

Prepare evidence is not trusted at commit. Current DataScope and all dynamic controls are queried again inside the Claim transaction. Any missing version, changed deny decision or unavailable store rejects before Task/Pool mutation. This was compile/unit/regression verified; a live mid-flight external E2E drift run remains pending.

## 18. Performance

Only the trusted real Directory observations are available (P50 126.137 ms / P95 186.502 ms / P99 223.398 ms). No fabricated end-to-end percentiles are reported for DataScope, SoD, evidence DB, Claim transaction or external delivery.

## 19. Monitoring

`RoleRuntimeCapabilityMetrics` records PII-free per-capability latency/outcome and Claim metrics remain available. V2.6.18 exposes queryable outbox backlog, retry/dead states and receipt status for operational metrics/alerts. Required dashboard names map to directory, DataScope, SoD, governance-block, eligibility, Claim and audit-outbox dimensions; production alert thresholds still require environment ownership.

## 20. Security / PII

The HTTPS adapter rejects plaintext HTTP and missing service credentials, sends credentials only as headers, and does not log them. Evidence/hashes omit HR detail. Source scan found no committed private key or credential value in the new paths. Controlled user/org IDs remain audit identifiers; no phone, ID card, address or salary fields are emitted.

## 21. Legacy Regression

Java 21 full backend regression: **571 tests, 0 failures, 0 errors, 0 skipped**. Spring Boot contexts pass. EXPLICIT_USER_V1 remains ACTIVE; USER/DIRECT/Legacy do not depend on the production bundle and external audit outbox is added only when ROLE realtime evidence is present.

## 22. Migration

V2.6.18 creates only the shared governance control store and external audit outbox/receipt. MySQL Community Server 8.4.9 / Flyway 13.0.0 isolated results: Fresh PASS, V2.6.17 Upgrade PASS, strict validate PASS, second migrate no-op, 41 successful Flyway history rows, checksum `1856348077`.

- SHA-256: `feca0f252d44a61938552ed37764aecc2d0cf78aa9e35d81b71d5bf73d1d3542`
- Full fingerprint: `16134c8087d2fe9319ba9867f3bffe380f9c8302d5456d9b28ab40120bc3474d`
- Workflow fingerprint: `4e307033a866111a19a4af1f24ba8ebdd51bca70d5899648a5c4d2e2247bfb83`
- Capability fingerprint: `d01127549bd0ebcfe42f9616be1cd7ecef3f1b2ec860563351c3ef393013fb4c`
- Governed hashes: 40/40 PASS
- Evidence root: `D:\codex-validation-wf-cap-final-20260821-094353`
- Asset: `CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`

## 23. Registry Decision

No registry activation was performed. Because final external E2E evidence is incomplete, ROLE_DIRECTORY_V1 remains `PREPARED / NON_EXECUTABLE`; it is not promoted to EXECUTION_ELIGIBLE.

## 24. Canary Decision

Independent Canary enablement is **NOT AUTHORIZED**. Feature Flag remains default OFF, the static master gate remains false, and safe Kill Switch behavior remains effective.

## 25. Remaining Risks

P0 external resource needed for closure: a dedicated TEST/PREPROD External Audit Sink HTTPS URL, trusted certificate chain, non-secret service identity reference, injected service credential, frozen request/receipt contract, idempotency commitment and observable retention/SLA. After delivery, the same Sprint evidence must execute the combined real Claim, receipt, outage/retry, 10/25/50 concurrency and configuration-drift matrix. Platform SoD also still needs live validation of organization-configured incompatible-node/admin rules before independent Canary authorization.

## 26. Final State

- `WORKFLOW_V1_INTERNAL_ENGINEERING_COMPLETE`
- `APPROVAL_ROLE_DIRECTORY_PROVIDER_READY`
- `V2.6.18 = CANONICAL_IMMUTABLE / EPHEMERAL_MYSQL8_VALIDATED`
- `EXPLICIT_USER_V1 = ACTIVE`
- `ROLE_DIRECTORY_V1 = PREPARED / NON_EXECUTABLE`
- `ROLE_RUNTIME = DISABLED`
- `ROLE_RUNTIME_CANARY_EXECUTION_ELIGIBLE = FALSE`
