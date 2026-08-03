package cn.gov.enterprise.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthRouteServiceTest {
    @Mock SecurityIdentityMapper identityMapper;
    @Mock PermissionCacheService permissionCacheService;

    @Test
    void returnsAuthorizedMenuTreeAndFiltersButtons() {
        List<SecurityIdentityMapper.SecurityMenuRow> rows = List.of(
                row(400L, null, "项目管理", "M", "/projects", "project:view"),
                row(410L, 400L, "项目列表", "C", "/projects", "project:lifecycle:list"),
                row(411L, 410L, "项目新增", "B", null, "project:add"));
        when(permissionCacheService.menus(any(), any())).thenAnswer(invocation ->
                ((Supplier<List<SecurityIdentityMapper.SecurityMenuRow>>) invocation.getArgument(1)).get());
        when(identityMapper.selectMenus(90001L)).thenReturn(rows);

        var routes = new AuthRouteService(identityMapper, permissionCacheService).routes(90001L);

        assertThat(routes).hasSize(1);
        assertThat(routes.getFirst().menuName()).isEqualTo("项目管理");
        assertThat(routes.getFirst().children()).extracting(LoginResponse.MenuItem::menuName)
                .containsExactly("项目列表");
        assertThat(routes.toString()).doesNotContain("项目新增");
    }

    private SecurityIdentityMapper.SecurityMenuRow row(
            Long id, Long parentId, String name, String type, String path, String permission) {
        return new SecurityIdentityMapper.SecurityMenuRow(
                id, parentId, name, type, path, null, permission, null, 1, "B".equals(type) ? 0 : 1);
    }
}
