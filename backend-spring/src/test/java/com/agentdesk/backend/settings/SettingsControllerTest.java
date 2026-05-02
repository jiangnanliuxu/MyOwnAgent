package com.agentdesk.backend.settings;

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

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void settingsEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{projectId}/settings/overview", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.request_id", not(blankOrNullString())));
    }

    @Test
    void settingsOverviewCanBeReadAndPatched() throws Exception {
        AuthSession session = register("b14-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/api/v1/projects/{projectId}/settings/overview", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.context_compression.enabled").value(true))
                .andExpect(jsonPath("$.data.backup.target").value("minio"))
                .andExpect(jsonPath("$.data.tool_authorization.secret_policy").value("secret_ref_only"))
                .andExpect(jsonPath("$.data.task_queue.queued").value(0));

        mockMvc.perform(patch("/api/v1/projects/{projectId}/settings", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "context_compression": {"enabled": true, "strength": "aggressive", "trigger_tokens": 8000},
                                  "backup": {"enabled": true, "target": "minio", "retention_days": 14},
                                  "tool_authorization": {"mode": "approval_required", "secret_policy": "secret_ref_only"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.context_compression.strength").value("aggressive"))
                .andExpect(jsonPath("$.data.backup.retention_days").value(14))
                .andExpect(jsonPath("$.data.tool_authorization.mode").value("approval_required"));
    }

    @Test
    void taskLogsCanBeListedAndCrossUserSettingsAccessIsRejected() throws Exception {
        AuthSession owner = register("b14-owner-" + UUID.randomUUID() + "@example.com");
        AuthSession other = register("b14-other-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get("/api/v1/projects/{projectId}/task-logs?limit=5", owner.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limit").value(5))
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/api/v1/projects/{projectId}/settings/overview", owner.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","name":"B14 User","password":"password-123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new AuthSession(data.path("access_token").asText(), UUID.fromString(data.path("default_project_id").asText()));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthSession(String accessToken, UUID projectId) {
    }
}
