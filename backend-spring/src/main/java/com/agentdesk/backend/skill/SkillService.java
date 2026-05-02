package com.agentdesk.backend.skill;

import com.agentdesk.backend.auth.AuthRepository;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SkillService {

    private final AuthRepository authRepository;
    private final SkillRepository skillRepository;

    public SkillService(AuthRepository authRepository, SkillRepository skillRepository) {
        this.authRepository = authRepository;
        this.skillRepository = skillRepository;
    }

    public SkillDtos.SkillListResponse listSkills(AuthenticatedUser user, UUID projectId) {
        assertProjectAccess(user, projectId);
        skillRepository.ensureSeeded(projectId);
        return new SkillDtos.SkillListResponse(skillRepository.listSkills(projectId));
    }

    public SkillDtos.SkillResponse createSkill(
            AuthenticatedUser user,
            UUID projectId,
            SkillDtos.CreateSkillRequest request
    ) {
        assertProjectAccess(user, projectId);
        skillRepository.ensureSeeded(projectId);
        return new SkillDtos.SkillResponse(skillRepository.createSkill(projectId, request));
    }

    public SkillDtos.SkillResponse getSkill(AuthenticatedUser user, UUID skillId) {
        UUID projectId = projectIdForSkill(skillId);
        assertProjectAccess(user, projectId);
        return new SkillDtos.SkillResponse(skillRepository.findSkill(skillId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Skill is not available.")));
    }

    public SkillDtos.SkillResponse patchSkill(
            AuthenticatedUser user,
            UUID skillId,
            SkillDtos.PatchSkillRequest request
    ) {
        UUID projectId = projectIdForSkill(skillId);
        assertProjectAccess(user, projectId);
        return new SkillDtos.SkillResponse(skillRepository.patchSkill(skillId, request));
    }

    public SkillDtos.SkillResponse toggleSkill(
            AuthenticatedUser user,
            UUID skillId,
            SkillDtos.ToggleSkillRequest request
    ) {
        UUID projectId = projectIdForSkill(skillId);
        assertProjectAccess(user, projectId);
        return new SkillDtos.SkillResponse(skillRepository.toggleSkill(
                skillId,
                request == null ? null : request.nextStatus()
        ));
    }

    public SkillDtos.SyncPolicyResponse syncPolicy(AuthenticatedUser user, UUID projectId) {
        assertProjectAccess(user, projectId);
        skillRepository.ensureSeeded(projectId);
        return skillRepository.syncPolicy(projectId);
    }

    private UUID projectIdForSkill(UUID skillId) {
        return skillRepository.findProjectIdBySkill(skillId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Skill is not available."));
    }

    private void assertProjectAccess(AuthenticatedUser user, UUID projectId) {
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        if (!authRepository.projectBelongsToUser(user.id(), projectId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Project is not available.");
        }
    }
}
