package cn.gov.enterprise.modules.system.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotNull Long id,
        @NotNull Integer version,
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 50) String realName,
        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") String phone,
        @Email @Size(max = 100) String email,
        @NotNull Long orgId,
        @NotNull @Min(0) @Max(1) Integer status) {
}
