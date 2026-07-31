package cn.gov.enterprise;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@MapperScan({"cn.gov.enterprise.modules", "cn.gov.enterprise.security"})
@ConfigurationPropertiesScan
@SpringBootApplication
public class EnterprisePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnterprisePlatformApplication.class, args);
    }
}
