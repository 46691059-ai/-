package cn.gov.enterprise.modules.system.datascope.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 保存角色数据范围的请求。 */
public record RoleDataScopeSaveRequest(
        @NotNull @Positive Long roleId,
        @NotBlank @Size(max = 32) String dataScope,
        @NotNull @Size(max = 1000) List<@NotNull @Positive Long> orgIds) {
}
