package cn.gov.enterprise.modules.system.log.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public record LogPageQuery(
        String username,
        String logType,
        String moduleName,
        String status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
        @Positive Long page,
        @Positive Long size) {

    public long currentPage() { return page == null ? 1 : page; }
    public long pageSize() { return size == null ? 20 : Math.min(size, 100); }
}
