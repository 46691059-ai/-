package cn.gov.enterprise.modules.system.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
        @NotNull Long id,
        @NotNull @Min(0) @Max(1) Integer status,
        @NotNull Integer version) {
}
