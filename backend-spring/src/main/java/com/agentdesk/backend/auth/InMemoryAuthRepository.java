package com.agentdesk.backend.auth;

import com.agentdesk.backend.user.UserPreferences;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!dev")
public class InMemoryAuthRepository implements AuthRepository {

    private static final String DEFAULT_THREAD_KEY = "session-review";

    private final ObjectMapper objectMapper;
    private final Map<UUID, UserAccount> usersById = new HashMap<>();
    private final Map<String, UUID> userIdsByEmail = new HashMap<>();
    private final Map<UUID, String> passwordHashesByUserId = new HashMap<>();
    private final Map<UUID, UUID> defaultProjectIdsByUserId = new HashMap<>();
    private final Map<UUID, UserPreferences> preferencesByUserId = new HashMap<>();
    private final Map<String, StoredRefreshToken> refreshTokensByHash = new HashMap<>();
    private final Map<UUID, String> refreshTokenHashesById = new HashMap<>();
    private final Map<UUID, Map<String, Boolean>> threadKeysByUserId = new HashMap<>();

    public InMemoryAuthRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized Optional<UserAccount> findUserById(UUID userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    @Override
    public synchronized Optional<UserAccountWithPassword> findUserByEmailWithPassword(String email) {
        UUID userId = userIdsByEmail.get(email);
        if (userId == null) {
            return Optional.empty();
        }
        UserAccount user = usersById.get(userId);
        String passwordHash = passwordHashesByUserId.get(userId);
        if (user == null || passwordHash == null) {
            return Optional.empty();
        }
        return Optional.of(new UserAccountWithPassword(user, passwordHash));
    }

    @Override
    public synchronized boolean userEmailExists(String email) {
        return userIdsByEmail.containsKey(email);
    }

    @Override
    public synchronized RegisteredUser createUserWithDefaults(String email, String name, String passwordHash) {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UserAccount user = new UserAccount(userId, email, name, null, "active");

        usersById.put(userId, user);
        userIdsByEmail.put(email, userId);
        passwordHashesByUserId.put(userId, passwordHash);
        defaultProjectIdsByUserId.put(userId, projectId);
        preferencesByUserId.put(userId, new UserPreferences(userId, DEFAULT_THREAD_KEY, objectMapper.createObjectNode()));
        return new RegisteredUser(user, projectId);
    }

    @Override
    public synchronized Optional<UUID> findDefaultProjectId(UUID userId) {
        return Optional.ofNullable(defaultProjectIdsByUserId.get(userId));
    }

    @Override
    public synchronized boolean projectBelongsToUser(UUID userId, UUID projectId) {
        return projectId != null && projectId.equals(defaultProjectIdsByUserId.get(userId));
    }

    @Override
    public synchronized void saveRefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        StoredRefreshToken refreshToken = new StoredRefreshToken(UUID.randomUUID(), userId, expiresAt);
        refreshTokensByHash.put(tokenHash, refreshToken);
        refreshTokenHashesById.put(refreshToken.id(), tokenHash);
    }

    @Override
    public synchronized Optional<StoredRefreshToken> findValidRefreshToken(String tokenHash, Instant now) {
        StoredRefreshToken refreshToken = refreshTokensByHash.get(tokenHash);
        if (refreshToken == null || !refreshToken.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(refreshToken);
    }

    @Override
    public synchronized void revokeRefreshToken(UUID tokenId, Instant now) {
        String tokenHash = refreshTokenHashesById.remove(tokenId);
        if (tokenHash != null) {
            refreshTokensByHash.remove(tokenHash);
        }
    }

    @Override
    public synchronized UserPreferences getOrCreatePreferences(UUID userId) {
        return preferencesByUserId.computeIfAbsent(
                userId,
                id -> new UserPreferences(id, DEFAULT_THREAD_KEY, objectMapper.createObjectNode())
        );
    }

    @Override
    public synchronized UserPreferences updatePreferences(UUID userId, String activeThreadKey, JsonNode preferences) {
        UserPreferences current = getOrCreatePreferences(userId);
        JsonNode nextPreferences = preferences == null ? current.preferences() : preferences.deepCopy();
        UserPreferences updated = new UserPreferences(userId, activeThreadKey, nextPreferences);
        preferencesByUserId.put(userId, updated);
        return updated;
    }

    @Override
    public synchronized boolean userHasThreads(UUID userId) {
        return !threadKeysByUserId.getOrDefault(userId, Map.of()).isEmpty();
    }

    @Override
    public synchronized boolean threadKeyBelongsToUser(UUID userId, String threadKey) {
        return threadKeysByUserId.getOrDefault(userId, Map.of()).containsKey(threadKey);
    }
}
