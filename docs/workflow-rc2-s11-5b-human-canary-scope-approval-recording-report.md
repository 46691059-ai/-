# Workflow V1 RC2-S11.5B Human Canary Scope Approval Recording Gate Report

## Recorded decision

- Decision maker: `EXPLICIT HUMAN DECISION`
- Human decision: `APPROVE`
- Decision source: `EXPLICIT_HUMAN_DECISION`
- Previous state: `PENDING_HUMAN_APPROVAL`
- Resulting state: `APPROVED_NOT_ENABLED`
- Pre-decision baseline: `e96b498deb97c7c2a0d32b9165bbe1edef99b1e0`

No personal name, account, email, workstation identity, or job title was supplied. The record therefore uses the normalized non-PII actor reference `HUMAN / EXPLICIT_INTERACTIVE_APPROVER`. The UTC decision time was captured when S11.5B was executed and was not backfilled.

## Exact approved subject

- Runtime release: `workflow-v1.0.0-rc2.1` / `c5946d272e8eb88115671d66b46e8c8ec67b1477`
- Annotated tag object: `269595532f17cc3db09880404ca11629d108fc6d`
- Post-tag attestation commit: `d627c38af00eb6be5f2a8fda572679c62c4937c3`
- Scope: enterprise `990001`, organization `990101`, definition `990401`, definition version `990402`, node `990404`, role `RC1_TEST_CANARY_APPROVER`
- Directory: revision `1`, candidates `2`, recommended candidates `1`

## Frozen evidence

- Directory result: `2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234`
- Version binding: `5b473845390a2a4c69f28a7b9d63d538a0a371cea447e97ecd00707ddbb87c1c`
- Manifest: `e76bc7f8ee3cca977d4363b6073106d66deffe2491d0290a569d46fa3bac57ed`
- Approval content: `b2bc64b3c6cebbd1713b92074f5fdcf6862d94fb70338a6b2d07c76c61ef8ade`
- Structural fingerprint: `20253809be2aeb7c76fe36a7d37293b6ca8b77741998aead726893056044a5b9`

## Approval and runtime separation

The versioned artifact records the approval event with append-only semantics. It records no Canary enablement event and no ROLE Runtime event. Authorization means only that the exact scope may proceed to a future, independent Canary Enablement Gate.

- `CANARY_AUTHORIZED=YES`
- `CANARY_ENABLED=NO`
- `ROLE_RUNTIME_ENABLED=NO`
- `ROLE_RUNTIME=DISABLED`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`
- `POST_APPROVAL_PRE_ENABLE_RUNTIME_FAIL_CLOSED=PASS`

No production approval service, task, claim, routing, activation, or database ledger was invoked. The approval record is the versioned governance evidence artifact permitted by S11.5B.

## Verification record

- Java 21 compile: `PASS`
- Full backend regression: `719 tests / 0 failures / 0 errors / 10 skipped / PASS`
- Spring context: `PASS`
- Migration SHA validation: `46/46 PASS`
- Database mapping contracts: `PASS`
- Approval recording, evidence, state machine and runtime gate: `12/12 PASS`
- Secret / PII scan: `0 real findings / PASS`
- Production code changes: `NONE`
- Migration SQL changes: `NONE`
