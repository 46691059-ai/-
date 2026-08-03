package cn.gov.enterprise.common.datascope.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.common.datascope.service.DataScopeTestService;
import cn.gov.enterprise.common.datascope.vo.DataScopeTestVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 开发环境数据权限验收接口；生产环境默认不注册。 */
@RestController
@RequestMapping("/test/dataScope")
@ConditionalOnProperty(name = "app.datascope.test-endpoint-enabled", havingValue = "true")
public class DataScopeTestController {
    private final DataScopeTestService service;

    public DataScopeTestController(DataScopeTestService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DataScopeTestVO> query() {
        return ApiResponse.success(service.query());
    }
}
