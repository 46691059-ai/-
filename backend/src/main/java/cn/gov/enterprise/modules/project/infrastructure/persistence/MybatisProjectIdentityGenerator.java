package cn.gov.enterprise.modules.project.infrastructure.persistence;

import cn.gov.enterprise.modules.project.domain.repository.ProjectIdentityGenerator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Component;

/** MyBatis Plus snowflake identity adapter. */
@Component
public class MybatisProjectIdentityGenerator implements ProjectIdentityGenerator {
    @Override
    public Long nextId() {
        return IdWorker.getId();
    }
}
