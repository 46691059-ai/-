package cn.gov.enterprise.config;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 启动期实体与数据库字段映射审计。
 *
 * <p>该检查只输出报告，不执行DDL，也不会自动修改实体或数据库。</p>
 */
@Component
public class DatabaseMappingChecker implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseMappingChecker.class);
    private static final String ENTITY_PACKAGE = "cn.gov.enterprise.modules";

    private final DataSource dataSource;
    private final boolean enabled;

    public DatabaseMappingChecker(
            DataSource dataSource,
            @Value("${app.database.mapping-check.enabled:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Database entity mapping check is disabled");
            return;
        }

        List<Class<?>> entityTypes = scanEntityTypes();
        int matched = 0;
        int inconsistent = 0;
        int missingTables = 0;

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (Class<?> entityType : entityTypes) {
                TableName table = entityType.getAnnotation(TableName.class);
                Set<String> entityColumns = resolveEntityColumns(entityType);
                Set<String> databaseColumns = resolveDatabaseColumns(connection, metadata, table.value());

                if (databaseColumns.isEmpty()) {
                    missingTables++;
                    log.warn("Database mapping report: entity={}, table={}, result=TABLE_NOT_FOUND",
                            entityType.getName(), table.value());
                    continue;
                }

                Set<String> entityOnly = difference(entityColumns, databaseColumns);
                Set<String> databaseOnly = difference(databaseColumns, entityColumns);
                if (entityOnly.isEmpty() && databaseOnly.isEmpty()) {
                    matched++;
                } else {
                    inconsistent++;
                    log.error(
                            "Database mapping report: entity={}, table={}, entityOnly={}, databaseOnly={}",
                            entityType.getName(), table.value(), entityOnly, databaseOnly);
                }
            }
            log.info(
                    "Database mapping check completed: checked={}, matched={}, inconsistent={}, missingTables={}",
                    entityTypes.size(), matched, inconsistent, missingTables);
        } catch (SQLException exception) {
            log.error("Database mapping check failed: sqlState={}, errorCode={}, type={}",
                    exception.getSQLState(), exception.getErrorCode(), exception.getClass().getSimpleName());
        }
    }

    private List<Class<?>> scanEntityTypes() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));

        List<Class<?>> entities = new ArrayList<>();
        scanner.findCandidateComponents(ENTITY_PACKAGE).forEach(definition -> {
            try {
                entities.add(ClassUtils.forName(
                        definition.getBeanClassName(), DatabaseMappingChecker.class.getClassLoader()));
            } catch (ClassNotFoundException exception) {
                log.error("Unable to load entity for database mapping check: {}", definition.getBeanClassName());
            }
        });
        entities.sort(Comparator.comparing(Class::getName));
        return entities;
    }

    static Set<String> resolveEntityColumns(Class<?> entityType) {
        Set<String> columns = new LinkedHashSet<>();
        Class<?> currentType = entityType;
        while (currentType != null && currentType != Object.class) {
            for (Field field : currentType.getDeclaredFields()) {
                if (field.isSynthetic()
                        || Modifier.isStatic(field.getModifiers())
                        || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && !tableField.exist()) {
                    continue;
                }
                TableId tableId = field.getAnnotation(TableId.class);
                String explicitName = tableId != null && StringUtils.hasText(tableId.value())
                        ? tableId.value()
                        : tableField != null && StringUtils.hasText(tableField.value()) ? tableField.value() : null;
                columns.add(normalizeColumn(explicitName == null ? camelToUnderline(field.getName()) : explicitName));
            }
            currentType = currentType.getSuperclass();
        }
        return columns;
    }

    private Set<String> resolveDatabaseColumns(
            Connection connection, DatabaseMetaData metadata, String tableName) throws SQLException {
        String schema = resolveSchema(connection);
        String catalog = connection.getCatalog();
        List<String[]> namespaces = List.of(
                new String[] {catalog, schema},
                new String[] {catalog, null},
                new String[] {null, schema});
        List<String> tableCandidates = List.of(
                tableName, tableName.toUpperCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT));
        for (String[] namespace : namespaces) {
            for (String candidate : tableCandidates) {
                Set<String> columns = readColumns(metadata, namespace[0], namespace[1], candidate);
                if (!columns.isEmpty()) {
                    return columns;
                }
            }
        }
        return Set.of();
    }

    private String resolveSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException | AbstractMethodError exception) {
            log.debug("Database driver does not expose current schema: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private Set<String> readColumns(
            DatabaseMetaData metadata, String catalog, String schema, String tableName) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getColumns(catalog, schema, tableName, null)) {
            while (resultSet.next()) {
                if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    columns.add(normalizeColumn(resultSet.getString("COLUMN_NAME")));
                }
            }
        }
        return columns;
    }

    private static Set<String> difference(Set<String> source, Set<String> target) {
        Set<String> result = new LinkedHashSet<>(source);
        result.removeAll(target);
        return result;
    }

    private static String normalizeColumn(String column) {
        return column.toLowerCase(Locale.ROOT);
    }

    private static String camelToUnderline(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isUpperCase(current)) {
                result.append('_').append(Character.toLowerCase(current));
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }
}
