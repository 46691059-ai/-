package cn.gov.enterprise.modules.system.org.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.system.org.dto.OrgCreateRequest;
import cn.gov.enterprise.modules.system.org.dto.OrgPageQuery;
import cn.gov.enterprise.modules.system.org.dto.OrgUpdateRequest;
import cn.gov.enterprise.modules.system.org.service.OrgManagementService;
import cn.gov.enterprise.modules.system.org.vo.LeaderOptionVO;
import cn.gov.enterprise.modules.system.org.vo.OrgTreeVO;
import cn.gov.enterprise.modules.system.org.vo.OrgVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/system/org")
public class OrgManagementController {
    private final OrgManagementService service;

    public OrgManagementController(OrgManagementService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:org:view')")
    public ApiResponse<List<OrgTreeVO>> tree() {
        return ApiResponse.success(service.tree());
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:org:view')")
    public ApiResponse<PageResponse<OrgVO>> page(@Valid OrgPageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:org:view')")
    public ApiResponse<OrgVO> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('org:add')")
    public ApiResponse<Long> create(@Valid @RequestBody OrgCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('org:edit')")
    public ApiResponse<Void> update(@Valid @RequestBody OrgUpdateRequest request) {
        service.update(request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('org:delete')")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/leaderOptions")
    @PreAuthorize("hasAnyAuthority('system:org:view', 'org:add', 'org:edit')")
    public ApiResponse<List<LeaderOptionVO>> leaderOptions() {
        return ApiResponse.success(service.leaderOptions());
    }
}
