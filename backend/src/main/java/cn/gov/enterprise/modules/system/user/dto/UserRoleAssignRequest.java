package cn.gov.enterprise.modules.system.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserRoleAssignRequest(
        @NotNull Long userId,
        @NotNull List<@NotNull Long> roleIds) {
}
