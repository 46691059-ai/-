package cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal;

import cn.gov.enterprise.modules.organization.approvalrole.domain.ApprovalRoleDirectoryFailure;
import cn.gov.enterprise.modules.organization.approvalrole.infrastructure.persistence.PersistentApprovalRoleDirectoryProviderAuditAdapter.AuditPersistenceException;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes=ApprovalRoleDirectoryProviderController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix="app.approval-role-directory.provider", name="enabled", havingValue="true")
public final class ApprovalRoleDirectoryProviderExceptionHandler {
    @ExceptionHandler(ApprovalRoleDirectoryFailure.class)
    ResponseEntity<Map<String,Object>> directory(ApprovalRoleDirectoryFailure failure){
        return ResponseEntity.unprocessableEntity().body(Map.of("code",failure.code().name(),"complete",false,"timestamp",Instant.now().toString()));
    }
    @ExceptionHandler({IllegalArgumentException.class})
    ResponseEntity<Map<String,Object>> invalid(RuntimeException failure){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code","INVALID_DIRECTORY_QUERY","complete",false,"timestamp",Instant.now().toString()));
    }
    @ExceptionHandler(AuditPersistenceException.class)
    ResponseEntity<Map<String,Object>> auditFailure(AuditPersistenceException failure){
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("code","PROVIDER_AUDIT_PERSISTENCE_FAILED","complete",false,"timestamp",Instant.now().toString()));
    }
}
