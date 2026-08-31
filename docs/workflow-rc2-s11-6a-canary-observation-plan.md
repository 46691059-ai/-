# Workflow V1 RC2-S11.6A Canary Observation Plan

## Observation boundary

Observation is limited to the approved six-dimensional scope and at most one recommended candidate. S11.6A creates no runtime traffic. The observation window begins only after a future explicit enablement event and must never be inferred from approval or preparation timestamps.

## Required signals

| Signal | Required condition | Stop / rollback trigger |
| --- | --- | --- |
| Governance scope | Every decision matches all six dimensions | Any mismatch, null, wildcard, inheritance, or fallback |
| Runtime authorization | Current append-only state is exactly `ENABLED` | Missing, stale, duplicate-current, `SUSPENDED`, or `REVOKED` state |
| Kill Switch | Explicitly allows the requested operation | `STOP_NEW_AND_CLAIM`, missing, unknown, or inconsistent value |
| ROLE Runtime | Separately approved and enabled | Disabled, absent, expired, suspended, or revoked activation |
| Directory evidence | Revision `1`, two candidates, frozen result hash | Revision/hash/count drift or incomplete response |
| Candidate selection | No more than one recommended candidate | Multiple recommendations or out-of-scope candidate |
| Task/claim/admission | No unauthorized or cross-scope product | Any unexpected creation, claim, admission, or routing |
| Errors and latency | No material regression from the pre-enable baseline | Error spike, timeout, dependency failure, or safety-check failure |
| Audit evidence | Complete append-only event chain | Gap, mutation, deletion, duplicate revision, or hash mismatch |

## Observation procedure

Before enablement, capture the zero-runtime-object baseline and revalidate release identity, evidence hashes, scope, ROLE Runtime state, and Kill Switch. After any future enablement, observe continuously through the separately approved window and record only sanitized aggregate evidence. Do not store credentials, personal identifiers, raw directory payloads, local paths, or temporary database details.

## Suspend and rollback path

On any trigger, first retain or restore `STOP_NEW_AND_CLAIM`; separately disable ROLE Runtime if it was activated; append `SUSPENDED` for an investigable pause or `REVOKED` for terminal withdrawal; block new routing, task, candidate-pool, claim, and admission activity; preserve all historical ledger rows and evidence. Resumption from `SUSPENDED` requires another explicit gate. `REVOKED` cannot return to `ENABLED`.
