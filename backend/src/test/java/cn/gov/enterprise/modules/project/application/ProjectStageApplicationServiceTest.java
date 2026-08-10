package cn.gov.enterprise.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.gov.enterprise.modules.project.application.service.ProjectResourceAccessService;
import cn.gov.enterprise.modules.project.application.service.ProjectStageApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectStageApplicationServiceTest {
    @Mock ProjectResourceAccessService accessService;

    @Test
    void shouldReadLifecycleSnapshotInStageOrder() {
        when(accessService.requireAccessible(100L))
                .thenReturn(ProjectApplicationTestFixtures.project(100L));
        ProjectStageApplicationService service = new ProjectStageApplicationService(accessService);

        var stages = service.queryStages(100L);

        assertThat(stages).extracting(stage -> stage.stageCode())
                .containsExactly("RESERVE", "IMPLEMENTATION");
        assertThat(stages.getFirst().stageName()).isEqualTo("储备");
        assertThat(stages.getFirst().stageOrder()).isEqualTo(1);
        assertThat(stages.getFirst().status()).isEqualTo("IN_PROGRESS");
    }
}
