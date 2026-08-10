package cn.gov.enterprise.modules.investment;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.investment.domain.repository.InvestmentOpportunityRepository;
import cn.gov.enterprise.modules.investment.domain.repository.InvestmentProjectRepository;
import cn.gov.enterprise.modules.investment.application.service.InvestmentOpportunityApplicationService;
import cn.gov.enterprise.modules.investment.application.service.InvestmentProjectApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
        "app.security.jwt.secret=test-secret-must-have-at-least-thirty-two-bytes",
        "spring.datasource.url=jdbc:h2:mem:investment_bootstrap;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.data.redis.repositories.enabled=false"
})
class InvestmentModuleContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsWithFirstPersistenceAdaptersAndApplicationServices() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeansOfType(InvestmentProjectRepository.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(InvestmentOpportunityRepository.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(InvestmentProjectApplicationService.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(InvestmentOpportunityApplicationService.class)).hasSize(1);
    }
}
