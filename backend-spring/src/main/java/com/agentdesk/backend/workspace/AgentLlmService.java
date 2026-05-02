package com.agentdesk.backend.workspace;

import com.agentdesk.backend.bootstrap.BootstrapResponse;
import com.agentdesk.backend.llm.LlmGateway;
import com.agentdesk.backend.rag.RagRetrievalService;
import com.agentdesk.backend.role.RoleDtos;
import com.agentdesk.backend.role.RoleLookupService;
import com.agentdesk.backend.secret.InMemorySecretVault;
import com.agentdesk.backend.tools.DeveloperToolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AgentLlmService {

    private static final int MAX_TOOL_ROUNDS = 2;

    private final RoleLookupService roleLookupService;
    private final InMemorySecretVault secretVault;
    private final DeveloperToolService developerToolService;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    public AgentLlmService(
            RoleLookupService roleLookupService,
            InMemorySecretVault secretVault,
            DeveloperToolService developerToolService,
            LlmGateway llmGateway,
            ObjectMapper objectMapper
    ) {
        this.roleLookupService = roleLookupService;
        this.secretVault = secretVault;
        this.developerToolService = developerToolService;
        this.llmGateway = llmGateway;
        this.objectMapper = objectMapper;
    }

    public Answer generate(
            UUID projectId,
            BootstrapResponse.ThreadView thread,
            WorkspaceDtos.SendMessageRequest request,
            AgentOrchestrationService.AgentOrchestrationPlan plan,
            RagRetrievalService.RetrievalResult retrieval,
            Consumer<ToolProgress> toolProgressConsumer
    ) {
        List<RoleDtos.RoleItem> roleCandidates = resolveRoleCandidates(projectId, thread, plan);
        Optional<ConfiguredRole> configuredRole = roleCandidates.stream()
                .map(role -> chatConfig(role).map(config -> new ConfiguredRole(role, config)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        if (configuredRole.isEmpty()) {
            return new Answer(fallbackContent(retrieval, "当前角色还没有可用的 OpenAI Chat Completions 模型配置或 API Key。"));
        }
        RoleDtos.RoleItem role = configuredRole.get().role();
        LlmGateway.ChatConfig config = configuredRole.get().config();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt(thread, role, plan, retrieval)));
        messages.add(Map.of("role", "user", "content", request.content()));
        List<Map<String, Object>> tools = developerToolService.toolDefinitions();

        String lastContent = "";
        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            LlmGateway.ChatTurn turn = llmGateway.chat(config, messages, tools);
            lastContent = turn.content();
            if (!turn.hasToolCalls()) {
                return new Answer(StringUtils.hasText(lastContent) ? lastContent : "模型已完成处理，但未返回文本内容。");
            }

            messages.add(assistantToolMessage(turn));
            for (LlmGateway.ToolCall call : turn.toolCalls()) {
                DeveloperToolService.ToolResult result = developerToolService.invoke(call.name(), call.arguments());
                toolProgressConsumer.accept(new ToolProgress(call.name(), call.rawArguments(), result.success(), result.content()));
                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", StringUtils.hasText(call.id()) ? call.id() : call.name(),
                        "content", result.content()
                ));
            }
        }
        if (StringUtils.hasText(lastContent)) {
            return new Answer(lastContent);
        }
        return new Answer("模型已调用工具完成分析，但超过当前工具调用轮数限制，请缩小问题后重试。");
    }

    private List<RoleDtos.RoleItem> resolveRoleCandidates(
            UUID projectId,
            BootstrapResponse.ThreadView thread,
            AgentOrchestrationService.AgentOrchestrationPlan plan
    ) {
        Map<UUID, RoleDtos.RoleItem> candidates = new LinkedHashMap<>();
        if (StringUtils.hasText(plan.toRoleKey())) {
            addCandidate(candidates, roleLookupService.findRoleByClientKey(projectId, plan.toRoleKey()));
        }
        if (thread.sessionRoleId() != null) {
            addCandidate(candidates, roleLookupService.findRole(thread.sessionRoleId()));
        }
        if (StringUtils.hasText(thread.sessionRoleKey())) {
            addCandidate(candidates, roleLookupService.findRoleByClientKey(projectId, thread.sessionRoleKey()));
        }
        if (thread.focusRoleId() != null) {
            addCandidate(candidates, roleLookupService.findRole(thread.focusRoleId()));
        }
        if (StringUtils.hasText(thread.focusRoleKey())) {
            addCandidate(candidates, roleLookupService.findRoleByClientKey(projectId, thread.focusRoleKey()));
        }
        roleLookupService.listRoles(projectId).forEach(role -> candidates.putIfAbsent(role.id(), role));
        return List.copyOf(candidates.values());
    }

    private void addCandidate(Map<UUID, RoleDtos.RoleItem> candidates, Optional<RoleDtos.RoleItem> role) {
        role.ifPresent(item -> candidates.putIfAbsent(item.id(), item));
    }

    private Optional<LlmGateway.ChatConfig> chatConfig(RoleDtos.RoleItem role) {
        JsonNode config = role.config();
        String endpoint = text(config, "endpoint");
        String model = text(config, "model");
        String apiFormat = text(config, "api_format");
        String secretRef = text(config, "secret_ref");
        String apiKey = secretVault.resolve(secretRef)
                .or(() -> Optional.ofNullable(System.getenv("AGENT_DESK_LLM_DEFAULT_API_KEY")).filter(StringUtils::hasText))
                .or(() -> Optional.ofNullable(System.getenv("SILICONFLOW_API_KEY")).filter(StringUtils::hasText))
                .orElse("");
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(model) || !StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }
        boolean chatCompletions = "OpenAI Chat Completions".equalsIgnoreCase(apiFormat)
                || endpoint.contains("/chat/completions");
        if (!chatCompletions) {
            return Optional.empty();
        }
        return Optional.of(new LlmGateway.ChatConfig(endpoint, apiKey, model, apiFormat, temperature(config)));
    }

    private String systemPrompt(
            BootstrapResponse.ThreadView thread,
            RoleDtos.RoleItem role,
            AgentOrchestrationService.AgentOrchestrationPlan plan,
            RagRetrievalService.RetrievalResult retrieval
    ) {
        JsonNode config = role.config();
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 Agent Desk 的受控开发助手，必须用中文回答，优先给出可执行的工程结论。\n");
        prompt.append("当前会话：").append(thread.folder()).append(" / ").append(thread.label()).append("\n");
        prompt.append("当前角色：").append(role.name()).append("（").append(role.clientKey()).append("）\n");
        if (StringUtils.hasText(text(config, "prompt_prefix"))) {
            prompt.append("角色提示前缀：").append(text(config, "prompt_prefix")).append("\n");
        }
        if (StringUtils.hasText(text(config, "routing"))) {
            prompt.append("路由规则：").append(text(config, "routing")).append("\n");
        }
        prompt.append("编排原因：").append(plan.reason()).append("\n");
        prompt.append("工具边界：你只能使用 project_list_files、project_read_file、project_search 读取和检索项目；不能要求执行未授权命令，不能输出真实密钥。\n");
        if (retrieval.enabled() && retrieval.count() > 0) {
            prompt.append("RAG 召回片段：\n");
            for (RagRetrievalService.RetrievalSnippet snippet : retrieval.snippets()) {
                prompt.append("- ")
                        .append(snippet.sourceName())
                        .append(" [")
                        .append(snippet.status())
                        .append("]: ")
                        .append(snippet.preview())
                        .append("\n");
            }
        }
        return prompt.toString();
    }

    private Map<String, Object> assistantToolMessage(LlmGateway.ChatTurn turn) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", turn.content() == null ? "" : turn.content());
        List<Map<String, Object>> calls = new ArrayList<>();
        for (LlmGateway.ToolCall call : turn.toolCalls()) {
            calls.add(Map.of(
                    "id", StringUtils.hasText(call.id()) ? call.id() : call.name(),
                    "type", "function",
                    "function", Map.of(
                            "name", call.name(),
                            "arguments", call.rawArguments()
                    )
            ));
        }
        message.put("tool_calls", calls);
        return message;
    }

    private String fallbackContent(RagRetrievalService.RetrievalResult retrieval, String reason) {
        String base = "Mock Agent 已接收你的消息。" + reason + " 请在机器人设置页把 API 格式设为 OpenAI Chat Completions，并重新保存 API Key。";
        if (!retrieval.enabled() || retrieval.count() == 0) {
            return base;
        }
        List<String> sources = retrieval.snippets().stream()
                .map(RagRetrievalService.RetrievalSnippet::sourceName)
                .toList();
        return base + " 本次已执行 RAG 检索，召回 " + retrieval.count() + " 个片段：" + String.join("、", sources) + "。";
    }

    private String text(JsonNode config, String key) {
        if (config == null) {
            return "";
        }
        return config.path(key).asText("");
    }

    private double temperature(JsonNode config) {
        String configJson = text(config, "config_json");
        if (!StringUtils.hasText(configJson)) {
            return 0.2;
        }
        try {
            JsonNode parsed = objectMapper.readTree(configJson);
            if (parsed.has("temperature")) {
                return Math.min(Math.max(parsed.path("temperature").asDouble(0.2), 0), 2);
            }
        } catch (IOException ignored) {
            return 0.2;
        }
        return 0.2;
    }

    public record Answer(String content) {
    }

    public record ToolProgress(
            String name,
            String arguments,
            boolean success,
            String content
    ) {
    }

    private record ConfiguredRole(RoleDtos.RoleItem role, LlmGateway.ChatConfig config) {
    }
}
