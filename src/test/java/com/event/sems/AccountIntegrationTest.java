package com.event.sems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.event.sems.repository.RoleRepository;
import com.event.sems.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:account_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
class AccountIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserAccountRepository users;
  @Autowired RoleRepository roles;
  @Autowired PasswordEncoder passwords;

  @BeforeEach
  void sampleAccounts() {
    SampleAccounts.seed(roles, users, passwords);
  }

  @Test
  void databasePasswordsAndSessionPermissions() throws Exception {
    var admin = users.findByUsernameIgnoreCase("admin").orElseThrow();
    assertThat(admin.getPasswordHash()).startsWith("$2").isNotEqualTo("Admin@123456");
    assertThat(admin.getEmail()).isEqualTo("admin@sems.example");

    mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .param("username", "admin")
                .param("password", "wrong"))
        .andExpect(status().isUnauthorized());

    MockHttpSession adminSession = login("admin", "Admin@123456");
    mvc.perform(get("/api/v1/admin/users").session(adminSession)).andExpect(status().isOk());

    MvcResult created =
        mvc.perform(
                post("/api/v1/admin/users")
                    .session(adminSession)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsBytes(
                            Map.of(
                                "username",
                                "gate-staff",
                                "displayName",
                                "Nhân viên cổng",
                                "email",
                                "gate-staff@example.com",
                                "password",
                                "StaffPassword123!"))))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode staff = json.readTree(created.getResponse().getContentAsByteArray());
    assertThat(staff.has("passwordHash")).isFalse();
    long staffId = staff.get("id").asLong();
    assertThat(users.findByUsernameIgnoreCase("gate-staff").orElseThrow().getPasswordHash())
        .startsWith("$2");

    MockHttpSession staffSession = login("gate-staff", "StaffPassword123!");
    mvc.perform(get("/api/v1/admin/users").session(staffSession)).andExpect(status().isForbidden());

    mvc.perform(
            patch("/api/v1/admin/users/" + staffId + "/active")
                .session(adminSession)
                .with(csrf())
                .contentType("application/json")
                .content("{\"active\":false}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me").session(staffSession)).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .param("username", "gate-staff")
                .param("password", "StaffPassword123!"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void attendeeSignupCreatesOnlyAttendeeAndSessionsKeepRolesSeparate() throws Exception {
    mvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "username", "thai-demo",
                            "displayName", "Nguyễn Trường Thái",
                            "email", "thai-demo@example.com",
                            "password", "VisitorPassword123!"))))
        .andExpect(status().isCreated());
    var attendee = users.findByUsernameIgnoreCase("thai-demo").orElseThrow();
    assertThat(attendee.getPasswordHash()).startsWith("$2");
    assertThat(attendee.getRoles()).extracting(role -> role.getName()).containsExactly("ATTENDEE");

    MockHttpSession attendeeSession = login("thai-demo", "VisitorPassword123!");
    mvc.perform(post("/api/v1/account/profile").session(attendeeSession).with(csrf())
        .contentType("application/json")
        .content("{\"displayName\":\"Thái đã cập nhật\"}"))
        .andExpect(status().isOk());
    assertThat(users.findByUsernameIgnoreCase("thai-demo").orElseThrow().getDisplayName())
        .isEqualTo("Thái đã cập nhật");
    MockHttpSession staffSession = login("staff", "Staff@123456");
    MockHttpSession adminSession = login("admin", "Admin@123456");
    mvc.perform(get("/api/v1/events").session(attendeeSession)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/events").session(staffSession)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/events").session(adminSession)).andExpect(status().isOk());
    String search = "/api/v1/events/999999/check-ins/search?query=an";
    mvc.perform(get(search).session(staffSession)).andExpect(status().isNotFound());
    mvc.perform(get(search).session(adminSession)).andExpect(status().isForbidden());
    mvc.perform(get(search).session(attendeeSession)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/users").session(attendeeSession))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/users").session(staffSession)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/users").session(adminSession)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/me").session(attendeeSession)).andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        Map.of(
                            "username", "admin",
                            "displayName", "Khác",
                            "email", "other@example.com",
                            "password", "VisitorPassword123!"))))
        .andExpect(status().isConflict());
  }

  private MockHttpSession login(String username, String password) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .param("username", username)
                    .param("password", password))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }
}
