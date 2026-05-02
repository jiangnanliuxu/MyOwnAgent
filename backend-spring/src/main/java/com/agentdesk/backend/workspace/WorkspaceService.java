package com.agentdesk.backend.workspace;

import com.agentdesk.backend.auth.AuthRepository;
import com.agentdesk.backend.bootstrap.BootstrapResponse;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 100;

    private final AuthRepository authRepository;
    private final WorkspaceRepository workspaceRepository;

    public WorkspaceService(AuthRepository authRepository, WorkspaceRepository workspaceRepository) {
        this.authRepository = authRepository;
        this.workspaceRepository = workspaceRepository;
    }

    public WorkspaceDtos.FolderListResponse listFolders(
            AuthenticatedUser user,
            UUID projectId,
            boolean includeThreads
    ) {
        assertProjectAccess(user, projectId);
        workspaceRepository.ensureSeeded(projectId);
        return new WorkspaceDtos.FolderListResponse(workspaceRepository.listFolders(projectId, includeThreads));
    }

    public WorkspaceDtos.CreateFolderResponse createFolder(
            AuthenticatedUser user,
            UUID projectId,
            WorkspaceDtos.CreateFolderRequest request
    ) {
        assertProjectAccess(user, projectId);
        workspaceRepository.ensureSeeded(projectId);
        return workspaceRepository.createFolder(projectId, request);
    }

    public WorkspaceDtos.ThreadListResponse listThreads(AuthenticatedUser user, UUID folderId) {
        UUID projectId = projectIdForFolder(folderId);
        assertProjectAccess(user, projectId);
        workspaceRepository.ensureSeeded(projectId);
        return new WorkspaceDtos.ThreadListResponse(workspaceRepository.listThreads(folderId));
    }

    public WorkspaceDtos.CreateThreadResponse createThread(
            AuthenticatedUser user,
            UUID folderId,
            WorkspaceDtos.CreateThreadRequest request
    ) {
        UUID projectId = projectIdForFolder(folderId);
        assertProjectAccess(user, projectId);
        workspaceRepository.ensureSeeded(projectId);
        return workspaceRepository.createThread(folderId, request);
    }

    public WorkspaceDtos.ThreadResponse getThread(AuthenticatedUser user, UUID threadId) {
        UUID projectId = projectIdForThread(threadId);
        assertProjectAccess(user, projectId);
        return new WorkspaceDtos.ThreadResponse(workspaceRepository.findThread(threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Thread is not available.")));
    }

    public WorkspaceDtos.ThreadResponse patchThread(
            AuthenticatedUser user,
            UUID threadId,
            WorkspaceDtos.PatchThreadRequest request
    ) {
        UUID projectId = projectIdForThread(threadId);
        assertProjectAccess(user, projectId);
        return new WorkspaceDtos.ThreadResponse(workspaceRepository.patchThread(threadId, request));
    }

    public WorkspaceDtos.MessagePageResponse listMessages(
            AuthenticatedUser user,
            UUID threadId,
            Integer requestedLimit
    ) {
        UUID projectId = projectIdForThread(threadId);
        assertProjectAccess(user, projectId);
        int limit = requestedLimit == null ? DEFAULT_MESSAGE_LIMIT : Math.min(Math.max(requestedLimit, 1), MAX_MESSAGE_LIMIT);
        List<BootstrapResponse.MessageView> messages = workspaceRepository.listMessages(threadId, limit + 1);
        String nextCursor = messages.size() > limit ? messages.get(limit - 1).createdAt().toString() : null;
        List<BootstrapResponse.MessageView> items = messages.size() > limit ? messages.subList(0, limit) : messages;
        return new WorkspaceDtos.MessagePageResponse(items, nextCursor, limit);
    }

    public WorkspaceDtos.SendMessageResponse sendMessage(
            AuthenticatedUser user,
            UUID threadId,
            WorkspaceDtos.SendMessageRequest request,
            String idempotencyKey
    ) {
        UUID projectId = projectIdForThread(threadId);
        assertProjectAccess(user, projectId);
        String effectiveIdempotencyKey = StringUtils.hasText(idempotencyKey)
                ? idempotencyKey.trim()
                : request.clientMessageId();
        return workspaceRepository.sendMessage(threadId, request, effectiveIdempotencyKey);
    }

    private void assertProjectAccess(AuthenticatedUser user, UUID projectId) {
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        if (!authRepository.projectBelongsToUser(user.id(), projectId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Project is not available.");
        }
    }

    private UUID projectIdForFolder(UUID folderId) {
        return workspaceRepository.findProjectIdByFolder(folderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Folder is not available."));
    }

    private UUID projectIdForThread(UUID threadId) {
        return workspaceRepository.findProjectIdByThread(threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Thread is not available."));
    }
}
