package cn.gov.enterprise.modules.system.role.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RoleMenuAssignRequest(
        @NotNull Long roleId,
        @NotNull List<@NotNull Long> menuIds) {
}
