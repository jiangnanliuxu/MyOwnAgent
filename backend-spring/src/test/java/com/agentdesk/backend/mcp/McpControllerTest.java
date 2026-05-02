package com.agentdesk.backend.mcp;

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
class McpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void mcpEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{projectId}/mcp", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.request_id", not(blankOrNullString())));
    }

    @Test
    void endpointsCanBeManagedCheckedAndRegisteredAsTools() throws Exception {
        AuthSession session = register("b08-" + UUID.randomUUID() + "@example.com");

        JsonNode listed = read(mockMvc.perform(get("/api/v1/projects/{projectId}/mcp", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(findEndpoint(listed, "browser").path("tools").isArray()).isTrue();

        JsonNode created = read(mockMvc.perform(post("/api/v1/projects/{projectId}/mcp", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Custom MCP",
                                  "transport": "http",
                                  "auth_type": "api_key",
                                  "url": "https://api.example.com/mcp",
                                  "tools": ["get_data", "post_result", "get_data"],
                                  "health_config": {"timeout_ms": 5000},
                                  "secret_input": "must-not-return"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endpoint.name").value("Custom MCP"))
                .andExpect(jsonPath("$.data.endpoint.tools[0]").value("get_data"))
                .andExpect(jsonPath("$.data.endpoint.tools[1]").value("post_result"))
                .andExpect(jsonPath("$.data.endpoint.secret_ref").value(not(blankOrNullString())))
                .andReturn());
        UUID endpointId = UUID.fromString(created.path("data").path("endpoint").path("id").asText());

        mockMvc.perform(patch("/api/v1/mcp/{endpointId}", endpointId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "待连接",
                                  "tools": ["get_data", "summarize"],
                                  "secret_input": "still-secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endpoint.status").value("待连接"))
                .andExpect(jsonPath("$.data.endpoint.tools[1]").value("summarize"))
                .andExpect(jsonPath("$.data.endpoint.secret_ref").value(not(blankOrNullString())));

        mockMvc.perform(post("/api/v1/mcp/{endpointId}/health-check", endpointId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"timeout_ms": 3000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("已连接"))
                .andExpect(jsonPath("$.data.result.tools_available[0]").value("get_data"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/mcp/tools", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].tool_name", not(blankOrNullString())));

        mockMvc.perform(post("/api/v1/projects/{projectId}/mcp/health-check-all", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total").value(not(0)))
                .andExpect(jsonPath("$.data.summary.connected").value(not(0)));

        mockMvc.perform(get("/api/v1/projects/{projectId}/mcp/health-status", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0][0]").value("MCP 连接"));
    }

    @Test
    void internalToolInvokeRequiresTokenAndAuditsPlaceholderResult() throws Exception {
        AuthSession session = register("b08-internal-" + UUID.randomUUID() + "@example.com");
        JsonNode listed = read(mockMvc.perform(get("/api/v1/projects/{projectId}/mcp", session.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode browser = findEndpoint(listed, "browser");

        mockMvc.perform(post("/internal/tools/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"project_id":"%s","endpoint_client_key":"browser","tool_name":"click","arguments":{"selector":"#run"}}
                                """.formatted(session.projectId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/internal/tools/invoke")
                        .header("X-Internal-Token", "local-dev-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"project_id":"%s","endpoint_id":"%s","tool_name":"click","arguments":{"selector":"#run"}}
                                """.formatted(session.projectId(), browser.path("id").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("placeholder"))
                .andExpect(jsonPath("$.data.tool_name").value("click"))
                .andExpect(jsonPath("$.data.result.mode").value("placeholder"));
    }

    @Test
    void crossUserEndpointAccessIsRejected() throws Exception {
        AuthSession owner = register("b08-owner-" + UUID.randomUUID() + "@example.com");
        AuthSession other = register("b08-other-" + UUID.randomUUID() + "@example.com");

        JsonNode listed = read(mockMvc.perform(get("/api/v1/projects/{projectId}/mcp", owner.projectId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andReturn());
        UUID endpointId = UUID.fromString(findEndpoint(listed, "browser").path("id").asText());

        mockMvc.perform(get("/api/v1/mcp/{endpointId}", endpointId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private JsonNode findEndpoint(JsonNode response, String clientKey) {
        for (JsonNode item : response.path("data").path("items")) {
            if (clientKey.equals(item.path("client_key").asText())) {
                return item;
            }
        }
        throw new AssertionError("MCP endpoint not found: " + clientKey);
    }

    private AuthSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","name":"B08 User","password":"password-123"}
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
