package com.agentdesk.backend.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface LlmGateway {

    ChatTurn chat(ChatConfig config, List<Map<String, Object>> messages, List<Map<String, Object>> tools);

    record ChatConfig(
            String endpoint,
            String apiKey,
            String model,
            String apiFormat,
            double temperature,
            Integer maxTokens
    ) {
    }

    record ChatTurn(
            String content,
            List<ToolCall> toolCalls
    ) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    record ToolCall(
            String id,
            String name,
            String rawArguments,
            JsonNode arguments
    ) {
    }
}
