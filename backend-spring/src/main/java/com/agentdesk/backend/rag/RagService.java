package com.agentdesk.backend.rag;

import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.security.AuthenticatedUser;
import com.agentdesk.backend.workspace.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class RagService {

    private static final int DEFAULT_DOCUMENT_LIMIT = 20;
    private static final int MAX_DOCUMENT_LIMIT = 100;
    private static final long MAX_UPLOAD_BYTES = 50L * 1024L * 1024L;

    private final WorkspaceService workspaceService;
    private final RagDocumentStorage storage;
    private final RagRepository ragRepository;
    private final RagIndexJobPublisher jobPublisher;
    private final ObjectMapper objectMapper;

    public RagService(
            WorkspaceService workspaceService,
            RagDocumentStorage storage,
            RagRepository ragRepository,
            RagIndexJobPublisher jobPublisher,
            ObjectMapper objectMapper
    ) {
        this.workspaceService = workspaceService;
        this.storage = storage;
        this.ragRepository = ragRepository;
        this.jobPublisher = jobPublisher;
        this.objectMapper = objectMapper;
    }

    public RagDtos.UploadRagDocumentResponse upload(
            AuthenticatedUser user,
            UUID threadId,
            MultipartFile file,
            String scope,
            String sourcePath
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File is required.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File exceeds the 50MB upload limit.");
        }

        WorkspaceService.ThreadContext thread = workspaceService.resolveThreadContext(user, threadId);
        UUID documentId = UUID.randomUUID();
        byte[] content = content(file);
        String sourceName = safeSourceName(file.getOriginalFilename());
        String mimeType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
        String normalizedScope = StringUtils.hasText(scope) ? scope.trim() : "thread";
        if (!List.of("thread", "project").contains(normalizedScope)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "scope must be thread or project.");
        }

        RagDocumentStorage.StoredObject stored = store(thread, documentId, sourceName, mimeType, content);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("scope", normalizedScope);
        metadata.put("thread_client_key", thread.threadClientKey());
        metadata.put("thread_label", thread.label());
        metadata.put("folder", thread.folder());
        metadata.put("upload_entry", "composer_file_button");
        RagRepository.RagUploadRecord upload = ragRepository.createUpload(new RagDtos.CreateDocumentCommand(
                documentId,
                thread.projectId(),
                thread.threadId(),
                thread.folderId(),
                user.id(),
                sourceName,
                StringUtils.hasText(sourcePath) ? sourcePath.trim() : null,
                stored.storageUri(),
                mimeType,
                content.length,
                sha256(content),
                "agent_desk_chunks",
                metadata
        ));

        jobPublisher.publish(toPayload(upload.job(), upload.document(), normalizedScope));
        return new RagDtos.UploadRagDocumentResponse(upload.document(), upload.job(), RagIndexJobPublisher.STREAM_KEY);
    }

    public RagDtos.DocumentListResponse listDocuments(AuthenticatedUser user, UUID threadId, Integer requestedLimit) {
        workspaceService.resolveThreadContext(user, threadId);
        int limit = requestedLimit == null ? DEFAULT_DOCUMENT_LIMIT : Math.min(Math.max(requestedLimit, 1), MAX_DOCUMENT_LIMIT);
        return new RagDtos.DocumentListResponse(ragRepository.listThreadDocuments(threadId, limit), limit);
    }

    private RagDtos.RagIndexJobPayload toPayload(
            RagDtos.IndexJobItem job,
            RagDtos.DocumentItem document,
            String scope
    ) {
        return new RagDtos.RagIndexJobPayload(
                job.id(),
                document.id(),
                document.projectId(),
                document.threadId(),
                document.folderId(),
                document.storageUri(),
                document.sourceName(),
                document.sourcePath(),
                document.mimeType(),
                document.sizeBytes(),
                document.sha256(),
                scope,
                document.milvusCollection()
        );
    }

    private RagDocumentStorage.StoredObject store(
            WorkspaceService.ThreadContext thread,
            UUID documentId,
            String sourceName,
            String mimeType,
            byte[] content
    ) {
        try {
            return storage.store(new RagDocumentStorage.StoreCommand(
                    thread.projectId(),
                    thread.threadId(),
                    documentId,
                    sourceName,
                    mimeType,
                    content
            ));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read uploaded file.");
        }
    }

    private byte[] content(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Failed to read uploaded file.");
        }
    }

    private String safeSourceName(String originalFilename) {
        String fallback = "upload.bin";
        if (!StringUtils.hasText(originalFilename)) {
            return fallback;
        }
        String cleaned = org.springframework.util.StringUtils.cleanPath(originalFilename.trim())
                .replace('\\', '_')
                .replace('/', '_');
        if (!StringUtils.hasText(cleaned) || cleaned.contains("..")) {
            return fallback;
        }
        return cleaned;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 is not available.");
        }
    }
}
