package cn.gov.enterprise.modules.system.role.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoleCreateRequest(
        @NotBlank @Size(max = 50) String roleName,
        @NotBlank @Size(max = 50) String roleCode,
        @Size(max = 200) String description,
        @NotBlank String dataScopeType,
        @NotNull @Min(0) @Max(1) Integer status) {
}
