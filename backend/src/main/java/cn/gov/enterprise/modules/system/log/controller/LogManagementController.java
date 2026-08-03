package cn.gov.enterprise.modules.system.log.controller;

import cn.gov.enterprise.common.api.ApiResponse;
import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.modules.system.log.dto.LogPageQuery;
import cn.gov.enterprise.modules.system.log.service.LogManagementService;
import cn.gov.enterprise.modules.system.log.vo.LogVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/system/log")
public class LogManagementController {
    private final LogManagementService service;

    public LogManagementController(LogManagementService service) { this.service = service; }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:log:query')")
    public ApiResponse<PageResponse<LogVO>> page(@Valid LogPageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:log:detail')")
    public ApiResponse<LogVO> detail(@PathVariable @Positive Long id) {
        return ApiResponse.success(service.detail(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:log:delete')")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id); return ApiResponse.success(null);
    }

    @DeleteMapping("/clean")
    @PreAuthorize("hasAuthority('system:log:delete')")
    public ApiResponse<Integer> clean(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before) {
        return ApiResponse.success(service.clean(before));
    }
}
