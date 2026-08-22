package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaim;
import cn.gov.enterprise.modules.workflow.domain.claim.TaskClaimResult;
import cn.gov.enterprise.modules.workflow.domain.model.WorkflowTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WorkflowTaskClaimDomainTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 12, 0);
    private static final String HASH = "a".repeat(64);

    @Test
    void candidateTaskClaimMustAssignWithoutApproval() {
        WorkflowTask claimed = candidateTask().claim(21L, NOW);
        assertThat(claimed.status()).isEqualTo(WorkflowTask.Status.CLAIMED);
        assertThat(claimed.assigneeUserId()).isEqualTo(21L);
        assertThat(claimed.completedTime()).isNull();
        assertThat(claimed.decisionResult()).isNull();
    }

    @Test
    void directAndRepeatedClaimMustBeRejected() {
        assertThatThrownBy(() -> directTask().claim(21L, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIRECT_ASSIGNMENT_NOT_CLAIMABLE");
        assertThatThrownBy(() -> candidateTask().claim(21L, NOW).claim(21L, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void claimFactAndResultMustRemainConsistent() {
        TaskClaim claim = TaskClaim.claimed(1L, 2L, 3L, 4L, 5L, 6L, 21L,
                NOW, "idem", "trace", HASH, "eligible", "ALLOW:ORG",
                "BASIC:V1:ALLOW", 7);
        TaskClaimResult result = TaskClaimResult.from(claim, false);
        assertThat(result.taskStatus()).isEqualTo("CLAIMED");
        assertThat(result.poolStatus()).isEqualTo("CLAIMED");
        assertThat(claim.taskVersionAfter()).isEqualTo(8);
    }

    @Test
    void claimDomainMustRemainFrameworkFree() throws IOException {
        Path root = Path.of("src/main/java/cn/gov/enterprise/modules/workflow/domain/claim");
        try (var files = Files.walk(root)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try { return Files.readAllLines(path).stream(); }
                        catch (IOException exception) { throw new IllegalStateException(exception); }
                    })
                    .filter(line -> line.startsWith("import org.springframework.")
                            || line.startsWith("import com.baomidou.mybatisplus.")
                            || line.startsWith("import cn.gov.enterprise.modules.workflow.infrastructure."))
                    .toList()).isEmpty();
        }
    }

    private WorkflowTask candidateTask() {
        return new WorkflowTask(2L, "WFT-2", 5L, 7L, 8L, 6L, "N", "Node", 1,
                "N:1:1", null, "{}", WorkflowTask.Status.PENDING,
                "APPROVE,REJECT", null, null, null, null, null, 7,
                WorkflowTask.AssignmentMode.CANDIDATE_POOL);
    }

    private WorkflowTask directTask() {
        return new WorkflowTask(2L, "WFT-2", 5L, 7L, 8L, 6L, "N", "Node", 1,
                "N:1:1", 21L, "{}", WorkflowTask.Status.PENDING,
                "APPROVE,REJECT", null, null, null, null, null, 7,
                WorkflowTask.AssignmentMode.DIRECT);
    }
}
