package cn.gov.enterprise.modules.system.role.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record RolePageQuery(
        String roleName,
        String roleCode,
        @Min(0) @Max(1) Integer status,
        @Positive Long page,
        @Positive Long size) {
    public long currentPage() { return page == null ? 1 : page; }
    public long pageSize() { return size == null ? 20 : Math.min(size, 100); }
}
