package cn.gov.enterprise.modules.system.log.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.system.entity.SysLogEntity;
import cn.gov.enterprise.modules.system.log.service.AuditLogCommand;
import cn.gov.enterprise.modules.system.log.support.SensitiveDataSanitizer;
import cn.gov.enterprise.modules.system.mapper.SysLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {
    @Mock SysLogMapper mapper;

    @Test
    void persistsCompatibleAndSanitizedAuditRecord() {
        ObjectMapper objectMapper = new ObjectMapper();
        SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer(objectMapper);
        when(mapper.insert(any(SysLogEntity.class))).thenReturn(1);
        AuditLogServiceImpl service = new AuditLogServiceImpl(mapper, objectMapper, sanitizer);

        service.save(new AuditLogCommand(1L, "admin", "OPERATION", "SYSTEM_USER", "新增用户",
                "/system/user", "POST", "{\"password\":\"******\"}", "code=200",
                "127.0.0.1", "SUCCESS", null, 12L, "trace-1"));

        ArgumentCaptor<SysLogEntity> captor = ArgumentCaptor.forClass(SysLogEntity.class);
        verify(mapper).insert(captor.capture());
        SysLogEntity entity = captor.getValue();
        assertThat(entity.getOperation()).isEqualTo("OPERATION|SYSTEM_USER|新增用户");
        assertThat(entity.getTraceId()).isEqualTo("trace-1");
        assertThat(entity.getRemark()).contains("admin", "******").doesNotContain("Secret123");
    }
}
