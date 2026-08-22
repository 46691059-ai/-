package cn.gov.enterprise.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class V2615ExecutionAdmissionPersistenceDdlDependencyTest {
    private static final Path MIGRATION_DIR=Path.of("..","database","migration","mysql");
    private static final Path V2615=MIGRATION_DIR.resolve(
            "V2.6.15__create_role_runtime_execution_admission_persistence.sql");
    private static final Pattern CREATE_TABLE=Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+([a-zA-Z0-9_]+)\\s*\\((.*?)\\)\\s*ENGINE=");
    private static final Pattern UNIQUE=Pattern.compile(
            "(?is)(?:PRIMARY\\s+KEY|UNIQUE\\s+KEY\\s+[a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)");
    private static final Pattern ALTER_TABLE=Pattern.compile(
            "(?is)ALTER\\s+TABLE\\s+([a-zA-Z0-9_]+)(.*?);");
    private static final Pattern ADDED_UNIQUE=Pattern.compile(
            "(?is)ADD\\s+UNIQUE\\s+KEY\\s+[a-zA-Z0-9_]+\\s*\\(([^)]*)\\)");
    private static final Pattern FOREIGN_KEY=Pattern.compile(
            "(?is)CONSTRAINT\\s+([a-zA-Z0-9_]+)\\s+FOREIGN\\s+KEY\\s*\\(([^)]*)\\)"
                    + "\\s*REFERENCES\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)");

    @Test void everyV2615ForeignKeyMustReferenceAnExactParentPrimaryOrUniqueKey() throws Exception {
        String chain=String.join("\n", List.of(
                read("V2.5.0__create_workflow_definition_domain.sql"),
                read("V2.5.5__implement_workflow_version_release.sql"),
                read("V2.6.14__create_role_runtime_binding_persistence_foundation.sql"),
                Files.readString(V2615)));
        Map<String,List<String>> parentKeys=parentKeys(chain);
        Matcher foreignKeys=FOREIGN_KEY.matcher(Files.readString(V2615));
        Map<String,String> contractMap=new HashMap<>();
        while(foreignKeys.find()) {
            String constraint=foreignKeys.group(1);
            String childColumns=normalize(foreignKeys.group(2));
            String parentTable=foreignKeys.group(3).toLowerCase(Locale.ROOT);
            String parentColumns=normalize(foreignKeys.group(4));
            contractMap.put(constraint, childColumns+" -> "+parentTable+"("+parentColumns+")");
            assertThat(parentKeys.getOrDefault(parentTable,List.of()))
                    .as("%s must reference an exact parent PK/UNIQUE: %s",constraint,contractMap.get(constraint))
                    .contains(parentColumns);
        }
        assertThat(contractMap).hasSize(11)
                .containsKey("fk_role_admission_slot_candidate")
                .containsKey("fk_role_admission_candidate")
                .containsKey("fk_role_admission_evidence_owner")
                .containsKey("fk_role_admission_event_owner");
    }

    @Test void slotCandidateForeignKeyMustUseTheDedicatedStableParentKey() throws Exception {
        String sql=Files.readString(V2615);
        assertThat(normalize(extractParentColumns(sql,"fk_role_admission_slot_candidate")))
                .isEqualTo("id,snapshot_id,delete_token");
        assertThat(sql).contains("UNIQUE KEY uk_role_binding_snapshot_slot_owner (id,snapshot_id,delete_token)");
    }

    private static Map<String,List<String>> parentKeys(String sql) {
        Map<String,List<String>> keys=new HashMap<>();
        Matcher tables=CREATE_TABLE.matcher(sql);
        while(tables.find()) {
            String table=tables.group(1).toLowerCase(Locale.ROOT);
            Matcher unique=UNIQUE.matcher(tables.group(2));
            while(unique.find()) keys.computeIfAbsent(table,ignored->new ArrayList<>()).add(normalize(unique.group(1)));
        }
        Matcher alters=ALTER_TABLE.matcher(sql);
        while(alters.find()) {
            Matcher added=ADDED_UNIQUE.matcher(alters.group(2));
            while(added.find()) keys.computeIfAbsent(alters.group(1).toLowerCase(Locale.ROOT),ignored->new ArrayList<>())
                    .add(normalize(added.group(1)));
        }
        return keys;
    }

    private static String extractParentColumns(String sql,String constraint) {
        Matcher matcher=FOREIGN_KEY.matcher(sql);
        while(matcher.find()) if(matcher.group(1).equals(constraint)) return matcher.group(4);
        throw new AssertionError("Missing FK contract: "+constraint);
    }

    private static String normalize(String columns) {
        return columns.replace("`","").replaceAll("\\s+","").toLowerCase(Locale.ROOT);
    }

    private static String read(String file) throws Exception { return Files.readString(MIGRATION_DIR.resolve(file)); }
}
