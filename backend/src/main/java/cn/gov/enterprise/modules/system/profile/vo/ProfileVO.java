package cn.gov.enterprise.modules.system.profile.vo;

import java.util.List;

public record ProfileVO(
        Long id,
        String username,
        String realName,
        String phone,
        Long orgId,
        String orgName,
        List<String> roleCodes,
        List<String> roleNames) {
}
