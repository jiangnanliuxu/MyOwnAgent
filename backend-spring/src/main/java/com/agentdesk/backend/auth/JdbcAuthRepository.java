package com.agentdesk.backend.auth;

import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.user.UserPreferences;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("dev")
public class JdbcAuthRepository implements AuthRepository {

    private static final String DEFAULT_THREAD_KEY = "session-review";

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcAuthRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<UserAccount> findUserById(UUID userId) {
        return jdbcClient.sql("""
                        SELECT id, email, name, avatar_url, status
                        FROM users
                        WHERE id = :user_id AND status = 'active'
                        """)
                .param("user_id", userId)
                .query((rs, rowNum) -> new UserAccount(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("name"),
                        rs.getString("avatar_url"),
                        rs.getString("status")
                ))
                .optional();
    }

    @Override
    public Optional<UserAccountWithPassword> findUserByEmailWithPassword(String email) {
        return jdbcClient.sql("""
                        SELECT u.id, u.email, u.name, u.avatar_url, u.status, c.password_hash
                        FROM users u
                        JOIN user_auth_credentials c ON c.user_id = u.id
                        WHERE u.email = :email AND u.status = 'active'
                        """)
                .param("email", email)
                .query((rs, rowNum) -> new UserAccountWithPassword(
                        new UserAccount(
                                rs.getObject("id", UUID.class),
                                rs.getString("email"),
                                rs.getString("name"),
                                rs.getString("avatar_url"),
                                rs.getString("status")
                        ),
                        rs.getString("password_hash")
                ))
                .optional();
    }

    @Override
    public boolean userEmailExists(String email) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM users WHERE email = :email")
                .param("email", email)
                .query(Integer.class)
                .single();
        return count > 0;
    }

    @Override
    @Transactional
    public RegisteredUser createUserWithDefaults(String email, String name, String passwordHash) {
        try {
            UserAccount user = jdbcClient.sql("""
                            INSERT INTO users (email, name)
                            VALUES (:email, :name)
                            RETURNING id, email, name, avatar_url, status
                            """)
                    .param("email", email)
                    .param("name", name)
                    .query((rs, rowNum) -> new UserAccount(
                            rs.getObject("id", UUID.class),
                            rs.getString("email"),
                            rs.getString("name"),
                            rs.getString("avatar_url"),
                            rs.getString("status")
                    ))
                    .single();

            jdbcClient.sql("""
                            INSERT INTO user_auth_credentials (user_id, password_hash)
                            VALUES (:user_id, :password_hash)
                            """)
                    .param("user_id", user.id())
                    .param("password_hash", passwordHash)
                    .update();

            UUID projectId = jdbcClient.sql("""
                            INSERT INTO projects (user_id)
                            VALUES (:user_id)
                            RETURNING id
                            """)
                    .param("user_id", user.id())
                    .query(UUID.class)
                    .single();

            jdbcClient.sql("""
                            INSERT INTO user_configs (user_id, active_thread_key, preferences)
                            VALUES (:user_id, :active_thread_key, CAST(:preferences AS jsonb))
                            """)
                    .param("user_id", user.id())
                    .param("active_thread_key", DEFAULT_THREAD_KEY)
                    .param("preferences", "{}")
                    .update();

            return new RegisteredUser(user, projectId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email is already registered.");
        }
    }

    @Override
    public Optional<UUID> findDefaultProjectId(UUID userId) {
        return jdbcClient.sql("""
                        SELECT id
                        FROM projects
                        WHERE user_id = :user_id AND status = 'active'
                        ORDER BY created_at ASC
                        LIMIT 1
                        """)
                .param("user_id", userId)
                .query(UUID.class)
                .optional();
    }

    @Override
    public boolean projectBelongsToUser(UUID userId, UUID projectId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM projects
                        WHERE id = :project_id
                          AND user_id = :user_id
                          AND status = 'active'
                        """)
                .param("project_id", projectId)
                .param("user_id", userId)
                .query(Integer.class)
                .single();
        return count > 0;
    }

    @Override
    public void saveRefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        jdbcClient.sql("""
                        INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
                        VALUES (:user_id, :token_hash, :expires_at)
                        """)
                .param("user_id", userId)
                .param("token_hash", tokenHash)
                .param("expires_at", Timestamp.from(expiresAt))
                .update();
    }

    @Override
    public Optional<StoredRefreshToken> findValidRefreshToken(String tokenHash, Instant now) {
        return jdbcClient.sql("""
                        SELECT id, user_id, expires_at
                        FROM refresh_tokens
                        WHERE token_hash = :token_hash
                          AND revoked_at IS NULL
                          AND expires_at > :now
                        """)
                .param("token_hash", tokenHash)
                .param("now", Timestamp.from(now))
                .query((rs, rowNum) -> new StoredRefreshToken(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getTimestamp("expires_at").toInstant()
                ))
                .optional();
    }

    @Override
    public void revokeRefreshToken(UUID tokenId, Instant now) {
        jdbcClient.sql("""
                        UPDATE refresh_tokens
                        SET revoked_at = :revoked_at
                        WHERE id = :id AND revoked_at IS NULL
                        """)
                .param("id", tokenId)
                .param("revoked_at", Timestamp.from(now))
                .update();
    }

    @Override
    public UserPreferences getOrCreatePreferences(UUID userId) {
        Optional<UserPreferences> existing = jdbcClient.sql("""
                        SELECT user_id, active_thread_key, preferences::text AS preferences
                        FROM user_configs
                        WHERE user_id = :user_id
                        """)
                .param("user_id", userId)
                .query((rs, rowNum) -> new UserPreferences(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("active_thread_key"),
                        readJson(rs.getString("preferences"))
                ))
                .optional();

        return existing.orElseGet(() -> {
            jdbcClient.sql("""
                            INSERT INTO user_configs (user_id, active_thread_key, preferences)
                            VALUES (:user_id, :active_thread_key, CAST(:preferences AS jsonb))
                            """)
                    .param("user_id", userId)
                    .param("active_thread_key", DEFAULT_THREAD_KEY)
                    .param("preferences", "{}")
                    .update();
            return new UserPreferences(userId, DEFAULT_THREAD_KEY, objectMapper.createObjectNode());
        });
    }

    @Override
    public UserPreferences updatePreferences(UUID userId, String activeThreadKey, JsonNode preferences) {
        jdbcClient.sql("""
                        INSERT INTO user_configs (user_id, active_thread_key, preferences)
                        VALUES (:user_id, :active_thread_key, CAST(:preferences AS jsonb))
                        ON CONFLICT (user_id) DO UPDATE
                        SET active_thread_key = EXCLUDED.active_thread_key,
                            preferences = EXCLUDED.preferences
                        """)
                .param("user_id", userId)
                .param("active_thread_key", activeThreadKey)
                .param("preferences", preferences.toString())
                .update();

        return new UserPreferences(userId, activeThreadKey, preferences.deepCopy());
    }

    @Override
    public boolean userHasThreads(UUID userId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM threads t
                        JOIN projects p ON p.id = t.project_id
                        WHERE p.user_id = :user_id
                          AND t.status = 'active'
                        """)
                .param("user_id", userId)
                .query(Integer.class)
                .single();
        return count > 0;
    }

    @Override
    public boolean threadKeyBelongsToUser(UUID userId, String threadKey) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM threads t
                        JOIN projects p ON p.id = t.project_id
                        WHERE p.user_id = :user_id
                          AND t.client_key = :thread_key
                          AND t.status = 'active'
                        """)
                .param("user_id", userId)
                .param("thread_key", threadKey)
                .query(Integer.class)
                .single();
        return count > 0;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid JSON stored in user_configs.preferences.", exception);
        }
    }
}
