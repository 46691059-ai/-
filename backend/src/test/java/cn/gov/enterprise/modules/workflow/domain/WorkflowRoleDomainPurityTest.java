package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowRoleDomainPurityTest {
    private static final List<String> FORBIDDEN = List.of(
            "import org.springframework.", "import com.baomidou.mybatisplus.",
            "import cn.gov.enterprise.modules.workflow.infrastructure.");

    @Test
    void roleDomainMustRemainFrameworkAndPersistenceFree() throws IOException {
        Path root = Path.of("src/main/java/cn/gov/enterprise/modules/workflow/domain/role");
        try (var files = Files.walk(root)) {
            List<String> violations = files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream()
                                    .filter(line -> FORBIDDEN.stream().anyMatch(line::startsWith))
                                    .map(line -> path + ": " + line);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    }).toList();
            assertThat(violations).isEmpty();
        }
    }
}
