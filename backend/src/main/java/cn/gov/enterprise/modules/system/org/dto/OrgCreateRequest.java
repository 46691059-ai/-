package cn.gov.enterprise.modules.system.org.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrgCreateRequest(
        @NotBlank @Size(max = 50) String orgCode,
        @NotBlank @Size(max = 100) String orgName,
        @NotBlank String orgType,
        Long parentId,
        Long leaderId,
        @NotNull @Min(0) @Max(1) Integer status,
        @Min(0) Integer sortNo,
        @Size(max = 500) String remark) {
}
