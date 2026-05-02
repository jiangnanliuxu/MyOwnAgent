package com.agentdesk.backend.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BootstrapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bootstrapRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{projectId}/bootstrap", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.request_id", not(blankOrNullString())));
    }

    @Test
    void bootstrapReturnsSeededFrontendState() throws Exception {
        AuthSession session = register("b04-" + UUID.randomUUID() + "@example.com");

        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectId}/bootstrap", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.project.id").value(session.projectId().toString()))
                .andExpect(jsonPath("$.data.active_thread_key").value("session-review"))
                .andExpect(jsonPath("$.data.preferences.layout_density").value("紧凑"))
                .andExpect(jsonPath("$.data.folders[0].name").value("src/auth"))
                .andExpect(jsonPath("$.data.folders[0].thread_keys[0]").value("session-review"))
                .andExpect(jsonPath("$.data.threads.session-review.folder").value("src/auth"))
                .andExpect(jsonPath("$.data.threads.session-review.file").value("src/auth/useSession.ts"))
                .andExpect(jsonPath("$.data.threads.session-review.roles[0]").value("primary"))
                .andExpect(jsonPath("$.data.threads.session-review.focus_role_key").value("review"))
                .andExpect(jsonPath("$.data.threads.route-test.label").value("test-agent"))
                .andExpect(jsonPath("$.data.recent_messages.session-review[0].role").value("user"))
                .andExpect(jsonPath("$.data.roles.primary.name").value("主助手"))
                .andExpect(jsonPath("$.data.roles.auth.name").value("auth-agent"))
                .andExpect(jsonPath("$.data.skills[0].client_key").value("frontend-design"))
                .andExpect(jsonPath("$.data.mcp_endpoints[0].client_key").value("figma"))
                .andExpect(jsonPath("$.data.health_items[0].name").value("Skill 装载"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("password_hash", "refresh_token", "api_key");
    }

    @Test
    void queryThreadOverridesAndPersistsActiveThread() throws Exception {
        AuthSession session = register("b04-query-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/api/v1/projects/{projectId}/bootstrap?thread=route-test", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_thread_key").value("route-test"));

        mockMvc.perform(get("/api/v1/me/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_thread_key").value("route-test"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/bootstrap?thread=missing-thread", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_thread_key").value("route-test"));
    }

    @Test
    void bootstrapRejectsOtherUsersProject() throws Exception {
        AuthSession owner = register("b04-owner-" + UUID.randomUUID() + "@example.com");
        AuthSession other = register("b04-other-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/api/v1/projects/{projectId}/bootstrap", owner.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","name":"B04 User","password":"password-123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new AuthSession(
                data.path("access_token").asText(),
                UUID.fromString(data.path("default_project_id").asText())
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthSession(String accessToken, UUID projectId) {
    }
}
