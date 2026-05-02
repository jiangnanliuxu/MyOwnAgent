package com.agentdesk.backend.rag;

import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("dev")
class RedisRagIndexJobPublisher implements RagIndexJobPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    RedisRagIndexJobPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(RagDtos.RagIndexJobPayload payload) {
        try {
            redisTemplate.opsForStream().add(STREAM_KEY, Map.of(
                    "job_id", payload.jobId().toString(),
                    "document_id", payload.documentId().toString(),
                    "thread_id", payload.threadId().toString(),
                    "payload", objectMapper.writeValueAsString(payload)
            ));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to publish RAG index job.");
        }
    }
}
