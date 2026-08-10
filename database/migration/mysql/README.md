# MySQL Governed Migrations

Automatic Flyway location: `filesystem:/flyway/sql` after this directory is mounted read-only.

Sprint 2-1.10 promoted the byte-identical, MySQL 8 validated lifecycle chain:

- `V2.1.0` lifecycle V2 structure;
- `V2.1.1` project stage snapshot links;
- `V2.1.2` LEGACY lifecycle backfill;
- `V2.1.3` standard lifecycle templates.

Sprint 2-2 added and validated the next canonical asset:

- `V2.2.0` Investment module RBAC and menu bootstrap metadata.

Sprint 2-2.1 added and validated the Investment decision-loop schema baseline:

- `V2.4.0` Investment opportunity, feasibility version, due diligence, scheme, decision node and conditional approval structures.

Sprint 2-3.2 adds, and Sprint 2-3.2.1 validates on isolated MySQL 8.4.9, the
Investment decision integration increments:

- `V2.4.4` immutable decision snapshots and decision risk/audit references;
- `V2.4.5` Investment-to-Workflow bindings and decision-node references;
- `V2.4.6` condition risk/audit references and immutable business audit events.

Sprint 2-3.3 adds, and Sprint 2-3.3.1 validates on isolated MySQL 8.4.9:

- `V2.4.7` Investment Workflow Outbox/Inbox reliability state and decision RBAC metadata.

Sprint 2-3.4 adds, and Sprint 2-3.4.1 validates on isolated MySQL 8.4.9:

- `V2.4.8` canonical decision approval states plus approval, condition, and archive RBAC metadata.

Sprint 2-3.6 adds, and Sprint 2-3.6.1 validates on isolated MySQL 8.4.9:

- `V2.4.9` Investment-side Workflow worker leases, dead-letter and Inbox replay evidence,
  reliability audit records, and controlled replay RBAC metadata.

The V2.4.9 fresh-foundation and V2.4.8-upgrade paths passed pre-policy checks,
Flyway `migrate`, strict `validate`, and a second no-op `migrate`, with the
application Outbox Worker disabled throughout acceptance.

The governed migrations never create or query Workflow-owned tables.

`SHA256SUMS` is the release asset manifest. Every SQL file in this directory must be listed, and all listed hashes must pass before `migrate`. Promoted files are immutable; corrections require a higher migration version.

`V2.0.0__legacy_to_v1.sql` remains excluded because it is only valid for a specific legacy `pm_*` schema.
