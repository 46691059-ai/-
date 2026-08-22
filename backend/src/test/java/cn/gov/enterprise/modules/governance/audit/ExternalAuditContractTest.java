package cn.gov.enterprise.modules.governance.audit;

import static org.assertj.core.api.Assertions.*;
import cn.gov.enterprise.modules.governance.audit.domain.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExternalAuditContractTest {
    private static final String H="a".repeat(64);
    @Test void payloadAndReceiptCanonicalAreStableAndSensitive(){
        var event=event("E-1",H).withComputedHash();
        assertThat(event.verifyPayloadHash()).isTrue();
        assertThat(event("E-1",H).withComputedHash().payloadHash()).isEqualTo(event.payloadHash());
        assertThat(event("E-2",H).withComputedHash().payloadHash()).isNotEqualTo(event.payloadHash());
        assertThatThrownBy(()->new ExternalAuditEvent("E-1","ROLE_TASK_CLAIMED","1",event.occurredAt(),
                "1","2","3","4","5","6","7","A","8","R",H,H,H,H,"C","b".repeat(64)))
                .hasMessageContaining("payloadHash mismatch");
        var receipt=new ExternalAuditReceipt("E-1","R-1","GOVERNANCE_AUDIT_SINK","1.0.0",
                event.occurredAt(),event.payloadHash(),ExternalAuditReceipt.Status.ACCEPTED,null).withComputedHash();
        assertThat(receipt.verify()).isTrue();
        assertThatThrownBy(()->new ExternalAuditReceipt("E-1","R-1","GOVERNANCE_AUDIT_SINK","1.0.0",
                event.occurredAt(),event.payloadHash(),ExternalAuditReceipt.Status.ACCEPTED,"c".repeat(64)))
                .hasMessageContaining("receiptHash mismatch");
    }
    @Test void eventContractContainsNoCredentialOrHRSensitiveFields(){
        assertThat(ExternalAuditEvent.class.getRecordComponents()).extracting(c->c.getName().toLowerCase())
                .noneMatch(n->n.contains("token")||n.contains("secret")||n.contains("password")
                        ||n.contains("phone")||n.contains("salary")||n.contains("identitycard")||n.contains("address"));
    }
    private ExternalAuditEvent event(String id,String hash){return new ExternalAuditEvent(id,"ROLE_TASK_CLAIMED","1",
            Instant.parse("2026-08-21T01:02:03Z"),"1","2","3","4","5","6","7","A","8","R",
            hash,hash,hash,hash,"C",null);}
}
