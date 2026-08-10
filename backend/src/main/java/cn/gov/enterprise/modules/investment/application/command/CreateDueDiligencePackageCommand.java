package cn.gov.enterprise.modules.investment.application.command;

import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceType;
import java.util.Set;

public record CreateDueDiligencePackageCommand(
        String ruleVersion, Set<DueDiligenceType> requiredTypes) {
}
