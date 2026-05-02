package com.agentdesk.backend.workspace;

import com.agentdesk.backend.bootstrap.BootstrapResponse;
import com.agentdesk.backend.rag.RagRetrievalService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AgentOrchestrationService {

    private final WorkspaceRepository workspaceRepository;

    public AgentOrchestrationService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    public AgentOrchestrationPlan plan(
            UUID threadId,
            WorkspaceDtos.SendMessageRequest request,
            RagRetrievalService.RetrievalResult retrieval
    ) {
        BootstrapResponse.ThreadView thread = workspaceRepository.findThread(threadId).orElseThrow();
        List<String> roleKeys = thread.roleKeys() == null || thread.roleKeys().isEmpty()
                ? thread.roles()
                : thread.roleKeys();
        String fromRole = StringUtils.hasText(thread.focusRoleKey()) ? thread.focusRoleKey() : firstRole(roleKeys);
        String preferredRole = preferredRole(request.content());
        String toRole = roleKeys != null && roleKeys.contains(preferredRole) ? preferredRole : fromRole;
        boolean handoffRequired = StringUtils.hasText(toRole) && StringUtils.hasText(fromRole) && !toRole.equals(fromRole);
        List<String> skillIds = requestedSkillIds(request.context());
        String reason = handoffRequired
                ? "message intent matched role '" + toRole + "'"
                : "focus role handles this message";
        return new AgentOrchestrationPlan(
                fromRole,
                toRole,
                handoffRequired,
                reason,
                skillIds,
                retrieval.enabled() ? retrieval.count() : 0
        );
    }

    private String preferredRole(String content) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        if (normalized.contains("测试") || normalized.contains("test")) {
            return "test";
        }
        if (normalized.contains("路由") || normalized.contains("route")) {
            return "route";
        }
        if (normalized.contains("登录") || normalized.contains("auth") || normalized.contains("token")) {
            return "auth";
        }
        if (normalized.contains("审查") || normalized.contains("review")) {
            return "review";
        }
        return "primary";
    }

    private String firstRole(List<String> roleKeys) {
        return roleKeys == null || roleKeys.isEmpty() ? "primary" : roleKeys.get(0);
    }

    private List<String> requestedSkillIds(JsonNode context) {
        if (context == null || !context.has("skill_ids") || !context.path("skill_ids").isArray()) {
            return List.of();
        }
        List<String> skillIds = new ArrayList<>();
        context.path("skill_ids").forEach(node -> {
            if (StringUtils.hasText(node.asText())) {
                skillIds.add(node.asText());
            }
        });
        return List.copyOf(skillIds);
    }

    public record AgentOrchestrationPlan(
            String fromRoleKey,
            String toRoleKey,
            boolean handoffRequired,
            String reason,
            List<String> skillIds,
            int ragSnippetCount
    ) {
    }
}
