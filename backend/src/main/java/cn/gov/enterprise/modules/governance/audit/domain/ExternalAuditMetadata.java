package cn.gov.enterprise.modules.governance.audit.domain;

import java.util.Set;

public record ExternalAuditMetadata(String providerCode,String providerVersion,String serviceIdentity,
        String environmentIdentity,String contractVersion,String receiptContractVersion,
        Set<String> supportedCapabilities) { }
