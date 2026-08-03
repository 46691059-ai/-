package cn.gov.enterprise.modules.system.datascope.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.modules.system.datascope.dto.RoleDataScopeSaveRequest;
import cn.gov.enterprise.modules.system.datascope.service.DataScopeManagementService;
import cn.gov.enterprise.modules.system.datascope.vo.RoleDataScopeVO;
import cn.gov.enterprise.modules.system.org.vo.OrgTreeVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/system/datascope")
public class DataScopeController {
    private final DataScopeManagementService service;

    public DataScopeController(DataScopeManagementService service) {
        this.service = service;
    }

    @GetMapping("/role/{roleId}")
    @PreAuthorize("hasAnyAuthority('system:role:view', 'role:edit')")
    public ApiResponse<RoleDataScopeVO> roleDataScope(@PathVariable @Positive Long roleId) {
        return ApiResponse.success(service.roleDataScope(roleId));
    }

    @PutMapping("/role")
    @PreAuthorize("hasAuthority('role:edit')")
    public ApiResponse<Void> saveRoleDataScope(
            @Valid @RequestBody RoleDataScopeSaveRequest request) {
        service.saveRoleDataScope(request);
        return ApiResponse.success(null);
    }

    @GetMapping("/org/tree")
    @PreAuthorize("hasAuthority('role:edit')")
    public ApiResponse<List<OrgTreeVO>> organizationTree() {
        return ApiResponse.success(service.selectableOrganizationTree());
    }
}
