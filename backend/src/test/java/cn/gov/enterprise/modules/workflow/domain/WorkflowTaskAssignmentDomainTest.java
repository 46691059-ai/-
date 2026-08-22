package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentContext;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolutionResult;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResolverContext;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentResult;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentSnapshot;
import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserResolver;
import cn.gov.enterprise.modules.workflow.domain.assignment.ExplicitUserAssignmentStrategy;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowTaskAssignmentDomainTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 11, 10, 0);
    private final AssignmentContext context = new AssignmentContext(
            11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L,
            "17", now, "trace-1");

    @Test
    void explicitUserStrategyMustResolveOneFrozenUser() {
        var result = new ExplicitUserAssignmentStrategy().resolve(context, 99L);

        assertThat(result.strategyType()).isEqualTo(AssignmentStrategy.Type.USER);
        assertThat(result.targetType()).isEqualTo(AssignmentStrategy.Type.USER);
        assertThat(result.resolvedUserIds()).containsExactly(99L);
        assertThat(result.singleUserId()).isEqualTo(99L);
        assertThat(result.targetSnapshot()).isEqualTo("{\"userId\":99}");
    }

    @Test
    void snapshotMustFreezeContextAndResult() {
        var result = new ExplicitUserAssignmentStrategy().resolve(context, 99L);
        AssignmentSnapshot snapshot = AssignmentSnapshot.create(20L, context, result);

        assertThat(snapshot.taskId()).isEqualTo(11L);
        assertThat(snapshot.nodeExecutionId()).isEqualTo(15L);
        assertThat(snapshot.resolvedUserIds()).containsExactly(99L);
        assertThat(snapshot.resolveTime()).isEqualTo(now);
        assertThat(snapshot.version()).isZero();
    }

    @Test
    void invalidExplicitAssignmentMustBeRejected() {
        ExplicitUserAssignmentStrategy strategy = new ExplicitUserAssignmentStrategy();
        assertThatThrownBy(() -> strategy.resolve(context, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> strategy.resolve(context, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explicitUserResolverMustProduceOneDirectCandidate() {
        AssignmentResult strategyResult = new ExplicitUserAssignmentStrategy().resolve(context, 99L);

        var pool = new ExplicitUserResolver().resolve(
                new AssignmentResolverContext(context, strategyResult));
        var resolution = AssignmentResolutionResult.direct(pool);

        assertThat(pool.resolverVersion()).isEqualTo(ExplicitUserResolver.VERSION);
        assertThat(pool.assignmentReason()).isEqualTo("EXPLICIT_USER");
        assertThat(pool.candidateUserIds()).containsExactly(99L);
        assertThat(pool.candidates().getFirst().rank()).isEqualTo(1);
        assertThat(resolution.selectedUserId()).isEqualTo(99L);
        assertThat(resolution.toAssignmentResult().targetSnapshot())
                .isEqualTo(strategyResult.targetSnapshot());
    }

    @Test
    void explicitUserResolverMustRejectUnsupportedStrategy() {
        AssignmentResult roleResult = new AssignmentResult(
                AssignmentStrategy.Type.ROLE, AssignmentStrategy.Type.ROLE,
                "{\"roleCode\":\"MANAGER\"}", List.of(99L), now, "role-test");

        assertThatThrownBy(() -> new ExplicitUserResolver().resolve(
                new AssignmentResolverContext(context, roleResult)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only supports USER");
    }
}
