# Workflow V1 RC2-S11.3R Canary Approval Evidence Freeze

## 1. Result and safety boundary

`RC2_S11_3R=PASS`. Four pre-release business evidence hashes are frozen, reproducible and machine-verified. The release identity self-reference is removed by separating Business Approval Evidence from the S11.4 post-tag Release Identity Attestation.

No production Java, migration SQL, runtime state, database, existing tag, commit or remote was changed. No governance row, Candidate Pool, Task, Claim or Admission was created. Approval remains pending; Canary and ROLE Runtime remain disabled; the Kill Switch remains `STOP_NEW_AND_CLAIM`.

## 2. Evidence Hash DAG

The versioned canonical contract is documented in `workflow-rc2-canary-approval-evidence-hash-spec.md`:

```text
Fixture facts
  -> Directory and Version Binding canonical inputs
  -> directoryResultHash + versionBindingHash
  -> Governance Release Manifest -> manifestHash
  -> Approval Content -> contentHash
  -> future Hardened Release Commit
  -> future annotated workflow-v1.0.0-rc2.1 tag
  -> future post-tag Release Identity Attestation
```

The four frozen values are:

| Evidence | Canonical source | SHA-256 |
| --- | --- | --- |
| Directory result | `rc2-canary-directory-result-v1.json` + `ApprovalRoleCanonical.resultHash` | `2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234` |
| Version binding | `rc2-canary-version-binding-v1.json` + `VersionNodeResolverBindingCanonical` | `5b473845390a2a4c69f28a7b9d63d538a0a371cea447e97ecd00707ddbb87c1c` |
| Governance manifest | compact `rc2-canary-governance-release-manifest.json` | `e76bc7f8ee3cca977d4363b6073106d66deffe2491d0290a569d46fa3bac57ed` |
| Approval content | compact `rc2-canary-approval-content-v1.json` | `b2bc64b3c6cebbd1713b92074f5fdcf6862d94fb70338a6b2d07c76c61ef8ade` |

Each was computed twice by the automated contract test and both results matched the evidence artifact.

## 3. Business semantics

The Directory digest reuses the existing `ROLE_DIRECTORY_RESULT_HASH_V1` algorithm and the two existing fixture members `990201/990202`. Stable business effective dates participate because they define membership validity; non-deterministic `resolvedAt` does not participate.

The Version Binding digest reuses `VERSION_NODE_RESOLVER_BINDING_V1` and covers definition/version/node, resolver code/version/contract, binding order, strategy, mode, target, role, fixed organization and effective-time policy.

The Governance Manifest freezes Base RC2, V2.6.21-24 SHAs, V2.6.24 Flyway checksum, V2.6.23/24 structural fingerprints, exact scope, evidence schema and contract versions. Approval Content freezes the scope, Directory evidence, binding/manifest hashes, structural fingerprint and separation policies.

Neither manifest nor approval content includes a future commit/tag, so `CONTENT_HASH_RELEASE_IDENTITY_CYCLE=NO`.

## 4. Pre-release evidence and release identity

`rc2-canary-approval-evidence-v1.json` contains all four business hashes and the V2.6.24 fingerprint. Its hardened release state is exactly `AWAITING_RELEASE_IDENTITY`; there is no fake commit or tag field.

The release commit may therefore contain this artifact without claiming to know its own SHA. S11.4 must create the commit and annotated tag, verify the remote identity and then produce an external post-tag attestation binding the tag/commit to `contentHash`. Until then, the existing production `CanaryApprovalEvidence` constructor rejects the pre-release artifact and no real approval action is permitted.

## 5. Validation

- `Rc2CanaryApprovalEvidenceContractTest`: 3/3 PASS
- Java 21 compile: PASS
- Backend full suite: 715 run, 705 passed, 0 failed, 0 errors, 10 skipped
- Spring Context: PASS
- Migration SHA: 46/46 PASS
- V2.6.24 SHA: `e549147abcf77168a1b1ef465c76ed642f7ce02f250117d0019bbeeb8d524822`
- V2.6.24 Flyway checksum: `857951823`
- V2.6.24 structural fingerprint: `20253809be2aeb7c76fe36a7d37293b6ca8b77741998aead726893056044a5b9`

## 6. Git classification

| File group | Classification |
| --- | --- |
| five JSON canonical/evidence artifacts under `database/test-fixtures/rc2` | `S11_3R_EVIDENCE` |
| `Rc2CanaryApprovalEvidenceContractTest.java` | `S11_3R_TEST` |
| hash spec, approval package, S11.3 and S11.3R reports | `S11_3R_DOC` |
| `initialize-rc1-test-environment.ps1`, `validate-rc1-fresh-database.ps1`, `workflow-v1-rc1-fresh-database-validation-design.md` | `PRE_EXISTING_LOCAL_ONLY`, excluded |

`UNKNOWN_FILE_COUNT=0`. The hardened release file set is ready for a separately authorized commit task; it must exclude the three pre-existing local-only files.
