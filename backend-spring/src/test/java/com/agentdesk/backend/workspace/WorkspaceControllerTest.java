package com.agentdesk.backend.workspace;

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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void workspaceEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{projectId}/folders", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.request_id", not(blankOrNullString())));
    }

    @Test
    void foldersThreadsAndMessagesCanBeManagedWithinProject() throws Exception {
        AuthSession session = register("b05-" + UUID.randomUUID() + "@example.com");

        JsonNode folders = read(mockMvc.perform(get("/api/v1/projects/{projectId}/folders?include_threads=true", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("src/auth"))
                .andExpect(jsonPath("$.data.items[0].threads[0].client_key").value("session-review"))
                .andReturn());

        UUID srcAuthFolderId = UUID.fromString(folders.path("data").path("items").get(0).path("id").asText());

        JsonNode createdFolder = read(mockMvc.perform(post("/api/v1/projects/{projectId}/folders", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"src/new-feature","sort_order":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.folder.name").value("src/new-feature"))
                .andExpect(jsonPath("$.data.default_thread.label").value("primary-agent"))
                .andExpect(jsonPath("$.data.initial_messages[0].role").value("agent"))
                .andReturn());
        UUID newFolderId = UUID.fromString(createdFolder.path("data").path("folder").path("id").asText());

        JsonNode createdThread = read(mockMvc.perform(post("/api/v1/folders/{folderId}/threads", newFolderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "会话 2",
                                  "summary": "新的独立会话",
                                  "role_keys": ["primary", "review", "test"],
                                  "focus_role_key": "primary"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.thread.label").value("会话 2"))
                .andExpect(jsonPath("$.data.thread.role_status").value("未编排"))
                .andReturn());
        UUID threadId = UUID.fromString(createdThread.path("data").path("thread").path("id").asText());

        mockMvc.perform(get("/api/v1/folders/{folderId}/threads", newFolderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].label").value("primary-agent"))
                .andExpect(jsonPath("$.data.items[1].label").value("会话 2"));

        mockMvc.perform(patch("/api/v1/threads/{threadId}", threadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"路由守卫专家","summary":"关注未登录跳转","focus_role_key":"review"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thread.label").value("路由守卫专家"))
                .andExpect(jsonPath("$.data.thread.focus_role_key").value("review"));

        mockMvc.perform(get("/api/v1/threads/{threadId}/messages?limit=1", threadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].role").value("agent"))
                .andExpect(jsonPath("$.data.next_cursor", not(blankOrNullString())));

        JsonNode sent = sendMessage(session, threadId, "msg-b05-1", "帮我列一下这三个文件各自可能受影响的逻辑点");
        UUID messageId = UUID.fromString(sent.path("data").path("message").path("id").asText());

        JsonNode repeated = sendMessage(session, threadId, "msg-b05-1", "这次重试不应该重复入库");
        UUID repeatedMessageId = UUID.fromString(repeated.path("data").path("message").path("id").asText());
        org.assertj.core.api.Assertions.assertThat(repeatedMessageId).isEqualTo(messageId);

        mockMvc.perform(get("/api/v1/threads/{threadId}/messages?limit=20", threadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[2].client_message_id").value("msg-b05-1"))
                .andExpect(jsonPath("$.data.items[2].role").value("user"))
                .andExpect(jsonPath("$.data.items[3].client_message_id").value("msg-b05-1:agent"))
                .andExpect(jsonPath("$.data.items[3].status").value("completed"));

        MvcResult stream = mockMvc.perform(get("/api/v1/threads/{threadId}/stream?last_event_id=0&replay_only=true", threadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("message_completed")))
                .andExpect(content().string(containsString("Mock Agent")));

        mockMvc.perform(get("/api/v1/folders/{folderId}/threads", srcAuthFolderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].client_key").value("session-review"));
    }

    @Test
    void crossUserWorkspaceAccessIsRejected() throws Exception {
        AuthSession owner = register("b05-owner-" + UUID.randomUUID() + "@example.com");
        AuthSession other = register("b05-other-" + UUID.randomUUID() + "@example.com");

        JsonNode folders = read(mockMvc.perform(get("/api/v1/projects/{projectId}/folders?include_threads=true", owner.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        UUID folderId = UUID.fromString(folders.path("data").path("items").get(0).path("id").asText());
        UUID threadId = UUID.fromString(folders.path("data").path("items").get(0).path("threads").get(0).path("id").asText());

        mockMvc.perform(get("/api/v1/folders/{folderId}/threads", folderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/threads/{threadId}/messages", threadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"client_message_id":"cross-user","content":"nope"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void messageSendEmitsAgentHandoffAndSkillPlanEvents() throws Exception {
        AuthSession session = register("b13-" + UUID.randomUUID() + "@example.com");
        JsonNode folders = read(mockMvc.perform(get("/api/v1/projects/{projectId}/folders?include_threads=true", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        UUID threadId = UUID.fromString(folders.path("data").path("items").get(0).path("threads").get(0).path("id").asText());

        mockMvc.perform(post("/api/v1/threads/{threadId}/messages", threadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .header("X-Idempotency-Key", "b13-handoff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "client_message_id": "b13-handoff",
                                  "content": "请检查登录 token 刷新逻辑，并运行指定 skill",
                                  "context": {"skill_ids": ["smoke-check", "auth-review"]},
                                  "rag": {"enabled": false}
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("processing"));

        MvcResult stream = mockMvc.perform(get("/api/v1/threads/{threadId}/stream?last_event_id=0&replay_only=true", threadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("agent_selected")))
                .andExpect(content().string(containsString("agent_handoff")))
                .andExpect(content().string(containsString("from_role_key")))
                .andExpect(content().string(containsString("auth")))
                .andExpect(content().string(containsString("skill_run_planned")))
                .andExpect(content().string(containsString("python-skill-runner")))
                .andExpect(content().string(containsString("smoke-check")));
    }

    private JsonNode sendMessage(AuthSession session, UUID threadId, String clientMessageId, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/threads/{threadId}/messages", threadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .header("X-Idempotency-Key", clientMessageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"client_message_id":"%s","content":"%s"}
                                """.formatted(clientMessageId, content)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.message.client_message_id").value(clientMessageId))
                .andExpect(jsonPath("$.data.message.role").value("user"))
                .andExpect(jsonPath("$.data.status").value("processing"))
                .andReturn();
        return read(result);
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","name":"B05 User","password":"password-123"}
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
