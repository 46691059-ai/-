package cn.gov.enterprise.modules.investment.infrastructure.persistence.entity;

import cn.gov.enterprise.common.persistence.BaseIdEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @EqualsAndHashCode(callSuper = true)
@TableName("investment_due_diligence_package")
public class DueDiligencePackageEntity extends BaseIdEntity {
    private Long investmentId;
    private Integer packageVersion;
    private String ruleVersion;
    private String requiredTypes;
    private String overallConclusion;
    private Integer openBlockingCount;
    private String status;
    private LocalDateTime frozenTime;
    private String contentHash;
}
