# Workflow V1 ROLE Runtime Canary Monitoring & Acceptance Matrix

All thresholds and owners are `<TBD>` until approved. Missing telemetry or owner is NO-GO.

| Area | Metric / signal | Acceptance | Abort signal | Owner |
|---|---|---|---|---|
| Directory | availability | `<TBD>` | below `<TBD>` | `<TBD>` |
| Directory | P50 / P95 / P99 | `<TBD>` | any threshold breach | `<TBD>` |
| Directory | error rate | `<TBD>` | above `<TBD>` | `<TBD>` |
| Directory | revision mismatch | 0 unexpected | >0 | `<TBD>` |
| Directory | incomplete result | 0 | >0 | `<TBD>` |
| Directory | candidate count | within frozen bounds | 0/unexpected/excess | `<TBD>` |
| Eligibility | ELIGIBLE | reconciles with attempts | unexplained mismatch | `<TBD>` |
| Eligibility | INELIGIBLE | reason present | missing reason | `<TBD>` |
| Eligibility | INDETERMINATE | 0 | >0 | `<TBD>` |
| Eligibility | TTL expiry | expected only | premature/stale reuse | `<TBD>` |
| Eligibility | validator/capability failure | 0 unexpected | >0 | `<TBD>` |
| Claim | attempt / success / conflict | reconciled, one winner | duplicate winner | `<TBD>` |
| Claim | idempotent replay | same result | extra Claim/Audit | `<TBD>` |
| Claim | rollback | no partial writes | any residue | `<TBD>` |
| Claim | latency | `<TBD>` | P95/P99 breach | `<TBD>` |
| Audit | outbox pending / retry / dead | `<TBD>` | backlog/dead breach | `<TBD>` |
| Audit | receipt accepted/rejected | accepted=Claim success | reject/missing | `<TBD>` |
| Audit | receipt latency | `<TBD>` | threshold breach | `<TBD>` |
| Runtime | Task / Pool / Node / Instance | state-consistent | illegal combination | `<TBD>` |
| Controls | Flag / Canary / Kill Switch version | exact frozen versions | drift/missing | `<TBD>` |
| Controls | DataScope / Platform SoD / Business SoD version | exact frozen versions | drift/ambiguous | `<TBD>` |
| Security | 401 / 403 | explained probes only | unexpected increase | `<TBD>` |
| Security | TLS failure | 0 | >0 | `<TBD>` |
| Security | contract/canonical mismatch | 0 | >0 | `<TBD>` |

## Evidence retention

Capture query time, environment, change ID, metric window and dashboard/export reference. Store only PII-minimal IDs/hashes; never store service tokens, private keys or full directory member payloads.

