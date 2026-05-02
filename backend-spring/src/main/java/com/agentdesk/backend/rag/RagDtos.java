package com.agentdesk.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RagDtos {

    private RagDtos() {
    }

    public record UploadRagDocumentResponse(
            DocumentItem document,
            IndexJobItem job,
            String stream
    ) {
    }

    public record DocumentListResponse(
            List<DocumentItem> items,
            int limit
    ) {
    }

    public record DocumentItem(
            UUID id,
            UUID projectId,
            UUID threadId,
            UUID folderId,
            String sourceName,
            String sourcePath,
            String storageUri,
            String mimeType,
            long sizeBytes,
            String sha256,
            String status,
            int chunkCount,
            String embeddingModel,
            Integer embeddingDimension,
            String milvusCollection,
            JsonNode metadata,
            String errorMessage,
            Instant indexedAt,
            Instant createdAt
    ) {
    }

    public record IndexJobItem(
            UUID id,
            UUID projectId,
            UUID documentId,
            UUID threadId,
            String jobType,
            String status,
            JsonNode metadata,
            Instant createdAt
    ) {
    }

    public record CreateDocumentCommand(
            UUID documentId,
            UUID projectId,
            UUID threadId,
            UUID folderId,
            UUID uploadedBy,
            String sourceName,
            String sourcePath,
            String storageUri,
            String mimeType,
            long sizeBytes,
            String sha256,
            String milvusCollection,
            JsonNode metadata
    ) {
    }

    public record RagIndexJobPayload(
            UUID jobId,
            UUID documentId,
            UUID projectId,
            UUID threadId,
            UUID folderId,
            String storageUri,
            String sourceName,
            String sourcePath,
            String mimeType,
            long sizeBytes,
            String sha256,
            String scope,
            String milvusCollection
    ) {
    }
}
