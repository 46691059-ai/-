# Governed Migration Directory

This directory is the only automatic Flyway scan root for newly governed migrations.

Rules:

1. Select exactly one vendor directory for each run.
2. Only versioned/baseline/repeatable Flyway assets belong in a vendor directory.
3. Never scan `database/mysql/manual`, `deprecated`, `init`, `rollback`, or legacy asset directories.
4. Applied migrations are immutable. Fixes require a higher version.
5. `clean` and automatic `repair` are prohibited.
6. Production migrations run through a dedicated deployment job, never during application startup.

Vendor paths:

- MySQL: `database/migration/mysql`
- Dameng: `database/migration/dm`
- KingbaseES: `database/migration/kingbase`

The vendor directories intentionally contain no executable migration in Sprint 2-1.8. Existing assets remain in their original locations until a target database has passed history and schema-fingerprint onboarding.
