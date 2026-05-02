package com.agentdesk.backend.rag;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!dev")
class InMemoryRagDocumentStorage implements RagDocumentStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public StoredObject store(StoreCommand command) {
        String objectKey = objectKey(command);
        objects.put(objectKey, command.content().clone());
        return new StoredObject("memory://" + objectKey);
    }

    private String objectKey(StoreCommand command) {
        return "rag/%s/%s/%s/%s".formatted(
                command.projectId(),
                command.threadId(),
                command.documentId(),
                command.sourceName()
        );
    }
}
