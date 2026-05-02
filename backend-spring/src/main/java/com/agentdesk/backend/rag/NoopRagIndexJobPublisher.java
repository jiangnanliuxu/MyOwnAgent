package com.agentdesk.backend.rag;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
class NoopRagIndexJobPublisher implements RagIndexJobPublisher {

    @Override
    public void publish(RagDtos.RagIndexJobPayload payload) {
        // Default profile has no Redis infrastructure; repository state is enough for tests.
    }
}
