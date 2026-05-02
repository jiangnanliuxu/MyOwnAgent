package com.agentdesk.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!dev")
class InMemoryRagRepository implements RagRepository {

    private final ObjectMapper objectMapper;
    private final Map<UUID, RagDtos.DocumentItem> documents = new ConcurrentHashMap<>();
    private final Map<UUID, RagDtos.IndexJobItem> jobs = new ConcurrentHashMap<>();

    InMemoryRagRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RagUploadRecord createUpload(RagDtos.CreateDocumentCommand command) {
        Instant now = Instant.now();
        JsonNode metadata = command.metadata() == null ? objectMapper.createObjectNode() : command.metadata();
        RagDtos.DocumentItem document = new RagDtos.DocumentItem(
                command.documentId(),
                command.projectId(),
                command.threadId(),
                command.folderId(),
                command.sourceName(),
                command.sourcePath(),
                command.storageUri(),
                command.mimeType(),
                command.sizeBytes(),
                command.sha256(),
                "uploaded",
                0,
                null,
                null,
                command.milvusCollection(),
                metadata,
                null,
                null,
                now
        );
        RagDtos.IndexJobItem job = new RagDtos.IndexJobItem(
                UUID.randomUUID(),
                command.projectId(),
                command.documentId(),
                command.threadId(),
                "index",
                "queued",
                metadata,
                now
        );
        documents.put(document.id(), document);
        jobs.put(job.id(), job);
        return new RagUploadRecord(document, job);
    }

    @Override
    public List<RagDtos.DocumentItem> listThreadDocuments(UUID threadId, int limit) {
        return documents.values().stream()
                .filter(document -> threadId.equals(document.threadId()))
                .sorted(Comparator.comparing(RagDtos.DocumentItem::createdAt).reversed())
                .limit(limit)
                .toList();
    }
}
