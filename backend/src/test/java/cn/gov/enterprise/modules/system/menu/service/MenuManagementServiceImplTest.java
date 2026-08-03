package cn.gov.enterprise.modules.system.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.entity.SysMenuEntity;
import cn.gov.enterprise.modules.system.entity.SysPermissionEntity;
import cn.gov.enterprise.modules.system.mapper.SysMenuMapper;
import cn.gov.enterprise.modules.system.mapper.SysPermissionMapper;
import cn.gov.enterprise.modules.system.menu.dto.MenuCreateRequest;
import cn.gov.enterprise.modules.system.menu.dto.MenuUpdateRequest;
import cn.gov.enterprise.modules.system.menu.mapper.MenuManagementMapper;
import cn.gov.enterprise.modules.system.menu.service.impl.MenuManagementServiceImpl;
import cn.gov.enterprise.security.CurrentSecurityContext;
import cn.gov.enterprise.security.PermissionCacheService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuManagementServiceImplTest {
    @Mock SysMenuMapper menuMapper;
    @Mock MenuManagementMapper managementMapper;
    @Mock SysPermissionMapper permissionMapper;
    @Mock CurrentSecurityContext securityContext;
    @Mock PermissionCacheService permissionCacheService;
    private MenuManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "menu-management-test");
        TableInfoHelper.initTableInfo(assistant, SysMenuEntity.class);
        TableInfoHelper.initTableInfo(assistant, SysPermissionEntity.class);
        service = new MenuManagementServiceImpl(menuMapper, managementMapper, permissionMapper,
                securityContext, permissionCacheService);
    }

    @Test
    void createsButtonUnderPageAndForcesItInvisible() {
        SysMenuEntity parent = menu(940L, "C", "system:menu:view");
        when(menuMapper.selectById(940L)).thenReturn(parent);
        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(permissionMapper.selectCount(any())).thenReturn(1L);

        service.create(new MenuCreateRequest(940L, "菜单新增", "b", "/ignored", "ignored",
                "menu:add", null, 1, 1));

        ArgumentCaptor<SysMenuEntity> captor = ArgumentCaptor.forClass(SysMenuEntity.class);
        verify(menuMapper).insert(captor.capture());
        assertThat(captor.getValue().getMenuType()).isEqualTo("B");
        assertThat(captor.getValue().getVisible()).isZero();
        assertThat(captor.getValue().getPath()).isNull();
        assertThat(captor.getValue().getComponent()).isNull();
    }

    @Test
    void deleteRejectsNodeWithChildren() {
        when(menuMapper.selectById(900L)).thenReturn(menu(900L, "M", "system:view"));
        when(menuMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(900L))
                .isInstanceOf(BusinessException.class).hasMessage("存在子菜单，禁止删除");
        verify(managementMapper, never()).countRoleBindings(any());
    }

    @Test
    void updateRejectsPermissionChangeWhenMenuIsRoleBound() {
        SysMenuEntity current = menu(940L, "C", "system:menu:view");
        current.setVersion(0);
        when(menuMapper.selectById(940L)).thenReturn(current);
        when(menuMapper.selectById(900L)).thenReturn(menu(900L, "M", "system:view"));
        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(permissionMapper.selectCount(any())).thenReturn(1L);
        when(managementMapper.countRoleBindings(940L)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(new MenuUpdateRequest(
                940L, 0, 900L, "菜单管理", "C", "/system/menu", "system/menu/index",
                "system:menu:changed", "Menu", 4, 1)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能修改权限标识");
    }

    private SysMenuEntity menu(Long id, String type, String permission) {
        SysMenuEntity menu = new SysMenuEntity();
        menu.setId(id); menu.setMenuName(permission); menu.setMenuType(type);
        menu.setPermission(permission); menu.setStatus(1); menu.setSortNo(1); menu.setVisible(1);
        return menu;
    }
}
