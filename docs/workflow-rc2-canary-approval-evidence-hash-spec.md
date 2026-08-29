# RC2 Canary Approval Evidence Hash Specification

## 1. Canonical contract

```text
HASH_ALGORITHM=SHA-256
ENCODING=UTF-8
LINE_ENDING=LF
CANONICALIZATION_VERSION=RC2_CANARY_GOVERNANCE_JSON_CANONICAL_V1
```

JSON canonical payloads are parsed and serialized as compact JSON with the field and array order defined by their versioned source artifact. No insignificant whitespace or terminal newline participates in the digest. String values are NFC-normalized by the pre-existing domain canonicalizers where applicable.

## 2. Acyclic Evidence DAG

```text
Fixture business facts
  -> Directory and Version Binding canonical inputs
  -> directoryResultHash + versionBindingHash
  -> Governance Release Manifest
  -> manifestHash
  -> Approval Content
  -> contentHash
  -> Hardened Release Commit
  -> annotated workflow-v1.0.0-rc2.1 tag
  -> post-tag Release Identity Attestation
```

Neither the Governance Release Manifest nor Approval Content includes a future release commit, tag object ID, runtime timestamp, machine path, credential, or environment identity. Thus no `releaseCommit -> contentHash -> releaseCommit` cycle exists.

## 3. Directory result

Source: `database/test-fixtures/rc2/rc2-canary-directory-result-v1.json`.

The source names the full six-dimensional approval scope. The digest is computed by the existing `ApprovalRoleCanonical.resultHash` implementation (`ROLE_DIRECTORY_RESULT_HASH_V1`). Members are sorted by `userId`, then assignment evidence by `assignmentId`. The Directory-bound digest covers the exact role/organization, revision, stable fixture assignments and frozen business effective window; the workflow dimensions are bound by the manifest and approval-content digests. The non-deterministic resolution time is deliberately excluded by the existing algorithm.

```text
directoryResultHash=2e1736fae83be972259ccee92b8d448d463e7c8a5483d49afcda7225a3279234
```

## 4. Version binding

Source: `database/test-fixtures/rc2/rc2-canary-version-binding-v1.json`.

The digest is computed by the existing `VersionNodeResolverBindingCanonical` length-prefixed `VERSION_NODE_RESOLVER_BINDING_V1` contract. It covers definition/version/node, binding order, resolver code/version/contract hash, strategy/mode/target, role, organization scope/ID, and effective-time policy.

```text
versionBindingHash=5b473845390a2a4c69f28a7b9d63d538a0a371cea447e97ecd00707ddbb87c1c
```

## 5. Governance release manifest

Source: `database/test-fixtures/rc2/rc2-canary-governance-release-manifest.json`.

The canonical compact JSON freezes the Base RC2 identity, V2.6.21-24 SHA values, V2.6.24 Flyway checksum, V2.6.23/24 structural attestations, approval schema, exact scope and canonical contract versions. It excludes the future hardened commit and tag.

```text
manifestHash=e76bc7f8ee3cca977d4363b6073106d66deffe2491d0290a569d46fa3bac57ed
```

## 6. Approval content

Source: `database/test-fixtures/rc2/rc2-canary-approval-content-v1.json`.

The canonical compact JSON represents the human-reviewable governance content: exact scope, Directory revision/count/hash, binding hash, manifest hash, V2.6.24 structural fingerprint, governance policy version, approval/enablement separation, ROLE Runtime separation and required Kill Switch state. Release tag and commit are intentionally excluded.

```text
contentHash=b2bc64b3c6cebbd1713b92074f5fdcf6862d94fb70338a6b2d07c76c61ef8ade
CONTENT_HASH_RELEASE_IDENTITY_CYCLE=NO
```

## 7. Release identity protocol

`rc2-canary-approval-evidence-v1.json` is valid pre-release business evidence with `hardenedRelease.status=AWAITING_RELEASE_IDENTITY`; that state is not a dummy hash. The future release commit contains business evidence but is not required to contain its own SHA. S11.4 must create and remotely verify the annotated tag, then create an external post-tag attestation binding tag, peeled commit, tag object/target and `contentHash`.

Until that attestation passes, no real `CanaryApprovalEvidence` may be created and human approval remains prohibited.
