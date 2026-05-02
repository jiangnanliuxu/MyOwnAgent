package com.agentdesk.backend.workspace;

import com.agentdesk.backend.bootstrap.BootstrapResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AgentJobService {

    private final WorkspaceRepository workspaceRepository;
    private final AgentEventBus agentEventBus;

    public AgentJobService(WorkspaceRepository workspaceRepository, AgentEventBus agentEventBus) {
        this.workspaceRepository = workspaceRepository;
        this.agentEventBus = agentEventBus;
    }

    public void enqueueMockJob(UUID threadId, WorkspaceDtos.SendMessageResponse response) {
        agentEventBus.publish(threadId, "job_queued", Map.of(
                "job_id", response.jobId(),
                "message_id", response.message().id(),
                "placeholder_id", response.agentPlaceholder().id()
        ));
        String content = "Mock Agent 已接收你的消息。B09 已接通 SSE 事件流；真实 Python Agent 会在 B10 后接入。";
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
