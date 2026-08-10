package cn.gov.enterprise;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@MapperScan({
        "cn.gov.enterprise.modules.project.mapper",
        "cn.gov.enterprise.modules.investment.infrastructure.persistence.mapper",
        "cn.gov.enterprise.modules.system.mapper",
        "cn.gov.enterprise.modules.system.user.mapper",
        "cn.gov.enterprise.modules.system.org.mapper",
        "cn.gov.enterprise.modules.system.role.mapper",
        "cn.gov.enterprise.modules.system.menu.mapper",
        "cn.gov.enterprise.modules.system.log.mapper",
        "cn.gov.enterprise.modules.system.datascope.mapper",
        "cn.gov.enterprise.common.datascope.mapper",
        "cn.gov.enterprise.security"
})
@ConfigurationPropertiesScan
@SpringBootApplication
public class EnterprisePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnterprisePlatformApplication.class, args);
    }
}
