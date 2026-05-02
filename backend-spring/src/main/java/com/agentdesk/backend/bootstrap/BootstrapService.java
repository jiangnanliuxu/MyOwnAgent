package com.agentdesk.backend.bootstrap;

import com.agentdesk.backend.auth.AuthRepository;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.security.AuthenticatedUser;
import com.agentdesk.backend.user.UserPreferences;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class BootstrapService {

    private static final String DEFAULT_THREAD_KEY = "session-review";

    private final AuthRepository authRepository;
    private final BootstrapRepository bootstrapRepository;

    public BootstrapService(
            AuthRepository authRepository,
            BootstrapRepository bootstrapRepository
    ) {
        this.authRepository = authRepository;
        this.bootstrapRepository = bootstrapRepository;
    }

    public BootstrapResponse getBootstrap(AuthenticatedUser authenticatedUser, UUID projectId, String queryThreadKey) {
        if (authenticatedUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        if (!authRepository.projectBelongsToUser(authenticatedUser.id(), projectId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Project is not available.");
        }

        bootstrapRepository.ensureSeeded(projectId);
        String activeThreadKey = resolveActiveThreadKey(authenticatedUser.id(), projectId, queryThreadKey);
        UserPreferences preferences = authRepository.getOrCreatePreferences(authenticatedUser.id());
        return bootstrapRepository.load(projectId, activeThreadKey, preferences.preferences());
    }

    private String resolveActiveThreadKey(UUID userId, UUID projectId, String queryThreadKey) {
        if (StringUtils.hasText(queryThreadKey)
                && bootstrapRepository.threadKeyBelongsToProject(projectId, queryThreadKey.trim())) {
            UserPreferences current = authRepository.getOrCreatePreferences(userId);
            authRepository.updatePreferences(userId, queryThreadKey.trim(), current.preferences());
            return queryThreadKey.trim();
        }

        UserPreferences preferences = authRepository.getOrCreatePreferences(userId);
        String storedThreadKey = preferences.activeThreadKey();
        if (StringUtils.hasText(storedThreadKey)
                && bootstrapRepository.threadKeyBelongsToProject(projectId, storedThreadKey)) {
            return storedThreadKey;
        }

        if (bootstrapRepository.threadKeyBelongsToProject(projectId, DEFAULT_THREAD_KEY)) {
            authRepository.updatePreferences(userId, DEFAULT_THREAD_KEY, preferences.preferences());
            return DEFAULT_THREAD_KEY;
        }

        return bootstrapRepository.load(projectId, DEFAULT_THREAD_KEY, preferences.preferences())
                .threads()
                .keySet()
                .stream()
                .findFirst()
                .orElse(DEFAULT_THREAD_KEY);
    }
}
