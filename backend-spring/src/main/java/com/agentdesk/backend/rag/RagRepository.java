package com.agentdesk.backend.rag;

import java.util.List;
import java.util.UUID;

interface RagRepository {

    RagUploadRecord createUpload(RagDtos.CreateDocumentCommand command);

    List<RagDtos.DocumentItem> listThreadDocuments(UUID threadId, int limit);

    record RagUploadRecord(
            RagDtos.DocumentItem document,
            RagDtos.IndexJobItem job
    ) {
    }
}
