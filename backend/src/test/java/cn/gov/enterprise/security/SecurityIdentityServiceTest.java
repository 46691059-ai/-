package cn.gov.enterprise.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;

class SecurityIdentityServiceTest {

    @Test
    void orgAndChildrenScopeIncludesCurrentOrgAndEveryDescendant() {
        SecurityIdentityMapper mapper = mock(SecurityIdentityMapper.class);
        SecurityIdentityMapper.SecurityUserRow user = new SecurityIdentityMapper.SecurityUserRow(
                50001L, "dept-manager", 103L, 1, 0, null);
        when(mapper.selectUser(50001L)).thenReturn(user);
        when(mapper.selectPermissions(50001L)).thenReturn(List.of("project:lifecycle:list"));
        when(mapper.selectDataScopeTypes(50001L)).thenReturn(List.of("ORG_AND_CHILDREN"));
        when(mapper.selectOrgAndChildren(103L)).thenReturn(List.of(103L, 10301L, 10302L));

        PermissionCacheService cache = passThroughCache();
        SecuritySnapshot snapshot = new SecurityIdentityService(mapper, cache).load(50001L);

        assertThat(snapshot.allDataScope()).isFalse();
        assertThat(snapshot.selfOnly()).isFalse();
        assertThat(snapshot.allowedOrgIds()).containsExactlyInAnyOrder(103L, 10301L, 10302L);
    }

    @Test
    void rejectsUserWhoseLockHasNotExpired() {
        SecurityIdentityMapper mapper = mock(SecurityIdentityMapper.class);
        SecurityIdentityMapper.SecurityUserRow user = new SecurityIdentityMapper.SecurityUserRow(
                10001L, "locked-user", 10L, 1, 0, LocalDateTime.now().plusMinutes(10));
        when(mapper.selectUser(10001L)).thenReturn(user);

        SecurityIdentityService service = new SecurityIdentityService(mapper, passThroughCache());

        assertThatThrownBy(() -> service.load(10001L))
                .isInstanceOf(LockedException.class)
                .hasMessage("用户已临时锁定");
        verify(mapper).selectUser(10001L);
        verifyNoMoreInteractions(mapper);
    }

    private PermissionCacheService passThroughCache() {
        PermissionCacheService cache = mock(PermissionCacheService.class);
        when(cache.identity(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> ((Supplier<SecuritySnapshot>) invocation.getArgument(1)).get());
        return cache;
    }
}
