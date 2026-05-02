package com.agentdesk.backend.settings;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

interface SettingsRepository {

    JsonNode settings(UUID projectId);

    JsonNode patchSettings(UUID projectId, SettingsDtos.PatchSettingsRequest request);

    SettingsDtos.TaskQueueSummary taskQueue(UUID projectId);

    List<SettingsDtos.TaskLogItem> taskLogs(UUID projectId, SettingsDtos.TaskLogQuery query);
}
