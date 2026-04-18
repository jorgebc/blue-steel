package com.bluesteel.adapters.in.web.health;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.port.out.ComponentStatus;
import com.bluesteel.application.port.out.HealthPort;
import com.bluesteel.application.port.out.SystemHealth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    classes = BlueSteelApplication.class,
    webEnvironment = WebEnvironment.MOCK,
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration,"
          + "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration"
    })
class HealthControllerTest {

  @MockitoBean private HealthPort healthPort;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    when(healthPort.check()).thenReturn(SystemHealth.of(ComponentStatus.UP));
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should return 200 with status=UP and db=UP when all components are healthy")
  void health_returnsUpWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("UP"))
        .andExpect(jsonPath("$.data.db").value("UP"))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors").isEmpty());
  }

  @Test
  @DisplayName("should return 200 with status=DEGRADED and db=DOWN when database is unreachable")
  void health_returnsDegradedWhenDbIsDown() throws Exception {
    when(healthPort.check()).thenReturn(SystemHealth.of(ComponentStatus.DOWN));

    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DEGRADED"))
        .andExpect(jsonPath("$.data.db").value("DOWN"));
  }

  @Test
  @DisplayName("should return 401 on any route other than /health when request is unauthenticated")
  void otherRoutes_requireAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/campaigns")).andExpect(status().isUnauthorized());
  }
}
