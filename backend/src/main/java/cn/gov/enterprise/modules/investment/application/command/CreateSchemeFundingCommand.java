package cn.gov.enterprise.modules.investment.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSchemeFundingCommand(
        String fundingType,
        String providerName,
        BigDecimal amount,
        BigDecimal costRate,
        LocalDate availableDate,
        boolean confirmed,
        Long evidenceFileId) {
}
