package com.agentdesk.backend.skill;

import com.agentdesk.backend.bootstrap.BootstrapSeedData;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!dev")
public class InMemorySkillRepository implements SkillRepository {

    private final ObjectMapper objectMapper;
    private final Map<UUID, ProjectState> projects = new LinkedHashMap<>();
    private final Map<UUID, UUID> skillProjects = new LinkedHashMap<>();

    public InMemorySkillRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void ensureSeeded(UUID projectId) {
        projects.computeIfAbsent(projectId, this::seedProject);
    }

    @Override
    public synchronized Optional<UUID> findProjectIdBySkill(UUID skillId) {
        return Optional.ofNullable(skillProjects.get(skillId));
    }

    @Override
    public synchronized List<SkillDtos.SkillItem> listSkills(UUID projectId) {
        ensureSeeded(projectId);
        return projects.get(projectId).skills.values().stream()
                .sorted(Comparator.comparing(SkillState::createdAt))
                .map(this::skillItem)
                .toList();
    }

    @Override
    public synchronized SkillDtos.SkillItem createSkill(UUID projectId, SkillDtos.CreateSkillRequest request) {
        ensureSeeded(projectId);
        ProjectState state = projects.get(projectId);
        String clientKey = StringUtils.hasText(request.clientKey()) ? request.clientKey().trim() : slug(request.name());
        if (state.skills.values().stream().anyMatch(skill -> clientKey.equals(skill.clientKey()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill already exists.");
        }
        SkillState skill = new SkillState(
                UUID.randomUUID(),
                clientKey,
                request.name().trim(),
                request.source().trim(),
                normalizeStatus(request.status(), "待启用"),
                request.scope() == null ? null : request.scope().trim(),
                normalizeMounts(request.mounts()),
                null,
                objectOrEmpty(request.manifest()),
                objectOrEmpty(request.config()),
                Instant.now()
        );
        state.skills.put(skill.id(), skill);
        skillProjects.put(skill.id(), projectId);
        return skillItem(skill);
    }

    @Override
    public synchronized Optional<SkillDtos.SkillItem> findSkill(UUID skillId) {
        UUID projectId = skillProjects.get(skillId);
        if (projectId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(projects.get(projectId).skills.get(skillId)).map(this::skillItem);
    }

    @Override
    public synchronized SkillDtos.SkillItem patchSkill(UUID skillId, SkillDtos.PatchSkillRequest request) {
        UUID projectId = skillProjects.get(skillId);
        ProjectState state = projects.get(projectId);
        SkillState current = state.skills.get(skillId);
        SkillState updated = current.withPatch(
                valueOrCurrent(request.name(), current.name()),
                valueOrCurrent(request.source(), current.source()),
                normalizeStatus(request.status(), current.status()),
                request.scope() == null ? current.scope() : request.scope().trim(),
                request.mounts() == null ? current.mounts() : normalizeMounts(request.mounts()),
                request.manifest() == null ? current.manifest() : objectOrEmpty(request.manifest()),
                mergeConfig(current.config(), request.config()),
                current.lastRunAt()
        );
        state.skills.put(skillId, updated);
        return skillItem(updated);
    }

    @Override
    public synchronized SkillDtos.SkillItem toggleSkill(UUID skillId, String nextStatus) {
        UUID projectId = skillProjects.get(skillId);
        ProjectState state = projects.get(projectId);
        SkillState current = state.skills.get(skillId);
        String status = StringUtils.hasText(nextStatus)
                ? normalizeStatus(nextStatus, current.status())
                : ("启用".equals(current.status()) ? "停用" : "启用");
        SkillState updated = current.withPatch(current.name(), current.source(), status, current.scope(), current.mounts(), current.manifest(), current.config(), current.lastRunAt());
        state.skills.put(skillId, updated);
        return skillItem(updated);
    }

    @Override
    public synchronized SkillDtos.SyncPolicyResponse syncPolicy(UUID projectId) {
        ensureSeeded(projectId);
        ProjectState state = projects.get(projectId);
        Instant now = Instant.now();
        int synced = 0;
        for (SkillState skill : List.copyOf(state.skills.values())) {
            if ("启用".equals(skill.status())) {
                ObjectNode config = skill.config().isObject() ? (ObjectNode) skill.config().deepCopy() : objectMapper.createObjectNode();
                config.put("last_run", "刚刚 / 同步装载策略");
                config.put("policy_synced", true);
                state.skills.put(skill.id(), skill.withPatch(skill.name(), skill.source(), skill.status(), skill.scope(), skill.mounts(), skill.manifest(), config, now));
                synced++;
            }
        }
        return new SkillDtos.SyncPolicyResponse(synced, synced, now, listSkills(projectId));
    }

    private ProjectState seedProject(UUID projectId) {
        ProjectState state = new ProjectState(projectId);
        for (BootstrapSeedData.SeedSkill seed : BootstrapSeedData.skills(objectMapper)) {
            ObjectNode config = seed.config().isObject() ? (ObjectNode) seed.config().deepCopy() : objectMapper.createObjectNode();
            config.put("last_run", seed.lastRun());
            SkillState skill = new SkillState(
                    stableId(projectId, "skill:" + seed.clientKey()),
                    seed.clientKey(),
                    seed.name(),
                    seed.source(),
                    seed.status(),
                    seed.scope(),
                    seed.mounts(),
                    null,
                    seed.manifest(),
                    config,
                    Instant.now()
            );
            state.skills.put(skill.id(), skill);
            skillProjects.put(skill.id(), projectId);
        }
        return state;
    }

    private SkillDtos.SkillItem skillItem(SkillState skill) {
        return new SkillDtos.SkillItem(
                skill.id(),
                skill.clientKey(),
                skill.name(),
                skill.source(),
                skill.status(),
                skill.scope(),
                skill.mounts(),
                skill.lastRunAt(),
                skill.config().path("last_run").asText(null),
                skill.manifest(),
                skill.config(),
                skill.createdAt()
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

    private String valueOrCurrent(String value, String current) {
        return StringUtils.hasText(value) ? value.trim() : current;
    }

    private String slug(String value) {
        return value.trim().toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-").replaceAll("^-|-$", "");
    }

    private UUID stableId(UUID ownerId, String key) {
        return UUID.nameUUIDFromBytes((ownerId + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    private record ProjectState(UUID projectId, Map<UUID, SkillState> skills) {
        ProjectState(UUID projectId) {
            this(projectId, new LinkedHashMap<>());
        }
    }

    private record SkillState(
            UUID id,
            String clientKey,
            String name,
            String source,
            String status,
            String scope,
            List<String> mounts,
            Instant lastRunAt,
            JsonNode manifest,
            JsonNode config,
            Instant createdAt
    ) {
        SkillState withPatch(
                String name,
                String source,
                String status,
                String scope,
                List<String> mounts,
                JsonNode manifest,
                JsonNode config,
                Instant lastRunAt
        ) {
            return new SkillState(id, clientKey, name, source, status, scope, mounts, lastRunAt, manifest, config, createdAt);
        }
    }
}
