package com.agentdesk.backend.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleLlmGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsOpenAiChatCompletionsPayloadAndParsesToolCalls() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(requestBody, """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "",
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "project_search",
                              "arguments": "{\\"query\\":\\"AgentJobService\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """);
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
            OpenAiCompatibleLlmGateway gateway = new OpenAiCompatibleLlmGateway(objectMapper);

            LlmGateway.ChatTurn turn = gateway.chat(
                    new LlmGateway.ChatConfig(endpoint, "test-key", "Pro/zai-org/GLM-4.7", "OpenAI Chat Completions", 0.2, 64),
                    List.of(Map.of("role", "user", "content", "搜索 AgentJobService")),
                    List.of(Map.of("type", "function", "function", Map.of("name", "project_search")))
            );

            JsonNode sent = objectMapper.readTree(requestBody.get());
            assertThat(sent.path("model").asText()).isEqualTo("Pro/zai-org/GLM-4.7");
            assertThat(sent.path("max_tokens").asInt()).isEqualTo(64);
            assertThat(sent.path("messages").get(0).path("content").asText()).contains("AgentJobService");
            assertThat(sent.path("tools").get(0).path("function").path("name").asText()).isEqualTo("project_search");
            assertThat(turn.toolCalls()).hasSize(1);
            assertThat(turn.toolCalls().get(0).arguments().path("query").asText()).isEqualTo("AgentJobService");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(AtomicReference<String> requestBody, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }
}
