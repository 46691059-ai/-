package cn.gov.enterprise.modules.project.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.GlobalExceptionHandler;
import cn.gov.enterprise.modules.project.application.service.ProjectApplicationService;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitConfig(ProjectControllerTest.TestConfig.class)
@WebAppConfiguration
@ActiveProfiles("project-controller-test")
class ProjectControllerTest {
    @Autowired ProjectLifecycleService service;
    @Autowired ProjectApplicationService applicationService;
    @Autowired WebApplicationContext applicationContext;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    @WithMockUser(authorities = "project:lifecycle:list")
    void pageReturnsUnifiedRestResponse() throws Exception {
        ProjectDtos.Response project = new ProjectDtos.Response(
                9001L, "PRJ-001", "测试项目", "04", "SELF_OPERATED",
                null, null, 200L, 100L, "RESERVED", null, null, null, null,
                BigDecimal.TEN, null, BigDecimal.ONE, BigDecimal.ZERO, null, null,
                "RESERVE", "LOW", BigDecimal.ZERO, null, null,
                LocalDateTime.now(), LocalDateTime.now(), 0);
        when(service.page(1, 20, null, null, null, null))
                .thenReturn(new PageResponse<>(List.of(project), 1, 1, 20));

        mockMvc.perform(get("/projects").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].projectNo").value("PRJ-001"));
    }

    @Test
    @WithMockUser(authorities = "project:lifecycle:create")
    void pageRejectsUserWithoutListPermission() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @WithMockUser(authorities = "project:lifecycle:list")
    void projectPermissionUserQueriesThroughApplicationService() throws Exception {
        ProjectDtos.DetailResponse detail = new ProjectDtos.DetailResponse(
                null, List.of(), List.of(), 0, List.of(), 0);
        when(applicationService.queryProject(9001L)).thenReturn(detail);

        mockMvc.perform(get("/projects/{projectId}", 9001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(authorities = "project:add")
    void administratorCreatesThroughApplicationService() throws Exception {
        ProjectDtos.DetailResponse detail = new ProjectDtos.DetailResponse(
                null, List.of(), List.of(), 0, List.of(), 0);
        when(applicationService.createProject(any(ProjectDtos.CreateRequest.class))).thenReturn(detail);

        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectNo":"PRJ-001",
                                  "projectName":"Repository migration",
                                  "projectType":"04",
                                  "leaderId":20,
                                  "departmentId":10,
                                  "budgetAmount":0,
                                  "expectedIncome":0,
                                  "expectedProfit":0,
                                  "riskLevel":"LOW"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(authorities = "profile:view")
    void userWithoutProjectPermissionCannotQueryProject() throws Exception {
        mockMvc.perform(get("/projects/{projectId}", 9001L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @TestConfiguration
    @Profile("project-controller-test")
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        ProjectLifecycleService projectLifecycleService() {
            return mock(ProjectLifecycleService.class);
        }

        @Bean
        ProjectApplicationService projectApplicationService() {
            return mock(ProjectApplicationService.class);
        }

        @Bean
        ProjectController projectController(
                ProjectLifecycleService service,
                ProjectApplicationService applicationService) {
            return new ProjectController(service, applicationService);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
