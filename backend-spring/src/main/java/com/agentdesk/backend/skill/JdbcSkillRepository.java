package com.agentdesk.backend.skill;

import com.agentdesk.backend.bootstrap.BootstrapRepository;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("dev")
public class JdbcSkillRepository implements SkillRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final BootstrapRepository bootstrapRepository;

    public JdbcSkillRepository(JdbcClient jdbcClient, ObjectMapper objectMapper, BootstrapRepository bootstrapRepository) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.bootstrapRepository = bootstrapRepository;
    }

    @Override
    public void ensureSeeded(UUID projectId) {
        bootstrapRepository.ensureSeeded(projectId);
    }

    @Override
    public Optional<UUID> findProjectIdBySkill(UUID skillId) {
        return jdbcClient.sql("SELECT project_id FROM skills WHERE id = :skill_id")
                .param("skill_id", skillId)
                .query(UUID.class)
                .optional();
    }

    @Override
    public List<SkillDtos.SkillItem> listSkills(UUID projectId) {
        return jdbcClient.sql(skillSelect() + " WHERE project_id = :project_id ORDER BY created_at")
                .param("project_id", projectId)
                .query(this::skillItem)
                .list();
    }

    @Override
    @Transactional
    public SkillDtos.SkillItem createSkill(UUID projectId, SkillDtos.CreateSkillRequest request) {
        String clientKey = StringUtils.hasText(request.clientKey())
                ? request.clientKey().trim()
                : slug(request.name());
        try {
            UUID id = jdbcClient.sql("""
                            INSERT INTO skills (project_id, client_key, name, source, status, scope, mounts, manifest, config)
                            VALUES (:project_id, :client_key, :name, :source, :status, :scope, :mounts, CAST(:manifest AS jsonb), CAST(:config AS jsonb))
                            RETURNING id
                            """)
                    .param("project_id", projectId)
                    .param("client_key", clientKey)
                    .param("name", request.name().trim())
                    .param("source", request.source().trim())
                    .param("status", normalizeStatus(request.status(), "待启用"))
                    .param("scope", request.scope() == null ? null : request.scope().trim())
                    .param("mounts", normalizeMounts(request.mounts()).toArray(String[]::new))
                    .param("manifest", objectOrEmpty(request.manifest()).toString())
                    .param("config", objectOrEmpty(request.config()).toString())
                    .query(UUID.class)
                    .single();
            return findSkill(id).orElseThrow();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill already exists.");
        }
    }

    @Override
    public Optional<SkillDtos.SkillItem> findSkill(UUID skillId) {
        return jdbcClient.sql(skillSelect() + " WHERE id = :skill_id")
                .param("skill_id", skillId)
                .query(this::skillItem)
                .optional();
    }

    @Override
    @Transactional
    public SkillDtos.SkillItem patchSkill(UUID skillId, SkillDtos.PatchSkillRequest request) {
        SkillDtos.SkillItem current = findSkill(skillId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Skill is not available."));
        JsonNode nextManifest = request.manifest() == null ? current.manifest() : objectOrEmpty(request.manifest());
        JsonNode nextConfig = mergeConfig(current.config(), request.config());
        jdbcClient.sql("""
                        UPDATE skills
                        SET name = :name,
                            source = :source,
                            status = :status,
                            scope = :scope,
                            mounts = :mounts,
                            manifest = CAST(:manifest AS jsonb),
                            config = CAST(:config AS jsonb)
                        WHERE id = :skill_id
                        """)
                .param("skill_id", skillId)
                .param("name", valueOrCurrent(request.name(), current.name()))
                .param("source", valueOrCurrent(request.source(), current.source()))
                .param("status", normalizeStatus(request.status(), current.status()))
                .param("scope", request.scope() == null ? current.scope() : request.scope().trim())
                .param("mounts", (request.mounts() == null ? current.mounts() : normalizeMounts(request.mounts())).toArray(String[]::new))
                .param("manifest", nextManifest.toString())
                .param("config", nextConfig.toString())
                .update();
        return findSkill(skillId).orElseThrow();
    }

    @Override
    @Transactional
    public SkillDtos.SkillItem toggleSkill(UUID skillId, String nextStatus) {
        SkillDtos.SkillItem current = findSkill(skillId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Skill is not available."));
        String status = StringUtils.hasText(nextStatus)
                ? normalizeStatus(nextStatus, current.status())
                : ("启用".equals(current.status()) ? "停用" : "启用");
        jdbcClient.sql("UPDATE skills SET status = :status WHERE id = :skill_id")
                .param("skill_id", skillId)
                .param("status", status)
                .update();
        return findSkill(skillId).orElseThrow();
    }

    @Override
    @Transactional
    public SkillDtos.SyncPolicyResponse syncPolicy(UUID projectId) {
        Instant now = Instant.now();
        ObjectNode patch = objectMapper.createObjectNode();
        patch.put("last_run", "刚刚 / 同步装载策略");
        patch.put("policy_synced", true);
        jdbcClient.sql("""
                        UPDATE skills
                        SET last_run_at = :last_run_at,
                            config = config || CAST(:config AS jsonb)
                        WHERE project_id = :project_id AND status = '启用'
                        """)
                .param("project_id", projectId)
                .param("last_run_at", Timestamp.from(now))
                .param("config", patch.toString())
                .update();
        List<SkillDtos.SkillItem> items = listSkills(projectId);
        int enabled = (int) items.stream().filter(skill -> "启用".equals(skill.status())).count();
        return new SkillDtos.SyncPolicyResponse(enabled, enabled, now, items);
    }

    private String skillSelect() {
        return """
                SELECT id, client_key, name, source, status, scope, mounts, last_run_at,
                       manifest::text AS manifest, config::text AS config, created_at
                FROM skills
                """;
    }

    private SkillDtos.SkillItem skillItem(ResultSet rs, int rowNum) throws SQLException {
        JsonNode config = readJson(rs.getString("config"));
        Timestamp lastRunAt = rs.getTimestamp("last_run_at");
        return new SkillDtos.SkillItem(
                rs.getObject("id", UUID.class),
                rs.getString("client_key"),
                rs.getString("name"),
                rs.getString("source"),
                rs.getString("status"),
                rs.getString("scope"),
                textArray(rs.getArray("mounts")),
                lastRunAt == null ? null : lastRunAt.toInstant(),
                config.path("last_run").asText(null),
                readJson(rs.getString("manifest")),
                config,
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private JsonNode mergeConfig(JsonNode current, JsonNode patch) {
        ObjectNode merged = current != null && current.isObject()
                ? (ObjectNode) current.deepCopy()
                : objectMapper.createObjectNode();
        if (patch == null || patch.isNull()) {
            return merged;
        }
        if (!patch.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "config must be a JSON object.");
        }
        patch.fields().forEachRemaining(entry -> merged.set(entry.getKey(), entry.getValue()));
        return merged;
    }

    private JsonNode objectOrEmpty(JsonNode value) {
        if (value == null || value.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!value.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON value must be an object.");
        }
        return value;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid skill JSON value.", exception);
        }
    }

    private List<String> normalizeMounts(List<String> mounts) {
        if (mounts == null || mounts.isEmpty()) {
            return List.of("任务层");
        }
        return mounts.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    private String normalizeStatus(String status, String fallback) {
        String value = StringUtils.hasText(status) ? status.trim() : fallback;
        if (!List.of("启用", "停用", "待启用").contains(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status must be 启用, 停用, or 待启用.");
        }
        return value;
    }

    private List<String> textArray(Array array) {
        if (array == null) {
            return List.of();
        }
        try {
            return Arrays.asList((String[]) array.getArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid text array value.", exception);
        }
    }

    private String valueOrCurrent(String value, String current) {
        return StringUtils.hasText(value) ? value.trim() : current;
    }

    private String slug(String value) {
        return value.trim().toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-").replaceAll("^-|-$", "");
    }
}
