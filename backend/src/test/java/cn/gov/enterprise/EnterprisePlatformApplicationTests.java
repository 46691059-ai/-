package cn.gov.enterprise;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** 验证基础配置、数据访问、安全和Web组件能够共同完成Spring上下文初始化。 */
@SpringBootTest(properties = {
        "app.security.jwt.secret=test-secret-must-have-at-least-thirty-two-bytes",
        "spring.datasource.url=jdbc:h2:mem:enterprise_platform;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.data.redis.repositories.enabled=false"
})
class EnterprisePlatformApplicationTests {

    @Test
    void contextLoads() {
    }
}
