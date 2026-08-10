package cn.gov.enterprise.modules.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.entity.ProjectLifecycleStageTemplateEntity;
import cn.gov.enterprise.modules.project.infrastructure.persistence.LifecycleTemplateRepositoryImpl;
import cn.gov.enterprise.modules.project.infrastructure.persistence.LifecycleTemplateSelectionRow;
import cn.gov.enterprise.modules.project.mapper.ProjectLifecycleStageTemplateMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectLifecycleTemplateMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LifecycleTemplateRepositoryImplTest {
    @Mock ProjectLifecycleTemplateMapper templateMapper;
    @Mock ProjectLifecycleStageTemplateMapper stageMapper;

    @Test
    void shouldLoadAndValidateSingleActiveTemplate() {
        when(templateMapper.selectActiveCandidates("01", 10L))
                .thenReturn(List.of(selection(1)));
        when(stageMapper.selectList(any())).thenReturn(List.of(
                stage(201L, "OPPORTUNITY", 1, true),
                stage(202L, "ARCHIVE", 2, false)));
        var repository = new LifecycleTemplateRepositoryImpl(templateMapper, stageMapper);

        var template = repository.findActive("01", 10L).orElseThrow();

        assertThat(template.templateCode()).isEqualTo("INVESTMENT_STANDARD");
        assertThat(template.stages()).extracting(stage -> stage.stageCode())
                .containsExactly("OPPORTUNITY", "ARCHIVE");
    }

    @Test
    void shouldRejectAmbiguousTemplatesAtSamePriority() {
        when(templateMapper.selectActiveCandidates("01", 10L))
                .thenReturn(List.of(selection(1), selection(1)));
        var repository = new LifecycleTemplateRepositoryImpl(templateMapper, stageMapper);

        assertThatThrownBy(() -> repository.findActive("01", 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多个活动生命周期模板");
    }

    private LifecycleTemplateSelectionRow selection(int priority) {
        LifecycleTemplateSelectionRow row = new LifecycleTemplateSelectionRow();
        row.setTemplateId(100L);
        row.setTemplateVersionId(101L);
        row.setTemplateCode("INVESTMENT_STANDARD");
        row.setTemplateName("标准投资项目生命周期");
        row.setVersionNo(1);
        row.setVersionName("V1");
        row.setContentChecksum(
                "2fd698a140a9f1317b523c84e1ed59618542d25686af834328fb3b63843dedc7");
        row.setSelectionPriority(priority);
        return row;
    }

    private ProjectLifecycleStageTemplateEntity stage(
            Long id, String code, int order, boolean autoStart) {
        ProjectLifecycleStageTemplateEntity entity = new ProjectLifecycleStageTemplateEntity();
        entity.setId(id);
        entity.setStageCode(code);
        entity.setStageName(code);
        entity.setStageOrder(order);
        entity.setProgressWeight(new BigDecimal("50.0000"));
        entity.setRequiredFlag(1);
        entity.setAllowSkip(0);
        entity.setAutoStart(autoStart ? 1 : 0);
        entity.setApprovalRequired(0);
        entity.setCompletionMode("MANUAL");
        return entity;
    }
}
