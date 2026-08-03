package cn.gov.enterprise.modules.system.menu.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.system.menu.dto.MenuCreateRequest;
import cn.gov.enterprise.modules.system.menu.dto.MenuPageQuery;
import cn.gov.enterprise.modules.system.menu.dto.MenuUpdateRequest;
import cn.gov.enterprise.modules.system.menu.service.MenuManagementService;
import cn.gov.enterprise.modules.system.menu.vo.MenuVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/system/menu")
public class MenuManagementController {
    private final MenuManagementService service;

    public MenuManagementController(MenuManagementService service) { this.service = service; }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:view')")
    public ApiResponse<List<MenuVO>> tree() { return ApiResponse.success(service.tree()); }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:menu:view')")
    public ApiResponse<PageResponse<MenuVO>> page(@Valid MenuPageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('menu:add')")
    public ApiResponse<Long> create(@Valid @RequestBody MenuCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('menu:edit')")
    public ApiResponse<Void> update(@Valid @RequestBody MenuUpdateRequest request) {
        service.update(request); return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:delete')")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id); return ApiResponse.success(null);
    }
}
