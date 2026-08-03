package cn.gov.enterprise.modules.system.menu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MenuUpdateRequest(
        @NotNull @Positive Long id,
        @NotNull @Min(0) Integer version,
        Long parentId,
        @NotBlank @Size(max = 100) String menuName,
        @NotBlank @Size(max = 20) String menuType,
        @Size(max = 200) String path,
        @Size(max = 200) String component,
        @Size(max = 200) String permission,
        @Size(max = 100) String icon,
        @NotNull @Min(0) Integer sort,
        @NotNull @Min(0) @Max(1) Integer status) {
}
