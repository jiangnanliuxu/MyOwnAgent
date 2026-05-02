package com.agentdesk.backend.workspace;

import com.agentdesk.backend.bootstrap.BootstrapResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WorkspaceRepository {

    void ensureSeeded(UUID projectId);

    List<WorkspaceDtos.FolderItem> listFolders(UUID projectId, boolean includeThreads);

    WorkspaceDtos.CreateFolderResponse createFolder(UUID projectId, WorkspaceDtos.CreateFolderRequest request);

    Optional<UUID> findProjectIdByFolder(UUID folderId);

    Optional<UUID> findProjectIdByThread(UUID threadId);

    List<BootstrapResponse.ThreadView> listThreads(UUID folderId);

    WorkspaceDtos.CreateThreadResponse createThread(UUID folderId, WorkspaceDtos.CreateThreadRequest request);

    Optional<BootstrapResponse.ThreadView> findThread(UUID threadId);

    BootstrapResponse.ThreadView patchThread(UUID threadId, WorkspaceDtos.PatchThreadRequest request);

    List<BootstrapResponse.MessageView> listMessages(UUID threadId, int limit);

    WorkspaceDtos.SendMessageResponse sendMessage(UUID threadId, WorkspaceDtos.SendMessageRequest request, String idempotencyKey);
}
