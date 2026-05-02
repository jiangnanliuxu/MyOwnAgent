package com.agentdesk.backend.rag;

interface RagIndexJobPublisher {

    String STREAM_KEY = "rag.index.jobs";

    void publish(RagDtos.RagIndexJobPayload payload);
}
