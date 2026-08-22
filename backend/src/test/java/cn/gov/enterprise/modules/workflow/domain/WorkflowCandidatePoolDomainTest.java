package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gov.enterprise.modules.workflow.domain.assignment.AssignmentStrategy;
import cn.gov.enterprise.modules.workflow.domain.assignment.ResolverContractHash;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidateMemberStatus;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePool;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolHash;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolMember;
import cn.gov.enterprise.modules.workflow.domain.candidate.CandidatePoolStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowCandidatePoolDomainTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 10, 0);
    private static final String CONTRACT = ResolverContractHash.sha256("resolver-contract").value();
    private static final String RULE = ResolverContractHash.sha256("rule").value();

    @Test
    void poolMustBeImmutableAndFreezeOnlyOnce() {
        ArrayList<CandidatePoolMember> input = new ArrayList<>(List.of(member(12L, 2, "r2"),
                member(11L, 1, "r1")));
        CandidatePool pool = pool(input).available();
        input.clear();

        assertThat(pool.status()).isEqualTo(CandidatePoolStatus.AVAILABLE);
        assertThat(pool.members()).extracting(CandidatePoolMember::candidateUserId)
                .containsExactly(11L, 12L);
        assertThatThrownBy(() -> pool.members().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(pool::available).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateCandidateAndOrderMustBeRejected() {
        assertThatThrownBy(() -> pool(List.of(member(11L, 1, "r1"), member(11L, 2, "r2"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unique");
        assertThatThrownBy(() -> pool(List.of(member(11L, 1, "r1"), member(12L, 1, "r2"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sortOrder");
    }

    @Test
    void hashMustBeStableUnderInputOrderAndChangeWithCandidateFacts() {
        CandidatePoolHash first = CandidatePoolHash.calculate("ROLE_RESOLVER", "V1", CONTRACT,
                RULE, AssignmentStrategy.Type.ROLE,
                List.of(member(11L, 1, "r1"), member(12L, 2, "r2")));
        CandidatePoolHash reordered = CandidatePoolHash.calculate("ROLE_RESOLVER", "V1", CONTRACT,
                RULE, AssignmentStrategy.Type.ROLE,
                List.of(member(12L, 2, "r2"), member(11L, 1, "r1")));
        CandidatePoolHash changed = CandidatePoolHash.calculate("ROLE_RESOLVER", "V1", CONTRACT,
                RULE, AssignmentStrategy.Type.ROLE,
                List.of(member(11L, 1, "r1"), member(13L, 2, "r2")));

        assertThat(first).isEqualTo(reordered);
        assertThat(first.value()).matches("[0-9a-f]{64}");
        assertThat(changed).isNotEqualTo(first);
    }

    @Test
    void candidateDomainMustRemainFrameworkAndPersistenceFree() throws IOException {
        Path root = Path.of("src/main/java/cn/gov/enterprise/modules/workflow/domain/candidate");
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

    private CandidatePool pool(List<CandidatePoolMember> members) {
        return CandidatePool.created(100L, "WCP-100", 200L, 300L, 400L, 500L,
                600L, 700L, 800L, 900L, 1000L, AssignmentStrategy.Type.ROLE,
                "ROLE_RESOLVER", "V1", ResolverContractHash.of(CONTRACT), RULE,
                NOW, NOW, NOW.plusDays(1), members, "test pool");
    }

    private CandidatePoolMember member(Long userId, int order, String sourceRef) {
        String eligibility = "eligible:" + userId;
        return new CandidatePoolMember(1000L + userId, 100L, 200L, 300L, userId,
                AssignmentStrategy.Type.ROLE, sourceRef, 30L, null, 40L,
                eligibility, ResolverContractHash.sha256(eligibility).value(), order,
                NOW, CandidateMemberStatus.INCLUDED, "test member", 0);
    }
}
