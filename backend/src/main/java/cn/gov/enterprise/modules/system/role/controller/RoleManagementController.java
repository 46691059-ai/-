package cn.gov.enterprise.modules.system.role.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.system.role.dto.RoleCreateRequest;
import cn.gov.enterprise.modules.system.role.dto.RoleMenuAssignRequest;
import cn.gov.enterprise.modules.system.role.dto.RolePageQuery;
import cn.gov.enterprise.modules.system.role.dto.RoleUpdateRequest;
import cn.gov.enterprise.modules.system.role.service.RoleManagementService;
import cn.gov.enterprise.modules.system.role.vo.MenuTreeVO;
import cn.gov.enterprise.modules.system.role.vo.RolePermissionVO;
import cn.gov.enterprise.modules.system.role.vo.RoleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/system/role")
public class RoleManagementController {
    private final RoleManagementService service;

    public RoleManagementController(RoleManagementService service) { this.service = service; }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResponse<PageResponse<RoleVO>> page(@Valid RolePageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:view')")
    public ApiResponse<RoleVO> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:add')")
    public ApiResponse<Long> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('role:edit')")
    public ApiResponse<Void> update(@Valid @RequestBody RoleUpdateRequest request) {
        service.update(request); return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:delete')")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id); return ApiResponse.success(null);
    }

    @GetMapping("/menuTree")
    @PreAuthorize("hasAuthority('role:permission')")
    public ApiResponse<List<MenuTreeVO>> menuTree() { return ApiResponse.success(service.menuTree()); }

    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('role:permission')")
    public ApiResponse<RolePermissionVO> rolePermissions(@PathVariable @Positive Long id) {
        return ApiResponse.success(service.rolePermissions(id));
    }

    @PutMapping("/menu")
    @PreAuthorize("hasAuthority('role:permission')")
    public ApiResponse<Void> assignMenus(@Valid @RequestBody RoleMenuAssignRequest request) {
        service.assignMenus(request); return ApiResponse.success(null);
    }
}
