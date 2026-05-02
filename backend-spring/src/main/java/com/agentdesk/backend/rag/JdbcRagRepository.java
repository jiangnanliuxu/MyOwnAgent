package com.agentdesk.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Profile("dev")
class JdbcRagRepository implements RagRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    JdbcRagRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public RagUploadRecord createUpload(RagDtos.CreateDocumentCommand command) {
        RagDtos.DocumentItem document = jdbcClient.sql("""
                        INSERT INTO rag_documents (
                          id, project_id, thread_id, folder_id, uploaded_by, source_name, source_path,
                          storage_uri, mime_type, size_bytes, sha256, status, chunk_count,
                          milvus_collection, metadata
                        )
                        VALUES (
                          :id, :project_id, :thread_id, :folder_id, :uploaded_by, :source_name, :source_path,
                          :storage_uri, :mime_type, :size_bytes, :sha256, 'uploaded', 0,
                          :milvus_collection, CAST(:metadata AS jsonb)
                        )
                        RETURNING id, project_id, thread_id, folder_id, source_name, source_path, storage_uri,
                                  mime_type, size_bytes, sha256, status, chunk_count, embedding_model,
                                  embedding_dimension, milvus_collection, metadata::text AS metadata,
                                  error_message, indexed_at, created_at
                        """)
                .param("id", command.documentId())
                .param("project_id", command.projectId())
                .param("thread_id", command.threadId())
                .param("folder_id", command.folderId())
                .param("uploaded_by", command.uploadedBy())
                .param("source_name", command.sourceName())
                .param("source_path", command.sourcePath())
                .param("storage_uri", command.storageUri())
                .param("mime_type", command.mimeType())
                .param("size_bytes", command.sizeBytes())
                .param("sha256", command.sha256())
                .param("milvus_collection", command.milvusCollection())
                .param("metadata", json(command.metadata()))
                .query(this::documentItem)
                .single();

        RagDtos.IndexJobItem job = jdbcClient.sql("""
                        INSERT INTO rag_index_jobs (project_id, document_id, thread_id, job_type, status, metadata)
                        VALUES (:project_id, :document_id, :thread_id, 'index', 'queued', CAST(:metadata AS jsonb))
                        RETURNING id, project_id, document_id, thread_id, job_type, status, metadata::text AS metadata, created_at
                        """)
                .param("project_id", command.projectId())
                .param("document_id", command.documentId())
                .param("thread_id", command.threadId())
                .param("metadata", json(command.metadata()))
                .query((rs, rowNum) -> new RagDtos.IndexJobItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getObject("thread_id", UUID.class),
                        rs.getString("job_type"),
                        rs.getString("status"),
                        readJson(rs.getString("metadata")),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .single();
        return new RagUploadRecord(document, job);
    }

    @Override
    public List<RagDtos.DocumentItem> listThreadDocuments(UUID threadId, int limit) {
        return jdbcClient.sql("""
                        SELECT id, project_id, thread_id, folder_id, source_name, source_path, storage_uri,
                               mime_type, size_bytes, sha256, status, chunk_count, embedding_model,
                               embedding_dimension, milvus_collection, metadata::text AS metadata,
                               error_message, indexed_at, created_at
                        FROM rag_documents
                        WHERE thread_id = :thread_id AND status <> 'deleted'
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """)
                .param("thread_id", threadId)
                .param("limit", limit)
                .query(this::documentItem)
                .list();
    }

    private RagDtos.DocumentItem documentItem(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp indexedAt = rs.getTimestamp("indexed_at");
        return new RagDtos.DocumentItem(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("thread_id", UUID.class),
                rs.getObject("folder_id", UUID.class),
                rs.getString("source_name"),
                rs.getString("source_path"),
                rs.getString("storage_uri"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getString("sha256"),
                rs.getString("status"),
                rs.getInt("chunk_count"),
                rs.getString("embedding_model"),
                (Integer) rs.getObject("embedding_dimension"),
                rs.getString("milvus_collection"),
                readJson(rs.getString("metadata")),
                rs.getString("error_message"),
                indexedAt == null ? null : indexedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private String json(JsonNode value) {
        return value == null ? "{}" : value.toString();
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }
}
