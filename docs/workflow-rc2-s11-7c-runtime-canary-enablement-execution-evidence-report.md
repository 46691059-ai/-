# Workflow V1 RC2-S11.7C Runtime Canary Enablement Evidence

## Attested execution

The execution baseline is commit `111297fe0174525549e11ba66e5005667f15890e`.
The isolated runtime database is `127.0.0.1:3306/enterprise_platform`; neither the
unrelated 3307 instance nor the protected RC1 instance was accessed.

The exact scope is `990001/990101/990401/990402/990404/RC1_TEST_CANARY_APPROVER`.
Its append-only sequence is `PROPOSED -> APPROVED_NOT_ENABLED -> ENABLED`. Before
execution the current revision was 2 with zero ENABLE events. The single execution
created revision 3 at `2026-08-31T08:22:53.611Z`; the current state is `ENABLED` and
the exact ENABLE count is one.

## Frozen lineage

- Bootstrap: `111297fe0174525549e11ba66e5005667f15890e`
- Preparation: `3fb1ec7236274aa3c44c6a907477c5ae3851c109`
- Authorization: `3904efd7cd2a35736f46988b48170a7cd2813654`
- Human approval: `e986791bc9a2870916b851e03b07186ebac4449e`
- Release: `workflow-v1.0.0-rc2.1` at
  `c5946d272e8eb88115671d66b46e8c8ec67b1477`, annotated tag object
  `269595532f17cc3db09880404ca11629d108fc6d`

The persisted revision retains the frozen directory result, version binding, manifest,
content, and V2.6.24 structural fingerprint hashes recorded in the execution evidence
artifact.

## Safety and observation baseline

ROLE Runtime remains `DISABLED`; no activation request, approval, or evidence event
exists. The Kill Switch remains `STOP_NEW_AND_CLAIM`. No traffic was released, no
business task was created, and no claim was executed by the gate. Flyway still contains
one baseline plus 46 successful SQL migrations, no failures, with V2.6.24 present.

The observation entrypoint is ready. The baseline is `PARTIAL`: current governance,
ROLE Runtime, Kill Switch, and scope counts are queryable, while business telemetry is
not asserted or fabricated.
