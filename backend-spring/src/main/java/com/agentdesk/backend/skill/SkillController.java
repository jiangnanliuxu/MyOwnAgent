package com.agentdesk.backend.skill;

import com.agentdesk.backend.common.api.ApiResponse;
import com.agentdesk.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping("/projects/{projectId}/skills")
    public ApiResponse<SkillDtos.SkillListResponse> listSkills(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId
    ) {
        return ApiResponse.success(skillService.listSkills(user, projectId));
    }

    @PostMapping("/projects/{projectId}/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SkillDtos.SkillResponse> createSkill(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody SkillDtos.CreateSkillRequest request
    ) {
        return ApiResponse.success(skillService.createSkill(user, projectId, request));
    }

    @PostMapping("/projects/{projectId}/skills/sync-policy")
    public ApiResponse<SkillDtos.SyncPolicyResponse> syncPolicy(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId
    ) {
        return ApiResponse.success(skillService.syncPolicy(user, projectId));
    }

    @GetMapping("/skills/{skillId}")
    public ApiResponse<SkillDtos.SkillResponse> getSkill(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID skillId
    ) {
        return ApiResponse.success(skillService.getSkill(user, skillId));
    }

    @PatchMapping("/skills/{skillId}")
    public ApiResponse<SkillDtos.SkillResponse> patchSkill(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID skillId,
            @Valid @RequestBody SkillDtos.PatchSkillRequest request
    ) {
        return ApiResponse.success(skillService.patchSkill(user, skillId, request));
    }

    @PostMapping("/skills/{skillId}/toggle")
    public ApiResponse<SkillDtos.SkillResponse> toggleSkill(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID skillId,
            @RequestBody(required = false) SkillDtos.ToggleSkillRequest request
    ) {
        return ApiResponse.success(skillService.toggleSkill(user, skillId, request));
    }
}
