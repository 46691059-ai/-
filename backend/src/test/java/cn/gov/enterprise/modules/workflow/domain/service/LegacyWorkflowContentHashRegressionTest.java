package cn.gov.enterprise.modules.workflow.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gov.enterprise.modules.workflow.domain.model.ResolverBindingModel;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowContentHashAlgorithm;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowDefinition;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowEngineMode;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowVersion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyWorkflowContentHashRegressionTest {
    private static final String EXISTING_GRAPH_HASH =
            "67c5bf892d3e5d57d3cb94f3c475096aee65aa41aaafda1dfa0dfc168e776567";

    @Test
    void existingGraphGoldenVectorMustRemainExact() {
        WorkflowDefinition definition = new WorkflowDefinition(
                1L, "WF_RC1", "RC1", "TEST", 100L, 30L,
                WorkflowDefinition.Status.ACTIVE, 20L, null, 0);
        LocalDateTime publishedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        WorkflowVersion version = new WorkflowVersion(
                20L, 1L, 1, WorkflowVersion.Status.PUBLISHED, "2.0",
                "0".repeat(64), null, publishedAt, null, 30L, publishedAt, null,
                WorkflowEngineMode.MULTI_NODE_LINEAR_V1,
                WorkflowContentHashAlgorithm.GRAPH_V2_SHA256, 0);

        String graphHash = new WorkflowVersionContentHasher()
                .hashGraph(definition, version, List.of(), List.of());
        assertThat(graphHash).isEqualTo(EXISTING_GRAPH_HASH);
        WorkflowCombinedContentComputation legacy = new WorkflowCombinedContentHasher()
                .compute(ResolverBindingModel.LEGACY_USER_ONLY, graphHash, null);
        assertThat(legacy.contentHash()).isEqualTo(EXISTING_GRAPH_HASH);
        assertThat(legacy.canonical()).isNull();
        assertThat(legacy.manifestHash()).isNull();
    }
}
