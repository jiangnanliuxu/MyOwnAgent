package com.agentdesk.backend.bootstrap;

import java.util.Optional;
import java.util.UUID;

public interface BootstrapRepository {

    void ensureSeeded(UUID projectId);

    Optional<BootstrapResponse.ProjectView> findProject(UUID projectId);

    BootstrapResponse load(UUID projectId, String activeThreadKey, com.fasterxml.jackson.databind.JsonNode preferences);

    boolean threadKeyBelongsToProject(UUID projectId, String threadKey);
}
