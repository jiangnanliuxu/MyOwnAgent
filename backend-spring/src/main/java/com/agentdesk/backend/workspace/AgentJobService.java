package com.agentdesk.backend.workspace;

import com.agentdesk.backend.bootstrap.BootstrapResponse;
import com.agentdesk.backend.rag.RagRetrievalService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentJobService {

    private final WorkspaceRepository workspaceRepository;
    private final AgentEventBus agentEventBus;
    private final RagRetrievalService ragRetrievalService;
    private final AgentOrchestrationService orchestrationService;

    public AgentJobService(
            WorkspaceRepository workspaceRepository,
            AgentEventBus agentEventBus,
            RagRetrievalService ragRetrievalService,
            AgentOrchestrationService orchestrationService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.agentEventBus = agentEventBus;
        this.ragRetrievalService = ragRetrievalService;
        this.orchestrationService = orchestrationService;
    }

    public void enqueueMockJob(UUID threadId, WorkspaceDtos.SendMessageResponse response, WorkspaceDtos.SendMessageRequest request) {
        agentEventBus.publish(threadId, "job_queued", Map.of(
                "job_id", response.jobId(),
                "message_id", response.message().id(),
                "placeholder_id", response.agentPlaceholder().id()
        ));
        RagRetrievalService.RetrievalResult retrieval = ragRetrievalService.retrieve(threadId, request.content(), request.rag());
        AgentOrchestrationService.AgentOrchestrationPlan plan = orchestrationService.plan(threadId, request, retrieval);
        agentEventBus.publish(threadId, "agent_selected", Map.of(
                "message_id", response.agentPlaceholder().id(),
                "role_key", plan.toRoleKey(),
                "reason", plan.reason(),
                "rag_snippet_count", plan.ragSnippetCount()
        ));
        if (plan.handoffRequired()) {
            agentEventBus.publish(threadId, "agent_handoff", Map.of(
                    "message_id", response.agentPlaceholder().id(),
                    "from_role_key", plan.fromRoleKey(),
                    "to_role_key", plan.toRoleKey(),
                    "reason", plan.reason()
            ));
        }
        if (!plan.skillIds().isEmpty()) {
            agentEventBus.publish(threadId, "skill_run_planned", Map.of(
                    "message_id", response.agentPlaceholder().id(),
                    "role_key", plan.toRoleKey(),
                    "skill_ids", plan.skillIds(),
                    "runner", "python-skill-runner"
            ));
        }
        if (retrieval.enabled() && retrieval.count() > 0) {
            agentEventBus.publish(threadId, "rag_retrieval", Map.of(
                    "message_id", response.agentPlaceholder().id(),
                    "scope", retrieval.scope(),
                    "count", retrieval.count(),
                    "snippets", retrieval.snippets()
            ));
        }
        String content = content(retrieval);
        agentEventBus.publish(threadId, "message_delta", Map.of(
                "message_id", response.agentPlaceholder().id(),
                "content", content
        ));
        BootstrapResponse.MessageView completed = workspaceRepository.completeAgentMessage(
                threadId,
                response.agentPlaceholder().clientMessageId(),
                content
        );
        agentEventBus.publish(threadId, "message_completed", completed);
    }

    private String content(RagRetrievalService.RetrievalResult retrieval) {
        String base = "Mock Agent 已接收你的消息。B09 已接通 SSE 事件流；真实 Python Agent 会在 B10 后接入。";
        if (!retrieval.enabled() || retrieval.count() == 0) {
            return base;
        }
        List<String> sources = retrieval.snippets().stream()
                .map(RagRetrievalService.RetrievalSnippet::sourceName)
                .toList();
        return base + " B12 已执行 RAG 检索，召回 " + retrieval.count() + " 个片段：" + String.join("、", sources) + "。";
    }
}
