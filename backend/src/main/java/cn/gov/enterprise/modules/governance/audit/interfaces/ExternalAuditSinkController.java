package cn.gov.enterprise.modules.governance.audit.interfaces;

import cn.gov.enterprise.modules.governance.audit.application.ExternalAuditSinkService;
import cn.gov.enterprise.modules.governance.audit.domain.*;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/governance-audit-sink")
public class ExternalAuditSinkController {
    private final ExternalAuditSinkService service; private final ExternalAuditSinkProperties properties;
    public ExternalAuditSinkController(ExternalAuditSinkService service,ExternalAuditSinkProperties properties){this.service=service;this.properties=properties;}
    @GetMapping("/health") public Object health(){return java.util.Map.of("status","UP","environment",properties.environmentIdentity());}
    @GetMapping("/metadata") public ExternalAuditMetadata metadata(){return new ExternalAuditMetadata(properties.providerCode(),properties.providerVersion(),
            properties.serviceIdentity(),properties.environmentIdentity(),ExternalAuditEvent.CONTRACT_VERSION,
            ExternalAuditReceipt.CANONICAL_VERSION,Set.of("IDEMPOTENT_RECEIVE","RECEIPT","PAYLOAD_HASH","FAILURE_SIMULATION"));}
    @PostMapping("/events") public ResponseEntity<?> receive(@RequestBody ExternalAuditEvent event,
            @RequestHeader(value="X-Audit-Test-Failure",required=false) String failure)throws InterruptedException{
        if("TIMEOUT".equals(failure)){Thread.sleep(3500);}
        if("HTTP_503".equals(failure))return ResponseEntity.status(503).body(java.util.Map.of("error","TEMPORARY_UNAVAILABLE"));
        if("HTTP_500".equals(failure))return ResponseEntity.status(500).body(java.util.Map.of("error","INTERNAL_ERROR"));
        if("MALFORMED".equals(failure))return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{");
        if("WRONG_EVENT".equals(failure))return ResponseEntity.ok(java.util.Map.of("auditEventId","WRONG","externalReceiptId","R-X",
                "providerCode","GOVERNANCE_AUDIT_SINK","providerVersion","1.0.0","receivedAt",java.time.Instant.now(),
                "payloadHash",event.payloadHash(),"receiptStatus","ACCEPTED","receiptHash","a".repeat(64)));
        if("WRONG_PAYLOAD".equals(failure))return ResponseEntity.ok(java.util.Map.of("auditEventId",event.auditEventId(),"externalReceiptId","R-X",
                "providerCode","GOVERNANCE_AUDIT_SINK","providerVersion","1.0.0","receivedAt",java.time.Instant.now(),
                "payloadHash","b".repeat(64),"receiptStatus","ACCEPTED","receiptHash","a".repeat(64)));
        if("WRONG_RECEIPT_HASH".equals(failure))return ResponseEntity.ok(java.util.Map.of("auditEventId",event.auditEventId(),"externalReceiptId","R-X",
                "providerCode","GOVERNANCE_AUDIT_SINK","providerVersion","1.0.0","receivedAt",java.time.Instant.now(),
                "payloadHash",event.payloadHash(),"receiptStatus","ACCEPTED","receiptHash","a".repeat(64)));
        if("REJECTED".equals(failure))return ResponseEntity.unprocessableEntity().body(java.util.Map.of("error","AUDIT_EVENT_REJECTED"));
        try{return ResponseEntity.ok(service.receive(event));}
        catch(ExternalAuditSinkService.ExternalAuditRejection rejected){return ResponseEntity.unprocessableEntity().body(java.util.Map.of("error",rejected.getMessage()));}
    }
}
