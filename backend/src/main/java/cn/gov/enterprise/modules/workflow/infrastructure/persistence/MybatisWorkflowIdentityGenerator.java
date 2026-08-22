package cn.gov.enterprise.modules.workflow.infrastructure.persistence;

import cn.gov.enterprise.modules.workflow.domain.repository.WorkflowIdentityGenerator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Component;

@Component
public class MybatisWorkflowIdentityGenerator implements WorkflowIdentityGenerator {
    @Override
    public Long nextId() {
        return IdWorker.getId();
    }
}
