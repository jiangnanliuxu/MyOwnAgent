package com.agentdesk.backend.workspace;

import com.agentdesk.backend.bootstrap.BootstrapResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class WorkspaceDtos {

    private WorkspaceDtos() {
    }

    public record FolderListResponse(List<FolderItem> items) {
    }

    public record FolderItem(
            UUID id,
            String name,
            String path,
            int sortOrder,
            int threadCount,
            List<String> threadKeys,
            List<BootstrapResponse.ThreadView> threads
    ) {
    }

    public record CreateFolderRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String path,
            Integer sortOrder
    ) {
    }

    public record CreateFolderResponse(
            FolderItem folder,
            BootstrapResponse.ThreadView defaultThread,
            List<BootstrapResponse.MessageView> initialMessages
    ) {
    }

    public record ThreadListResponse(List<BootstrapResponse.ThreadView> items) {
    }

    public record CreateThreadRequest(
            @Size(max = 160) String label,
            @Size(max = 500) String summary,
            List<String> roleKeys,
            String focusRoleKey
    ) {
    }

    public record ThreadResponse(BootstrapResponse.ThreadView thread) {
    }

    public record CreateThreadResponse(
            BootstrapResponse.ThreadView thread,
            List<BootstrapResponse.MessageView> initialMessages
    ) {
    }

    public record PatchThreadRequest(
            @Size(max = 160) String label,
            @Size(max = 500) String summary,
            String focusRoleKey,
            String status
    ) {
    }

    public record MessagePageResponse(
            List<BootstrapResponse.MessageView> items,
            String nextCursor,
            int limit
    ) {
    }

    public record SendMessageRequest(
            @Size(max = 120) String clientMessageId,
            @NotBlank @Size(max = 12000) String content,
            JsonNode context,
            JsonNode rag
    ) {
    }

    public record SendMessageResponse(
            BootstrapResponse.MessageView message,
            BootstrapResponse.MessageView agentPlaceholder,
            UUID jobId,
            String status,
            String streamUrl
    ) {
    }
}
