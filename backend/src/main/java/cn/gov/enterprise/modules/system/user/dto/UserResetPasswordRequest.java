package cn.gov.enterprise.modules.system.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserResetPasswordRequest(
        @NotNull Long id,
        @NotBlank @Size(min = 8, max = 72) String newPassword,
        @NotNull Integer version) {
}
