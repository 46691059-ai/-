package cn.gov.enterprise.modules.investment.application.command;

import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceItem;
import java.time.LocalDate;

public record CreateDueDiligenceItemCommand(
        String itemNo, String category, DueDiligenceItem.Severity severity, boolean blocking,
        String problemDescription, String impactDescription, String rectificationMeasure,
        Long responsibleOrgId, Long responsiblePersonId, LocalDate deadline) {
}
