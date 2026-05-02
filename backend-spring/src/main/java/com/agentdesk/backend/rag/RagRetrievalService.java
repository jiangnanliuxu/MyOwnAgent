package com.agentdesk.backend.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class RagRetrievalService {

    private static final int DEFAULT_TOP_K = 4;
    private static final int MAX_TOP_K = 10;

    private final RagRepository ragRepository;

    public RagRetrievalService(RagRepository ragRepository) {
        this.ragRepository = ragRepository;
    }

    public RetrievalResult retrieve(UUID threadId, String query, JsonNode options) {
        if (!enabled(options)) {
            return new RetrievalResult(false, "thread", List.of());
        }
        int topK = topK(options);
        List<RetrievalSnippet> snippets = ragRepository.listThreadDocuments(threadId, Math.max(topK, DEFAULT_TOP_K)).stream()
                .filter(document -> List.of("uploaded", "indexing", "indexed").contains(document.status()))
                .limit(topK)
                .map(document -> new RetrievalSnippet(
                        document.id(),
                        document.sourceName(),
                        document.sourcePath(),
                        document.status(),
                        "文档已进入 RAG 索引队列，当前回答会优先参考该文件的元数据和后续可用片段。",
                        0.72
                ))
                .toList();
        return new RetrievalResult(true, scope(options), snippets);
    }

    private boolean enabled(JsonNode options) {
        if (options != null && options.has("enabled")) {
            return options.path("enabled").asBoolean(true);
        }
        return true;
    }

    private int topK(JsonNode options) {
        if (options != null && options.has("top_k")) {
            int value = options.path("top_k").asInt(DEFAULT_TOP_K);
            return Math.min(Math.max(value, 1), MAX_TOP_K);
        }
        return DEFAULT_TOP_K;
    }

    private String scope(JsonNode options) {
        if (options != null && StringUtils.hasText(options.path("scope").asText())) {
            return options.path("scope").asText();
        }
        return "thread";
    }

    public record RetrievalResult(
            boolean enabled,
            String scope,
            List<RetrievalSnippet> snippets
    ) {
        public int count() {
            return snippets.size();
        }
    }

    public record RetrievalSnippet(
            UUID documentId,
            String sourceName,
            String sourcePath,
            String status,
            String preview,
            double score
    ) {
    }
}
