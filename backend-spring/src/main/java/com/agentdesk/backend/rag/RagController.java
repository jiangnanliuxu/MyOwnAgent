package com.agentdesk.backend.rag;

import com.agentdesk.backend.common.api.ApiResponse;
import com.agentdesk.backend.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@Validated
class RagController {

    private final RagService ragService;

    RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/api/v1/threads/{threadId}/rag/uploads")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiResponse<RagDtos.UploadRagDocumentResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID threadId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "scope", defaultValue = "thread") String scope,
            @RequestParam(name = "source_path", required = false) String sourcePath
    ) {
        return ApiResponse.success(ragService.upload(user, threadId, file, scope, sourcePath));
    }

    @GetMapping("/api/v1/threads/{threadId}/rag/documents")
    ApiResponse<RagDtos.DocumentListResponse> documents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID threadId,
            @RequestParam(name = "limit", required = false) @Min(1) @Max(100) Integer limit
    ) {
        return ApiResponse.success(ragService.listDocuments(user, threadId, limit));
    }
}
