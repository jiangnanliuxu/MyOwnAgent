package com.agentdesk.backend.skill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SkillRepository {

    void ensureSeeded(UUID projectId);

    Optional<UUID> findProjectIdBySkill(UUID skillId);

    List<SkillDtos.SkillItem> listSkills(UUID projectId);

    SkillDtos.SkillItem createSkill(UUID projectId, SkillDtos.CreateSkillRequest request);

    Optional<SkillDtos.SkillItem> findSkill(UUID skillId);

    SkillDtos.SkillItem patchSkill(UUID skillId, SkillDtos.PatchSkillRequest request);

    SkillDtos.SkillItem toggleSkill(UUID skillId, String nextStatus);

    SkillDtos.SyncPolicyResponse syncPolicy(UUID projectId);
}
