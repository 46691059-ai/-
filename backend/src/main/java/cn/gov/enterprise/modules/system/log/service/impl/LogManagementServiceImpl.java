package cn.gov.enterprise.modules.system.log.service.impl;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.system.entity.SysLogEntity;
import cn.gov.enterprise.modules.system.mapper.SysLogMapper;
import cn.gov.enterprise.modules.system.log.dto.LogPageQuery;
import cn.gov.enterprise.modules.system.log.mapper.LogManagementMapper;
import cn.gov.enterprise.modules.system.log.service.LogManagementService;
import cn.gov.enterprise.modules.system.log.support.StoredAuditContext;
import cn.gov.enterprise.modules.system.log.vo.LogVO;
import cn.gov.enterprise.security.CurrentSecurityContext;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LogManagementServiceImpl implements LogManagementService {
    private final SysLogMapper logMapper;
    private final LogManagementMapper managementMapper;
    private final CurrentSecurityContext securityContext;
    private final ObjectMapper objectMapper;

    public LogManagementServiceImpl(
            SysLogMapper logMapper,
            LogManagementMapper managementMapper,
            CurrentSecurityContext securityContext,
            ObjectMapper objectMapper) {
        this.logMapper = logMapper; this.managementMapper = managementMapper;
        this.securityContext = securityContext; this.objectMapper = objectMapper;
    }

    @Override
    public PageResponse<LogVO> page(LogPageQuery query) {
        validateRange(query.startTime(), query.endTime());
        Page<LogManagementMapper.LogRow> result = managementMapper.selectLogPage(
                new Page<>(query.currentPage(), query.pageSize()), query);
        return new PageResponse<>(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public LogVO detail(Long id) {
        LogManagementMapper.LogRow row = managementMapper.selectLogDetail(id);
        if (row == null) throw new BusinessException("B0404", "日志不存在");
        return toVO(row);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysLogEntity entity = logMapper.selectById(id);
        if (entity == null) throw new BusinessException("B0404", "日志不存在");
        entity.setDeleteToken(id);
        entity.setUpdateBy(securityContext.username());
        if (logMapper.updateById(entity) != 1 || logMapper.deleteById(id) != 1) {
            throw new BusinessException("B0409", "日志删除失败，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public int clean(LocalDateTime before) {
        if (before == null || !before.isBefore(LocalDateTime.now())) {
            throw new BusinessException("B0409", "清理截止时间必须早于当前时间");
        }
        return managementMapper.logicallyDeleteBefore(before, securityContext.username());
    }

    private LogVO toVO(LogManagementMapper.LogRow row) {
        String[] operation = row.operation() == null ? new String[0] : row.operation().split("\\|", 3);
        StoredAuditContext context = parseContext(row.remark());
        String username = StringUtils.hasText(row.username()) ? row.username() : context.username();
        return new LogVO(row.id(), row.userId(), username,
                operation.length > 0 ? operation[0] : "OPERATION",
                operation.length > 1 ? operation[1] : "UNKNOWN",
                operation.length > 2 ? operation[2] : row.operation(),
                row.requestUrl(), row.requestMethod(), context.requestParams(), context.responseResult(),
                row.ip(), row.result(), row.errorMessage(), row.durationMs(), row.traceId(), row.createTime());
    }

    private StoredAuditContext parseContext(String remark) {
        if (!StringUtils.hasText(remark)) return new StoredAuditContext(null, null, null);
        try {
            return objectMapper.readValue(remark, StoredAuditContext.class);
        } catch (Exception ignored) {
            return new StoredAuditContext(null, null, null);
        }
    }

    private void validateRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException("B0409", "开始时间不能晚于结束时间");
        }
    }
}
