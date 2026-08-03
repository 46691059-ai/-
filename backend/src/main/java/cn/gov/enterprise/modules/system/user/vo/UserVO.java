package cn.gov.enterprise.modules.system.user.vo;

import java.time.LocalDateTime;
import java.util.List;

public record UserVO(
        Long id,
        String username,
        String realName,
        String phone,
        String email,
        Long orgId,
        String orgName,
        Integer status,
        List<Long> roleIds,
        List<String> roleNames,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        Integer version) {
}
