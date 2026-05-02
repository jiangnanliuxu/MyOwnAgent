package com.agentdesk.backend.role;

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

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void roleEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{projectId}/roles", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.request_id", not(blankOrNullString())));
    }

    @Test
    void rolesCanBeListedPatchedAndSyncedWithThreads() throws Exception {
        AuthSession session = register("b06-" + UUID.randomUUID() + "@example.com");

        JsonNode roles = read(mockMvc.perform(get("/api/v1/projects/{projectId}/roles?include_thread_roles=true", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findRole(roles, "primary").path("bound_threads").isArray()).isTrue();

        UUID srcAuthFolderId = findFirstFolderId(roles, "thread-role-session-review");
        UUID sessionReviewRoleId = findRoleId(roles, "thread-role-session-review");

        JsonNode patched = read(mockMvc.perform(patch("/api/v1/roles/{roleId}", sessionReviewRoleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "代码审查专家",
                                  "description": "新的角色说明会同步回来源 thread",
                                  "config": {
                                    "compression": "强压缩",
                                    "api_key": "should-not-be-stored"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role.name").value("代码审查专家"))
                .andExpect(jsonPath("$.data.role.config.compression").value("强压缩"))
                .andExpect(jsonPath("$.data.role.config.secret_ref").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.data.role.config.api_key").doesNotExist())
                .andExpect(jsonPath("$.data.synced_threads[0].thread_client_key").value("session-review"))
                .andExpect(jsonPath("$.data.synced_threads[0].label_updated").value(true))
                .andReturn());
        mockMvc.perform(get("/api/v1/roles/{roleId}", sessionReviewRoleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role.bound_threads[0].thread_label").value("代码审查专家"));

        JsonNode synced = read(mockMvc.perform(post("/api/v1/folders/{folderId}/sync-roles", srcAuthFolderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folder_name").value("src/auth"))
                .andExpect(jsonPath("$.data.synced").value(not(0)))
                .andReturn());
        assertThat(hasThreadRole(synced, "session-auth")).isTrue();
    }

    @Test
    void roleModelConnectionCanBeTestedBeforeSaving() throws Exception {
        AuthSession session = register("b21-" + UUID.randomUUID() + "@example.com");
        JsonNode roles = read(mockMvc.perform(get("/api/v1/projects/{projectId}/roles?include_thread_roles=true", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        UUID roleId = findRoleId(roles, "thread-role-session-review");
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer llmServer = startLlmServer(requestBody);
        try {
            mockMvc.perform(post("/api/v1/roles/{roleId}/test-connection", roleId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "config": {
                                        "provider": "Local Mock",
                                        "endpoint": "http://127.0.0.1:%d/v1/chat/completions",
                                        "api_format": "OpenAI Chat Completions",
                                        "model": "mock-model",
                                        "api_key": "test-key",
                                        "config_json": "{\\"temperature\\":0.2,\\"max_tokens\\":16}"
                                      }
                                    }
                                    """.formatted(llmServer.getAddress().getPort())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("connected"))
                    .andExpect(jsonPath("$.data.model").value("mock-model"))
                    .andExpect(jsonPath("$.data.latency_ms", not(blankOrNullString())));

            JsonNode sent = objectMapper.readTree(requestBody.get());
            assertThat(sent.path("model").asText()).isEqualTo("mock-model");
            assertThat(sent.path("max_tokens").asInt()).isEqualTo(16);
        } finally {
            llmServer.stop(0);
        }
    }

    @Test
    void crossUserRoleAccessIsRejected() throws Exception {
        AuthSession owner = register("b06-owner-" + UUID.randomUUID() + "@example.com");
        AuthSession other = register("b06-other-" + UUID.randomUUID() + "@example.com");

        JsonNode roles = read(mockMvc.perform(get("/api/v1/projects/{projectId}/roles?include_thread_roles=true", owner.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        UUID roleId = findRoleId(roles, "primary");
        UUID folderId = findFirstFolderId(roles, "primary");

        mockMvc.perform(get("/api/v1/roles/{roleId}", roleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/folders/{folderId}/sync-roles", folderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private UUID findRoleId(JsonNode roles, String clientKey) {
        return UUID.fromString(findRole(roles, clientKey).path("id").asText());
    }

    private JsonNode findRole(JsonNode roles, String clientKey) {
        for (JsonNode item : roles.path("data").path("items")) {
            if (clientKey.equals(item.path("client_key").asText())) {
                return item;
            }
        }
        throw new AssertionError("Role not found: " + clientKey);
    }

    private UUID findFirstFolderId(JsonNode roles, String clientKey) {
        for (JsonNode item : roles.path("data").path("items")) {
            if (clientKey.equals(item.path("client_key").asText())) {
                return UUID.fromString(item.path("bound_threads").get(0).path("folder_id").asText());
            }
        }
        throw new AssertionError("Role folder not found: " + clientKey);
    }

    private boolean hasThreadRole(JsonNode synced, String threadClientKey) {
        for (JsonNode item : synced.path("data").path("thread_roles")) {
            if (threadClientKey.equals(item.path("thread_client_key").asText())) {
                return true;
            }
        }
        return false;
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","name":"B06 User","password":"password-123"}
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

    private HttpServer startLlmServer(AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"choices":[{"message":{"role":"assistant","content":"OK"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private record AuthSession(String accessToken, UUID projectId) {
    }
}
