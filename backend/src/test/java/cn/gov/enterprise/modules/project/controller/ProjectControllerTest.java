package cn.gov.enterprise.modules.project.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.gov.enterprise.common.api.PageResponse;
import cn.gov.enterprise.common.exception.GlobalExceptionHandler;
import cn.gov.enterprise.modules.project.dto.ProjectDtos;
import cn.gov.enterprise.modules.project.service.ProjectLifecycleService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitConfig(ProjectControllerTest.TestConfig.class)
@WebAppConfiguration
class ProjectControllerTest {
    @Autowired ProjectLifecycleService service;
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
                9001L, "PRJ-001", "测试项目", "DIGITAL", "SELF_OPERATED",
                200L, 100L, "RESERVED", null, null, null, null,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, "RESERVE",
                "LOW", BigDecimal.ZERO, null,
                LocalDateTime.now(), LocalDateTime.now(), 0);
        when(service.page(1, 20, null, null, null, null))
                .thenReturn(new PageResponse<>(List.of(project), 1, 1, 20));

        mockMvc.perform(get("/projects").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].projectNo").value("PRJ-001"));
    }

    @Test
    @WithMockUser(authorities = "project:lifecycle:create")
    void pageRejectsUserWithoutListPermission() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        ProjectLifecycleService projectLifecycleService() {
            return mock(ProjectLifecycleService.class);
        }

        @Bean
        ProjectController projectController(ProjectLifecycleService service) {
            return new ProjectController(service);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
