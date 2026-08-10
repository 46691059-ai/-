package cn.gov.enterprise.modules.project.infrastructure.persistence;

import cn.gov.enterprise.common.exception.BusinessException;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleTemplateDefinition;
import cn.gov.enterprise.modules.project.domain.model.lifecycle.LifecycleTemplateStageDefinition;
import cn.gov.enterprise.modules.project.domain.repository.LifecycleTemplateRepository;
import cn.gov.enterprise.modules.project.entity.ProjectLifecycleStageTemplateEntity;
import cn.gov.enterprise.modules.project.mapper.ProjectLifecycleStageTemplateMapper;
import cn.gov.enterprise.modules.project.mapper.ProjectLifecycleTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Repository;

/** MyBatis adapter that applies the deterministic active-template selection priority. */
@Repository
public class LifecycleTemplateRepositoryImpl implements LifecycleTemplateRepository {
    private final ProjectLifecycleTemplateMapper templateMapper;
    private final ProjectLifecycleStageTemplateMapper stageTemplateMapper;

    public LifecycleTemplateRepositoryImpl(
            ProjectLifecycleTemplateMapper templateMapper,
            ProjectLifecycleStageTemplateMapper stageTemplateMapper) {
        this.templateMapper = templateMapper;
        this.stageTemplateMapper = stageTemplateMapper;
    }

    @Override
    public Optional<LifecycleTemplateDefinition> findActive(String projectType, Long orgId) {
        List<LifecycleTemplateSelectionRow> candidates =
                templateMapper.selectActiveCandidates(projectType, orgId);
        if (candidates.isEmpty()) return Optional.empty();
        LifecycleTemplateSelectionRow selected = candidates.getFirst();
        if (candidates.size() > 1
                && selected.getSelectionPriority().equals(
                        candidates.get(1).getSelectionPriority())) {
            throw new BusinessException("B0500", "同一优先级存在多个活动生命周期模板");
        }
        List<LifecycleTemplateStageDefinition> stages = stageTemplateMapper.selectList(
                        new LambdaQueryWrapper<ProjectLifecycleStageTemplateEntity>()
                                .eq(ProjectLifecycleStageTemplateEntity::getTemplateVersionId,
                                        selected.getTemplateVersionId())
                                .orderByAsc(ProjectLifecycleStageTemplateEntity::getStageOrder))
                .stream().map(this::toDefinition).toList();
        if (!selected.getContentChecksum().equals(templateChecksum(stages))) {
            throw new BusinessException("B0500", "活动生命周期模板内容校验失败");
        }
        try {
            return Optional.of(new LifecycleTemplateDefinition(
                    selected.getTemplateId(), selected.getTemplateVersionId(),
                    selected.getTemplateCode(), selected.getTemplateName(),
                    selected.getVersionNo(), selected.getVersionName(),
                    selected.getContentChecksum(), stages));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("B0500", "活动生命周期模板配置不完整");
        }
    }

    private LifecycleTemplateStageDefinition toDefinition(
            ProjectLifecycleStageTemplateEntity entity) {
        return new LifecycleTemplateStageDefinition(
                entity.getId(), entity.getStageCode(), entity.getStageName(),
                entity.getStageOrder(), entity.getProgressWeight(),
                valueOrFalse(entity.getRequiredFlag()), valueOrFalse(entity.getAllowSkip()),
                entity.getPlannedDurationDays(), valueOrFalse(entity.getAutoStart()),
                valueOrFalse(entity.getApprovalRequired()), entity.getApprovalSceneCode(),
                entity.getCompletionMode());
    }

    private static boolean valueOrFalse(Integer value) {
        return value != null && value == 1;
    }

    private String templateChecksum(List<LifecycleTemplateStageDefinition> stages) {
        String canonical = stages.stream()
                .map(stage -> String.join("|",
                        stage.stageCode(), stage.stageName(), Integer.toString(stage.stageOrder()),
                        stage.progressWeight().toPlainString(), Boolean.toString(stage.required()),
                        Boolean.toString(stage.allowSkip()), String.valueOf(stage.plannedDurationDays()),
                        Boolean.toString(stage.autoStart()), Boolean.toString(stage.approvalRequired()),
                        String.valueOf(stage.approvalSceneCode()), stage.completionMode()))
                .reduce((left, right) -> left + "||" + right)
                .orElseThrow();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
