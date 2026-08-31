# Workflow V1 RC2-S11.7P-B Runtime Governance Bootstrap

## Purpose and boundary

The isolated `LOCAL_RC2_RUNTIME` database had the canonical V2.6.24 schema but no
authoritative six-dimensional governance aggregate. This gate adds only an audited,
transactional bootstrap path. It does not enable Canary, activate ROLE runtime, create
workflow tasks, execute claims, change migrations, or open traffic.

## Mechanism

`CanaryGovernanceApplicationService.bootstrapApproved` consumes an exact
`CanaryGovernanceBootstrapCommand`. The command validates the frozen Git identities and
uses the existing `CanaryApprovalEvidence`, `CanaryScope`, repository, mapper, audit
initializer, identity generator, state machine, and append-only database constraints.

The single transaction appends `PROPOSED` followed by `APPROVED_NOT_ENABLED`. Approval,
authorization, preparation, baseline, post-tag attestation, tag object, release identity,
and evidence hashes are bound to the immutable ledger and the reviewed JSON artifact.
No `ENABLED` transition exists in this bootstrap method.

## Safety properties

- Exact scope: `990001/990101/990401/990402/990404/RC1_TEST_CANARY_APPROVER`.
- Replay: the same frozen command returns `ALREADY_EXISTS`; drift fails closed.
- Concurrency: the V2.6.24 unique scope/revision constraints admit one root ledger.
- Atomicity: both append operations execute under one Spring transaction.
- Runtime controls remain `ROLE_RUNTIME=DISABLED` and
  `KILL_SWITCH=STOP_NEW_AND_CLAIM`.
- The runtime database is only `127.0.0.1:3306/enterprise_platform`; ports 3307 and
  34061 are outside this gate.

## Verification

The executable evidence is the guarded real-MySQL integration test
`Rc2RuntimeGovernanceBootstrapRealMysqlTest`, plus unit coverage for exact binding,
idempotency, concurrency, no-enable state, and cross-scope denial. Final runtime counts,
full regression totals, mapping results, migration SHA validation, and Git boundary are
reported by the S11.7P-B gate result.
