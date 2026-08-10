package cn.gov.enterprise.modules.investment.infrastructure.persistence.row;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class DecisionMaterialsRow {
    private Long schemeVersionId; private String schemeStatus; private String schemeHash;
    private Long feasibilityVersionId; private String feasibilityStatus; private String feasibilityConclusion; private String feasibilityHash;
    private Long dueDiligencePackageId; private String dueDiligenceStatus; private String dueDiligenceConclusion;
    private Integer openBlockingCount; private String dueDiligenceHash; private Long enterpriseId;
}
