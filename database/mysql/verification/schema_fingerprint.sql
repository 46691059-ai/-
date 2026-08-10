-- Read-only canonical schema stream for MySQL 8.
-- Run with: mysql --batch --raw --skip-column-names --database=<schema>
-- Hash the UTF-8 output, including the final newline, with SHA-256.

SET SESSION TRANSACTION READ ONLY;
START TRANSACTION WITH CONSISTENT SNAPSHOT;

SELECT canonical_line
FROM (
    SELECT CONCAT(
        'TABLE|', t.table_name, '|', t.table_type, '|', COALESCE(t.engine, ''), '|',
        COALESCE(t.table_collation, ''), '|',
        REPLACE(REPLACE(REPLACE(COALESCE(t.table_comment, ''), '|', '\\|'), CHAR(13), ' '), CHAR(10), ' ')
    ) AS canonical_line,
    CONCAT('01|', t.table_name) AS sort_key
    FROM information_schema.tables t
    WHERE t.table_schema = DATABASE()
      AND t.table_name NOT IN ('flyway_schema_history', 'database_release_audit')

    UNION ALL

    SELECT CONCAT(
        'COLUMN|', c.table_name, '|', LPAD(c.ordinal_position, 5, '0'), '|', c.column_name, '|',
        LOWER(c.column_type), '|', c.is_nullable, '|', COALESCE(c.column_default, '<NULL>'), '|',
        LOWER(COALESCE(c.extra, '')), '|', COALESCE(c.character_set_name, ''), '|',
        COALESCE(c.collation_name, ''), '|',
        REPLACE(REPLACE(REPLACE(COALESCE(c.column_comment, ''), '|', '\\|'), CHAR(13), ' '), CHAR(10), ' ')
    ) AS canonical_line,
    CONCAT('02|', c.table_name, '|', LPAD(c.ordinal_position, 5, '0')) AS sort_key
    FROM information_schema.columns c
    WHERE c.table_schema = DATABASE()
      AND c.table_name NOT IN ('flyway_schema_history', 'database_release_audit')

    UNION ALL

    SELECT CONCAT(
        'INDEX|', s.table_name, '|', s.index_name, '|', s.non_unique, '|',
        LPAD(s.seq_in_index, 5, '0'), '|', COALESCE(s.column_name, '<EXPRESSION>'), '|',
        COALESCE(s.sub_part, ''), '|', COALESCE(s.collation, ''), '|', s.index_type
    ) AS canonical_line,
    CONCAT('03|', s.table_name, '|', s.index_name, '|', LPAD(s.seq_in_index, 5, '0')) AS sort_key
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name NOT IN ('flyway_schema_history', 'database_release_audit')

    UNION ALL

    SELECT CONCAT(
        'FK|', k.table_name, '|', k.constraint_name, '|', LPAD(k.ordinal_position, 5, '0'), '|',
        k.column_name, '|', k.referenced_table_name, '|', k.referenced_column_name, '|',
        rc.update_rule, '|', rc.delete_rule
    ) AS canonical_line,
    CONCAT('04|', k.table_name, '|', k.constraint_name, '|', LPAD(k.ordinal_position, 5, '0')) AS sort_key
    FROM information_schema.key_column_usage k
    JOIN information_schema.referential_constraints rc
      ON rc.constraint_schema = k.constraint_schema
     AND rc.table_name = k.table_name
     AND rc.constraint_name = k.constraint_name
    WHERE k.table_schema = DATABASE()
      AND k.referenced_table_name IS NOT NULL
      AND k.table_name NOT IN ('flyway_schema_history', 'database_release_audit')

    UNION ALL

    SELECT CONCAT(
        'CHECK|', tc.table_name, '|', tc.constraint_name, '|',
        REPLACE(REPLACE(REPLACE(cc.check_clause, '|', '\\|'), CHAR(13), ' '), CHAR(10), ' ')
    ) AS canonical_line,
    CONCAT('05|', tc.table_name, '|', tc.constraint_name) AS sort_key
    FROM information_schema.table_constraints tc
    JOIN information_schema.check_constraints cc
      ON cc.constraint_schema = tc.constraint_schema
     AND cc.constraint_name = tc.constraint_name
    WHERE tc.table_schema = DATABASE()
      AND tc.constraint_type = 'CHECK'
      AND tc.table_name NOT IN ('flyway_schema_history', 'database_release_audit')

    UNION ALL

    SELECT CONCAT(
        'VIEW|', v.table_name, '|',
        REPLACE(REPLACE(REPLACE(v.view_definition, '|', '\\|'), CHAR(13), ' '), CHAR(10), ' ')
    ) AS canonical_line,
    CONCAT('06|', v.table_name) AS sort_key
    FROM information_schema.views v
    WHERE v.table_schema = DATABASE()
) fingerprint_rows
ORDER BY sort_key;

COMMIT;
