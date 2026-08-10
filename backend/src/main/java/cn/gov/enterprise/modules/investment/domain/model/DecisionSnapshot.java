package cn.gov.enterprise.modules.investment.domain.model;

import java.time.LocalDateTime;

/** Immutable references used by one workflow submission. */
public record DecisionSnapshot(
        Long id, Long decisionId, int snapshotVersion,
        Long schemeVersionId, String schemeHash,
        Long feasibilityVersionId, String feasibilityHash,
        Long dueDiligencePackageId, String dueDiligenceHash,
        String routeCode, String routeRuleVersion, String routeHash,
        Long frozenBy, LocalDateTime frozenTime, String snapshotHash) {
}
