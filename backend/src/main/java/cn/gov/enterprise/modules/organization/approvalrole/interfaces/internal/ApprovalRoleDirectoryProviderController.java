package cn.gov.enterprise.modules.organization.approvalrole.interfaces.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/approval-role-directory")
@ConditionalOnProperty(prefix="app.approval-role-directory.provider", name="enabled", havingValue="true")
public class ApprovalRoleDirectoryProviderController {
    private final ApprovalRoleDirectoryProviderFacade provider;
    public ApprovalRoleDirectoryProviderController(ApprovalRoleDirectoryProviderFacade provider){this.provider=provider;}
    @GetMapping("/health") public ApprovalRoleDirectoryProviderDtos.Health health(){return provider.health();}
    @GetMapping("/metadata") public ApprovalRoleDirectoryProviderDtos.Metadata metadata(){return provider.metadata();}
    @PostMapping("/canonical/verify") public ApprovalRoleDirectoryProviderDtos.CanonicalVerifyResult canonical(
            @RequestBody ApprovalRoleDirectoryProviderDtos.CanonicalVerifyRequest request){return provider.verifyCanonical(request.expectedHash());}
    @PostMapping("/resolve") public ApprovalRoleDirectoryProviderDtos.ResolveResult resolve(
            @RequestBody ApprovalRoleDirectoryProviderDtos.ResolveRequest request,
            @RequestHeader("X-Service-Identity") String callerServiceIdentity,
            @RequestHeader(value="X-Request-Id",required=false) String requestId){
        return provider.resolve(request,callerServiceIdentity,requestId);
    }
}
