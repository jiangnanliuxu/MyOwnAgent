package com.agentdesk.backend.settings;

import com.agentdesk.backend.common.api.ApiResponse;
import com.agentdesk.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
class SettingsController {

    private final SettingsService settingsService;

    SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/api/v1/projects/{projectId}/settings/overview")
    ApiResponse<SettingsDtos.SettingsOverviewResponse> overview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId
    ) {
        return ApiResponse.success(settingsService.overview(user, projectId));
    }

    @PatchMapping("/api/v1/projects/{projectId}/settings")
    ApiResponse<SettingsDtos.SettingsOverviewResponse> patch(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody SettingsDtos.PatchSettingsRequest request
    ) {
        return ApiResponse.success(settingsService.patch(user, projectId, request));
    }

    @GetMapping("/api/v1/projects/{projectId}/task-logs")
    ApiResponse<SettingsDtos.TaskLogListResponse> taskLogs(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return ApiResponse.success(settingsService.taskLogs(user, projectId, new SettingsDtos.TaskLogQuery(type, level, limit)));
    }
}
