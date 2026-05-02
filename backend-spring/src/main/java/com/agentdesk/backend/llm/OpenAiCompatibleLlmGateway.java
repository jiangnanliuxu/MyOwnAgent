package com.agentdesk.backend.llm;

import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiCompatibleLlmGateway implements LlmGateway {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ChatTurn chat(ChatConfig config, List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        assertSupported(config);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", messages);
        body.put("temperature", config.temperature());
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.endpoint()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.BAD_GATEWAY, "LLM request failed with status " + response.statusCode() + ".");
            }
            return parseTurn(response.body());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_GATEWAY, "LLM request failed: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BAD_GATEWAY, "LLM request was interrupted.");
        }
    }

    private ChatTurn parseTurn(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode message = root.path("choices").path(0).path("message");
        if (message.isMissingNode() || message.isNull()) {
            throw new BusinessException(ErrorCode.BAD_GATEWAY, "LLM response does not contain a chat message.");
        }
        String content = content(message.path("content"));
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode calls = message.path("tool_calls");
        if (calls.isArray()) {
            for (JsonNode call : calls) {
                String id = call.path("id").asText("");
                JsonNode function = call.path("function");
                String name = function.path("name").asText("");
                String rawArguments = function.path("arguments").asText("{}");
                toolCalls.add(new ToolCall(id, name, rawArguments, parseArguments(rawArguments)));
            }
        }
        return new ChatTurn(content, List.copyOf(toolCalls));
    }

    private JsonNode parseArguments(String rawArguments) {
        if (!StringUtils.hasText(rawArguments)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(rawArguments);
            return parsed.isObject() ? parsed : objectMapper.createObjectNode();
        } catch (IOException exception) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("raw", rawArguments);
            return fallback;
        }
    }

    private String content(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        return contentNode.toString();
    }

    private void assertSupported(ChatConfig config) {
        if (config == null
                || !StringUtils.hasText(config.endpoint())
                || !StringUtils.hasText(config.apiKey())
                || !StringUtils.hasText(config.model())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM endpoint, model and API key are required.");
        }
        String apiFormat = StringUtils.hasText(config.apiFormat()) ? config.apiFormat() : "";
        boolean chatCompletions = apiFormat.equalsIgnoreCase("OpenAI Chat Completions")
                || config.endpoint().contains("/chat/completions");
        if (!chatCompletions) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only OpenAI Chat Completions compatible endpoints are supported in B19.");
        }
    }
}
