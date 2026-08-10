package cn.gov.enterprise.modules.investment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.investment.domain.model.DueDiligenceItem;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.DueDiligenceRepositoryImpl;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.entity.DueDiligenceItemEntity;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.DueDiligenceItemMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.DueDiligencePackageMapper;
import cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper.DueDiligenceReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DueDiligenceRepositoryAdapterTest {
    @Mock DueDiligencePackageMapper packageMapper;
    @Mock DueDiligenceReportMapper reportMapper;
    @Mock DueDiligenceItemMapper itemMapper;

    @Test
    void blockingHighRiskItemShouldUpdateReportAndPackageCounters() {
        DueDiligenceRepositoryImpl repository = new DueDiligenceRepositoryImpl(
                packageMapper, reportMapper, itemMapper);
        DueDiligenceItem item = new DueDiligenceItem(
                700L, 600L, 100L, "RISK-001", "财务",
                DueDiligenceItem.Severity.HIGH, true, "现金流缺口", null,
                "补充融资方案", 20L, 40L, null, DueDiligenceItem.Status.OPEN, 0);
        when(itemMapper.insert(any(DueDiligenceItemEntity.class))).thenReturn(1);
        when(reportMapper.incrementRiskCounts(600L, 1)).thenReturn(1);
        when(packageMapper.incrementBlockingByReport(600L)).thenReturn(1);

        repository.saveItem(item);

        verify(reportMapper).incrementRiskCounts(600L, 1);
        verify(packageMapper).incrementBlockingByReport(600L);
        assertThat(item.blocking()).isTrue();
    }
}
