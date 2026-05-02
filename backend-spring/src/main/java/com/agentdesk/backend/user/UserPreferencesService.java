package com.agentdesk.backend.user;

import com.agentdesk.backend.auth.AuthRepository;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class UserPreferencesService {

    private static final String DEFAULT_THREAD_KEY = "session-review";

    private final AuthRepository authRepository;
    private final ObjectMapper objectMapper;

    public UserPreferencesService(AuthRepository authRepository, ObjectMapper objectMapper) {
        this.authRepository = authRepository;
        this.objectMapper = objectMapper;
    }

    public UserPreferencesResponse getPreferences(UUID userId) {
        UserPreferences preferences = authRepository.getOrCreatePreferences(userId);
        String activeThreadKey = normalizeStoredActiveThreadKey(preferences.activeThreadKey());
        return UserPreferencesResponse.from(new UserPreferences(userId, activeThreadKey, preferences.preferences()));
    }

    public UserPreferencesResponse patchPreferences(UUID userId, JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Preferences patch body must be a JSON object.");
        }

        UserPreferences current = authRepository.getOrCreatePreferences(userId);
        String activeThreadKey = normalizeStoredActiveThreadKey(current.activeThreadKey());
        JsonNode preferences = current.preferences() == null
                ? objectMapper.createObjectNode()
                : current.preferences().deepCopy();

        if (request.has("active_thread_key")) {
            activeThreadKey = resolveActiveThreadKey(userId, request.get("active_thread_key"));
        }

        if (request.has("preferences")) {
            JsonNode requestedPreferences = request.get("preferences");
            if (requestedPreferences == null || !requestedPreferences.isObject()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "preferences must be a JSON object.");
            }
            preferences = mergePreferences(preferences, requestedPreferences);
        }

        UserPreferences updated = authRepository.updatePreferences(userId, activeThreadKey, preferences);
        return UserPreferencesResponse.from(updated);
    }

    private String resolveActiveThreadKey(UUID userId, JsonNode requestedThreadKey) {
        if (requestedThreadKey == null || requestedThreadKey.isNull()) {
            return DEFAULT_THREAD_KEY;
        }
        if (!requestedThreadKey.isTextual()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "active_thread_key must be a string.");
        }

        String candidate = requestedThreadKey.asText().trim();
        if (!StringUtils.hasText(candidate) || DEFAULT_THREAD_KEY.equals(candidate)) {
            return DEFAULT_THREAD_KEY;
        }

        if (!authRepository.userHasThreads(userId)) {
            return DEFAULT_THREAD_KEY;
        }

        if (authRepository.threadKeyBelongsToUser(userId, candidate)) {
            return candidate;
        }

        return DEFAULT_THREAD_KEY;
    }

    private String normalizeStoredActiveThreadKey(String activeThreadKey) {
        if (!StringUtils.hasText(activeThreadKey)) {
            return DEFAULT_THREAD_KEY;
        }
        return activeThreadKey;
    }

    private JsonNode mergePreferences(JsonNode currentPreferences, JsonNode requestedPreferences) {
        ObjectNode merged = currentPreferences != null && currentPreferences.isObject()
                ? (ObjectNode) currentPreferences.deepCopy()
                : objectMapper.createObjectNode();
        requestedPreferences.properties().forEach(entry -> merged.set(entry.getKey(), entry.getValue()));
        return merged;
    }
}
