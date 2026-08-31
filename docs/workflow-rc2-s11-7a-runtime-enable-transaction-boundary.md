# Workflow V1 RC2-S11.7A Runtime Enable Transaction Boundary

## Authoritative state and target

The authoritative Canary state is the latest effective revision of the `CanaryGovernanceRecord` aggregate persisted in `workflow_role_canary_scope_governance`. The target is selected by all six scope keys; exactly one current aggregate is required. The pre-execution row must be revision `2`, state `APPROVED_NOT_ENABLED`, and must carry the frozen evidence and runtime release identity.

## Transaction and atomicity

`CanaryGovernanceApplicationService.transition` is transactional. It reads the exact current aggregate, verifies the expected revision, performs the domain transition, and appends one successor row. The append-only ledger is the state source; there is no separate mutable state write that may diverge from the event. The database trigger verifies predecessor state, revision, scope, evidence, and release identity before insert.

The unique `(scope, governance_revision, delete_token)` and `(previous_record_id, delete_token)` keys make the first valid append the only winner. A duplicate or concurrent insert is rejected and the transaction rolls back. No runtime object or traffic action may occur before the ledger transaction commits.

## Retry and idempotency

The stable operation key is SHA-256 of the exact scope, authorization commit, runtime tag, and runtime commit under `RC2_CANARY_ENABLE_OPERATION_KEY_V1`. Retries must reuse it. If the current revision is already greater than the expected revision or the predecessor has a successor, the request is a replay/stale request and must not append another `ENABLED` row. A retry may report the already-completed result only after verifying the existing successor has the same binding.

## Failure and partial-success semantics

Any missing target, non-unique target, stale revision, authorization drift, evidence drift, release drift, Kill Switch drift, invalid transition, or duplicate key fails closed. The ledger append rolls back as one transaction. Candidate resolution, traffic routing, task creation, claims, and observation cannot begin on a failed or uncommitted transaction.

## Concurrent execution

Concurrent requests use the same predecessor and expected revision. Database uniqueness permits at most one successor. The losing transaction receives a duplicate/concurrency error and must not retry with a new identity or widen scope. Canary enablement never writes ROLE Runtime governance.

## Observation entry point

Observation starts only after a committed, single `ENABLED` successor is re-read for the exact scope and verified against the operation binding. The start condition is `SUCCESSFUL_SINGLE_CANARY_ENABLE_EVENT`. The observation plan then checks event count, scope, runtime object count, candidate resolution, unexpected or duplicate claims, authorization denial, cross-scope access, application/database errors, latency anomalies, and Kill Switch condition.
