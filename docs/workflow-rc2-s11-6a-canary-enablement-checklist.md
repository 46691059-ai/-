# Workflow V1 RC2-S11.6A Canary Enablement Checklist

This checklist prepares a later explicit human enablement decision. It does not enable Canary and must remain unchecked in S11.6A.

- [ ] Runtime tag is `workflow-v1.0.0-rc2.1` and still peels to `c5946d272e8eb88115671d66b46e8c8ec67b1477`.
- [ ] Human approval record commit is `e986791bc9a2870916b851e03b07186ebac4449e` with state `APPROVED_NOT_ENABLED`.
- [ ] All five frozen evidence values match the approved artifact.
- [ ] The only blast radius is enterprise `990001`, organization `990101`, definition `990401`, definition version `990402`, node `990404`, role `RC1_TEST_CANARY_APPROVER`.
- [ ] Wildcards, null scope, parent inheritance, and cross-scope fallback remain prohibited.
- [ ] Directory revision is `1`, candidate count is `2`, and maximum recommended candidates is `1`.
- [ ] Current runtime object count is `0`; no Task, Claim, Candidate Pool, Admission, or routed traffic was created by preparation.
- [ ] Canary is still disabled and no `ENABLE` event exists.
- [ ] ROLE Runtime is still disabled and requires a separate explicit activation gate.
- [ ] Kill Switch is `STOP_NEW_AND_CLAIM` and will be revalidated at the enablement decision.
- [ ] Monitoring owners, queries, thresholds, and observation duration are explicitly accepted.
- [ ] Immediate rollback starts by retaining/restoring `STOP_NEW_AND_CLAIM`, then transitions `ENABLED → SUSPENDED` or to `REVOKED`.
- [ ] Rollback blocks new runtime/claim activity and preserves append-only evidence.

Future explicit decision — leave both unchecked during S11.6A:

- [ ] ENABLE EXACT CANARY SCOPE
- [ ] DO NOT ENABLE
