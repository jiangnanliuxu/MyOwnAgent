package com.agentdesk.backend.rag;

import java.io.IOException;
import java.util.UUID;

interface RagDocumentStorage {

    StoredObject store(StoreCommand command) throws IOException;

    record StoreCommand(
            UUID projectId,
            UUID threadId,
            UUID documentId,
            String sourceName,
            String mimeType,
            byte[] content
    ) {
    }

    record StoredObject(String storageUri) {
    }
}
