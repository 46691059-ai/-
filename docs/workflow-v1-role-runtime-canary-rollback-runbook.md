# Workflow V1 ROLE Runtime Canary Kill Switch / Rollback Runbook

Status: `RUNBOOK_READY / NOT_EXECUTED`.

## Governing principles

`STOP NEW` · `PRESERVE HISTORY` · `FAIL CLOSED` · `MANUAL REVIEW`.

Kill Switch first blocks new ROLE runtime objects and new ROLE claims. It must never delete or rewrite historical Task, Claim, Evidence, Audit, Receipt, Candidate Pool, Binding or Directory Revision.

## Trigger conditions

- Directory SLA breach, timeout, incomplete result or unavailable provider.
- Revision mismatch or abnormal historical effectiveAt response.
- DataScope or either SoD result is INDETERMINATE/DENY unexpectedly.
- Audit Sink unavailable, receipt mismatch, retry/dead backlog threshold or permanent audit-loss risk.
- Eligibility TTL anomaly, duplicate Claim, deadlock threshold or rollback anomaly.
- Configuration drift, unexpected candidate count, security incident or PII incident.

## Procedure

1. Declare incident and record change/canary/correlation IDs; do not log credentials or candidate detail.
2. Set the approved Kill Switch to `STOP_NEW_AND_CLAIM`; verify the new version is visible at commit gates.
3. Set Canary OFF and Feature Flag OFF through controlled configuration; do not modify historical controls.
4. Confirm no new ROLE object or Claim commits while USER/DIRECT remain unaffected.
5. Preserve and reconcile Task, Pool, Claim, Evidence, outbox and receipt counts.
6. Stop audit worker only when directed by the incident owner; retain pending/retry/dead messages.
7. Classify in-flight items for manual review; never force-complete or delete them.
8. Capture metrics, logs, hashes, versions and owner decisions; rotate credentials only through secret management.
9. Restore service only through a new GO/NO-GO review; never auto-reenable.

## Acceptance after abort

- Kill Switch version proves safe state; Flag/Canary are OFF.
- No historical mutation or loss; outbox/receipt reconciliation is complete.
- All anomalies have owners and tickets; Runtime remains disabled until separate authorization.

