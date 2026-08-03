package cn.gov.enterprise.modules.system.log.service;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.system.log.dto.LogPageQuery;
import cn.gov.enterprise.modules.system.log.vo.LogVO;
import java.time.LocalDateTime;

public interface LogManagementService {
    PageResponse<LogVO> page(LogPageQuery query);
    LogVO detail(Long id);
    void delete(Long id);
    int clean(LocalDateTime before);
}
