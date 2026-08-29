# Workflow V1 RC2-S11.4 Post-Tag Release Attestation Gate Report

## Decision

- `RC2_S11_4=PASS`
- `POST_TAG_ATTESTATION_STATUS=RELEASE_IDENTITY_BOUND`
- `APPROVAL_DECISION=PENDING_HUMAN_APPROVAL`
- `CANARY_AUTHORIZED=NO`
- `CANARY_ENABLED=NO`
- `ROLE_RUNTIME_ENABLED=NO`
- `KILL_SWITCH=STOP_NEW_AND_CLAIM`

S11.4 binds already-published release identity to already-frozen business evidence. It creates no governance state, approval, runtime object, commit, tag, or push.

## Release identity verification

| Field | Verified value |
| --- | --- |
| Base tag / commit | `workflow-v1.0.0-rc2` / `740bee63e79a2744eb06ff693b17e7ed3fdf9375` |
| Hardened tag | `workflow-v1.0.0-rc2.1` |
| Hardened commit / remote branch head | `c5946d272e8eb88115671d66b46e8c8ec67b1477` |
| Tag object / remote tag object | `269595532f17cc3db09880404ca11629d108fc6d` |
| Peeled local / remote tag target | `c5946d272e8eb88115671d66b46e8c8ec67b1477` |
| Tag type | `ANNOTATED` |
| Verification source | live `git ls-remote` against `origin` |

## Frozen evidence and self-reference boundary

| Evidence | Recomputed value |
| --- | --- |
| directoryResultHash | `2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234` |
| versionBindingHash | `5b473845390a2a4c69f28a7b9d63d538a0a371cea447e97ecd00707ddbb87c1c` |
| manifestHash | `e76bc7f8ee3cca977d4363b6073106d66deffe2491d0290a569d46fa3bac57ed` |
| contentHash | `b2bc64b3c6cebbd1713b92074f5fdcf6862d94fb70338a6b2d07c76c61ef8ade` |
| structuralFingerprint | `20253809be2aeb7c76fe36a7d37293b6ca8b77741998aead726893056044a5b9` |

The canonical release manifest and approval-content source were not modified. Release tag, commit, tag object, remote verification, migration identity, business hashes, and exact scope are held in the independent post-tag attestation. Therefore release identity cannot recursively change `manifestHash` or `contentHash`.

## Contract and runtime result

The evidence contract recomputes all four business hashes, checks the structural fingerprint, verifies the one-to-one release/business/scope binding, and instantiates `CanaryApprovalEvidence` with the final release tag and commit. It does not call propose, approve, enable, or any runtime operation.

The complete regression suite remains the runtime authority: an attestation alone does not satisfy an enabled six-dimensional Canary state, separate ROLE Runtime enablement, Feature Flag/safety checks, or Kill Switch permission. Runtime therefore remains fail closed.

## Verification record

- Java 21 compile: `PASS`
- Java 21 full backend test suite: `716 tests / 0 failures / 0 errors / 10 skipped / PASS`
- Spring context: `PASS`
- Migration SHA validation: `46/46 PASS`
- V2.6.24 SHA-256: `e549147abcf77168a1b1ef465c76ed642f7ce02f250117d0019bbeeb8d524822`
- Mapping / fixture contract: `PASS`
- Secret / PII scan of S11.4 assets: `PASS`
- Main Java changes: `NONE`
- Migration changes: `NONE`

## Separation of authority

The runtime release identity is `workflow-v1.0.0-rc2.1` at `c5946d272e8eb88115671d66b46e8c8ec67b1477`. Any later commit containing only this post-tag attestation, tests, approval-package update, and report is documentary evidence and is not a replacement runtime release identity.

The package is complete for human Canary-scope review. Human approval is still required and, if granted, must remain distinct from Canary enablement and ROLE Runtime enablement.
