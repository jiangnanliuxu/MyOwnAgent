package com.agentdesk.backend.settings;

import com.agentdesk.backend.auth.AuthRepository;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SettingsService {

    private final AuthRepository authRepository;
    private final SettingsRepository settingsRepository;

    public SettingsService(AuthRepository authRepository, SettingsRepository settingsRepository) {
        this.authRepository = authRepository;
        this.settingsRepository = settingsRepository;
    }

    public SettingsDtos.SettingsOverviewResponse overview(AuthenticatedUser user, UUID projectId) {
        assertProjectAccess(user, projectId);
        JsonNode settings = settingsRepository.settings(projectId);
        return new SettingsDtos.SettingsOverviewResponse(
                projectId,
                settings.path("context_compression"),
                settings.path("backup"),
                settings.path("tool_authorization"),
                settingsRepository.taskQueue(projectId),
                settingsRepository.taskLogs(projectId, new SettingsDtos.TaskLogQuery(null, null, 10))
        );
    }

    public SettingsDtos.SettingsOverviewResponse patch(
            AuthenticatedUser user,
            UUID projectId,
            SettingsDtos.PatchSettingsRequest request
    ) {
        assertProjectAccess(user, projectId);
        settingsRepository.patchSettings(projectId, request);
        return overview(user, projectId);
    }

    public SettingsDtos.TaskLogListResponse taskLogs(
            AuthenticatedUser user,
            UUID projectId,
            SettingsDtos.TaskLogQuery query
    ) {
        assertProjectAccess(user, projectId);
        return new SettingsDtos.TaskLogListResponse(settingsRepository.taskLogs(projectId, query), query.normalizedLimit());
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
