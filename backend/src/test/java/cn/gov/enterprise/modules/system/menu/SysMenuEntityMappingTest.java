package cn.gov.enterprise.modules.system.menu;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.system.entity.SysMenuEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

class SysMenuEntityMappingTest {
    @Test
    void shouldMapStableMenuCodeToTheNewDatabaseColumn() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "sys-menu-mapping-test");
        var tableInfo = TableInfoHelper.initTableInfo(assistant, SysMenuEntity.class);
        Map<String, String> mappings = tableInfo.getFieldList().stream()
                .collect(Collectors.toMap(field -> field.getProperty(), field -> field.getColumn()));

        assertThat(tableInfo.getTableName()).isEqualTo("sys_menu");
        assertThat(mappings).containsEntry("menuCode", "menu_code");
    }
}
