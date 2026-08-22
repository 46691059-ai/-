package cn.gov.enterprise.modules.workflow.infrastructure.eligibility;

import cn.gov.enterprise.modules.governance.audit.domain.ExternalAuditEvent;
import cn.gov.enterprise.modules.governance.audit.domain.ExternalAuditMetadata;
import cn.gov.enterprise.modules.governance.audit.domain.ExternalAuditReceipt;

/** HTTPS-only anti-corruption boundary to an independent Audit/Governance context. */
public interface ExternalAuditSinkClient {
    ExternalAuditMetadata metadata();
    ExternalAuditReceipt deliver(ExternalAuditEvent event);
    final class DeliveryException extends RuntimeException {
        private final String code; private final boolean retryable;
        public DeliveryException(String code,boolean retryable,Throwable cause){super(code,cause);this.code=code;this.retryable=retryable;}
        public DeliveryException(String code,boolean retryable){this(code,retryable,null);}
        public String code(){return code;} public boolean retryable(){return retryable;}
    }
}
