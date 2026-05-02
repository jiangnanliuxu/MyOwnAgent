package com.agentdesk.backend.auth;

import com.agentdesk.backend.user.UserPreferences;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthRepository {

    Optional<UserAccount> findUserById(UUID userId);

    Optional<UserAccountWithPassword> findUserByEmailWithPassword(String email);

    boolean userEmailExists(String email);

    RegisteredUser createUserWithDefaults(String email, String name, String passwordHash);

    Optional<UUID> findDefaultProjectId(UUID userId);

    boolean projectBelongsToUser(UUID userId, UUID projectId);

    void saveRefreshToken(UUID userId, String tokenHash, Instant expiresAt);

    Optional<StoredRefreshToken> findValidRefreshToken(String tokenHash, Instant now);

    void revokeRefreshToken(UUID tokenId, Instant now);

    UserPreferences getOrCreatePreferences(UUID userId);

    UserPreferences updatePreferences(UUID userId, String activeThreadKey, JsonNode preferences);

    boolean userHasThreads(UUID userId);

    boolean threadKeyBelongsToUser(UUID userId, String threadKey);
}
