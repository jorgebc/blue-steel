package com.bluesteel.adapters.in.web.health;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
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

  @MockitoBean private JdbcTemplate jdbcTemplate;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(1);
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
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
  void health_returnsDegradedWhenDbIsDown() throws Exception {
    when(jdbcTemplate.queryForObject(anyString(), any(Class.class)))
        .thenThrow(new RuntimeException("connection refused"));

    mockMvc
        .perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DEGRADED"))
        .andExpect(jsonPath("$.data.db").value("DOWN"));
  }

  @Test
  void otherRoutes_requireAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/campaigns")).andExpect(status().isUnauthorized());
  }
}
