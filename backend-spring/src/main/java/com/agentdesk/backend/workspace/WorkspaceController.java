package com.agentdesk.backend.workspace;

import com.agentdesk.backend.common.api.ApiResponse;
import com.agentdesk.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/projects/{projectId}/folders")
    public ApiResponse<WorkspaceDtos.FolderListResponse> listFolders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @RequestParam(name = "include_threads", defaultValue = "false") boolean includeThreads
    ) {
        return ApiResponse.success(workspaceService.listFolders(user, projectId, includeThreads));
    }

    @PostMapping("/projects/{projectId}/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkspaceDtos.CreateFolderResponse> createFolder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody WorkspaceDtos.CreateFolderRequest request
    ) {
        return ApiResponse.success(workspaceService.createFolder(user, projectId, request));
    }

    @GetMapping("/folders/{folderId}/threads")
    public ApiResponse<WorkspaceDtos.ThreadListResponse> listThreads(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID folderId
    ) {
        return ApiResponse.success(workspaceService.listThreads(user, folderId));
    }

    @PostMapping("/folders/{folderId}/threads")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkspaceDtos.CreateThreadResponse> createThread(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID folderId,
            @Valid @RequestBody WorkspaceDtos.CreateThreadRequest request
    ) {
        return ApiResponse.success(workspaceService.createThread(user, folderId, request));
    }

    @GetMapping("/threads/{threadId}")
    public ApiResponse<WorkspaceDtos.ThreadResponse> getThread(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID threadId
    ) {
        return ApiResponse.success(workspaceService.getThread(user, threadId));
    }

    @PatchMapping("/threads/{threadId}")
    public ApiResponse<WorkspaceDtos.ThreadResponse> patchThread(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID threadId,
            @Valid @RequestBody WorkspaceDtos.PatchThreadRequest request
    ) {
        return ApiResponse.success(workspaceService.patchThread(user, threadId, request));
    }

    @GetMapping("/threads/{threadId}/messages")
    public ApiResponse<WorkspaceDtos.MessagePageResponse> listMessages(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID threadId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return ApiResponse.success(workspaceService.listMessages(user, threadId, limit));
    }

    @PostMapping("/threads/{threadId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<WorkspaceDtos.SendMessageResponse> sendMessage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID threadId,
            @Valid @RequestBody WorkspaceDtos.SendMessageRequest request,
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ApiResponse.success(workspaceService.sendMessage(user, threadId, request, idempotencyKey));
    }
}
