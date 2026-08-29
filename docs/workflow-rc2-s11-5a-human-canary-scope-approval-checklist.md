# Workflow V1 RC2-S11.5A Human Canary Scope Approval Checklist

This checklist prepares, but does not make, the human Canary-scope decision. The reviewer must independently verify every item before selecting exactly one decision.

- [ ] Release tag is exactly `workflow-v1.0.0-rc2.1`.
- [ ] Runtime release commit is exactly `c5946d272e8eb88115671d66b46e8c8ec67b1477`.
- [ ] Annotated tag object is `269595532f17cc3db09880404ca11629d108fc6d`, and the tag still peels to the runtime release commit.
- [ ] Post-tag attestation commit `d627c38af00eb6be5f2a8fda572679c62c4937c3` exists and is distinct from the runtime release commit.
- [ ] Scope contains all six exact dimensions: enterprise `990001`, organization `990101`, definition `990401`, definition version `990402`, node `990404`, role `RC1_TEST_CANARY_APPROVER`.
- [ ] Directory result hash is `2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234`.
- [ ] Version binding hash is `5b473845390a2a4c69f28a7b9d63d538a0a371cea447e97ecd00707ddbb87c1c`.
- [ ] Manifest hash is `e76bc7f8ee3cca977d4363b6073106d66deffe2491d0290a569d46fa3bac57ed`.
- [ ] Content hash is `b2bc64b3c6cebbd1713b92074f5fdcf6862d94fb70338a6b2d07c76c61ef8ade`.
- [ ] Structural fingerprint is `20253809be2aeb7c76fe36a7d37293b6ca8b77741998aead726893056044a5b9`.
- [ ] Directory revision is `1` and candidate count is `2`.
- [ ] Recommended candidate count is `1`.
- [ ] Approval is not enablement; approval can produce only `APPROVED_NOT_ENABLED`.
- [ ] ROLE Runtime enablement remains a separate governance decision.
- [ ] Kill Switch remains `STOP_NEW_AND_CLAIM` and cannot be changed by this approval.

Human decision — select exactly one only after completing the checks above:

- [ ] APPROVE
- [ ] REJECT
