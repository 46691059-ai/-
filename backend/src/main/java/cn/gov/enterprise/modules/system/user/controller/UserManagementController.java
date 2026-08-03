package cn.gov.enterprise.modules.system.user.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.system.user.dto.*;
import cn.gov.enterprise.modules.system.user.service.UserManagementService;
import cn.gov.enterprise.modules.system.user.vo.RoleOptionVO;
import cn.gov.enterprise.modules.system.user.vo.OrgOptionVO;
import cn.gov.enterprise.modules.system.user.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/system/user")
public class UserManagementController {
    private final UserManagementService service;

    public UserManagementController(UserManagementService service) {
        this.service = service;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user:view')")
    public ApiResponse<PageResponse<UserVO>> page(@Valid UserPageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:view')")
    public ApiResponse<UserVO> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:add')")
    public ApiResponse<Long> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('user:edit')")
    public ApiResponse<Void> update(@Valid @RequestBody UserUpdateRequest request) {
        service.update(request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/status")
    @PreAuthorize("hasAuthority('user:edit')")
    public ApiResponse<Void> changeStatus(@Valid @RequestBody UserStatusRequest request) {
        service.changeStatus(request);
        return ApiResponse.success(null);
    }

    @PutMapping("/resetPassword")
    @PreAuthorize("hasAuthority('user:resetPassword')")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody UserResetPasswordRequest request) {
        service.resetPassword(request);
        return ApiResponse.success(null);
    }

    @PutMapping("/roles")
    @PreAuthorize("hasAuthority('user:edit')")
    public ApiResponse<Void> assignRoles(@Valid @RequestBody UserRoleAssignRequest request) {
        service.assignRoles(request);
        return ApiResponse.success(null);
    }

    @PutMapping("/role")
    @PreAuthorize("hasAuthority('user:edit')")
    public ApiResponse<Void> assignRole(@Valid @RequestBody UserRoleAssignRequest request) {
        service.assignRoles(request);
        return ApiResponse.success(null);
    }

    @GetMapping("/roleOptions")
    @PreAuthorize("hasAnyAuthority('system:user:view', 'user:edit')")
    public ApiResponse<List<RoleOptionVO>> roleOptions() {
        return ApiResponse.success(service.roleOptions());
    }

    @GetMapping("/orgOptions")
    @PreAuthorize("hasAnyAuthority('system:user:view', 'user:add', 'user:edit')")
    public ApiResponse<List<OrgOptionVO>> orgOptions() {
        return ApiResponse.success(service.orgOptions());
    }
}
