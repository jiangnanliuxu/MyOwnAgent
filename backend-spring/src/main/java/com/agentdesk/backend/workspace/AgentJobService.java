package com.agentdesk.backend.workspace;

import com.agentdesk.backend.bootstrap.BootstrapResponse;
import com.agentdesk.backend.rag.RagRetrievalService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AgentJobService {

    private final WorkspaceRepository workspaceRepository;
    private final AgentEventBus agentEventBus;
    private final RagRetrievalService ragRetrievalService;
    private final AgentOrchestrationService orchestrationService;
    private final AgentLlmService agentLlmService;

    public AgentJobService(
            WorkspaceRepository workspaceRepository,
            AgentEventBus agentEventBus,
            RagRetrievalService ragRetrievalService,
            AgentOrchestrationService orchestrationService,
            AgentLlmService agentLlmService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.agentEventBus = agentEventBus;
        this.ragRetrievalService = ragRetrievalService;
        this.orchestrationService = orchestrationService;
        this.agentLlmService = agentLlmService;
    }

    public void enqueueJob(UUID projectId, UUID threadId, WorkspaceDtos.SendMessageResponse response, WorkspaceDtos.SendMessageRequest request) {
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
        BootstrapResponse.ThreadView thread = workspaceRepository.findThread(threadId).orElseThrow();
        String content;
        try {
            AgentLlmService.Answer answer = agentLlmService.generate(
                    projectId,
                    thread,
                    request,
                    plan,
                    retrieval,
                    progress -> {
                        agentEventBus.publish(threadId, "tool_call", Map.of(
                                "message_id", response.agentPlaceholder().id(),
                                "name", progress.name(),
                                "arguments", progress.arguments()
                        ));
                        agentEventBus.publish(threadId, "tool_result", Map.of(
                                "message_id", response.agentPlaceholder().id(),
                                "name", progress.name(),
                                "success", progress.success(),
                                "content", progress.content()
                        ));
                    }
            );
            content = answer.content();
        } catch (RuntimeException exception) {
            content = "模型调用失败：" + exception.getMessage() + "。请检查模型请求地址、API 格式、模型名和 API Key。";
        }
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
}
