package com.agentdesk.backend.skill;

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
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void skillEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{projectId}/skills", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.request_id", not(blankOrNullString())));
    }

    @Test
    void skillsCanBeCreatedPatchedToggledAndSynced() throws Exception {
        AuthSession session = register("b07-" + UUID.randomUUID() + "@example.com");

        JsonNode listed = read(mockMvc.perform(get("/api/v1/projects/{projectId}/skills", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findSkill(listed, "frontend-design").path("mounts").isArray()).isTrue();

        JsonNode created = read(mockMvc.perform(post("/api/v1/projects/{projectId}/skills", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "code-reviewer",
                                  "source": "local skill",
                                  "scope": "代码审查、风格检查和最佳实践建议",
                                  "mounts": ["系统层", "角色层"],
                                  "config": {"auto_mount": true}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.skill.name").value("code-reviewer"))
                .andExpect(jsonPath("$.data.skill.status").value("待启用"))
                .andReturn());
        UUID skillId = UUID.fromString(created.path("data").path("skill").path("id").asText());

        mockMvc.perform(patch("/api/v1/skills/{skillId}", skillId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "启用",
                                  "mounts": ["任务层", "任务层", "记忆层"],
                                  "config": {"requires_rag": false}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skill.status").value("启用"))
                .andExpect(jsonPath("$.data.skill.mounts[0]").value("任务层"))
                .andExpect(jsonPath("$.data.skill.mounts[1]").value("记忆层"))
                .andExpect(jsonPath("$.data.skill.config.auto_mount").value(true))
                .andExpect(jsonPath("$.data.skill.config.requires_rag").value(false));

        mockMvc.perform(post("/api/v1/skills/{skillId}/toggle", skillId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skill.status").value("停用"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/skills/sync-policy", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(not(0)))
                .andExpect(jsonPath("$.data.synced").value(not(0)))
                .andExpect(jsonPath("$.data.items[0].last_run").value(not(blankOrNullString())));
    }

    @Test
    void crossUserSkillAccessIsRejected() throws Exception {
        AuthSession owner = register("b07-owner-" + UUID.randomUUID() + "@example.com");
        AuthSession other = register("b07-other-" + UUID.randomUUID() + "@example.com");

        JsonNode listed = read(mockMvc.perform(get("/api/v1/projects/{projectId}/skills", owner.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        UUID skillId = UUID.fromString(findSkill(listed, "frontend-design").path("id").asText());

        mockMvc.perform(get("/api/v1/skills/{skillId}", skillId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private JsonNode findSkill(JsonNode response, String clientKey) {
        for (JsonNode item : response.path("data").path("items")) {
            if (clientKey.equals(item.path("client_key").asText())) {
                return item;
            }
        }
        throw new AssertionError("Skill not found: " + clientKey);
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","name":"B07 User","password":"password-123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = read(result).path("data");
        return new AuthSession(data.path("access_token").asText(), UUID.fromString(data.path("default_project_id").asText()));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AuthSession(String accessToken, UUID projectId) {
    }
}
