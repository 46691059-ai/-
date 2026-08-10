package cn.gov.enterprise.modules.investment.infrastructure.persistence;

import cn.gov.enterprise.modules.investment.domain.repository.InvestmentIdentityGenerator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Component;

/** MyBatis Plus snowflake identity adapter for Investment aggregates. */
@Component
public class MybatisInvestmentIdentityGenerator implements InvestmentIdentityGenerator {
    @Override
    public Long nextId() {
        return IdWorker.getId();
    }
}
