package com.agentdesk.backend.role;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleListResponse(List<RoleItem> items) {
    }

    public record RoleResponse(RoleItem role) {
    }

    public record RoleUpdateResponse(
            RoleItem role,
            List<SyncedThread> syncedThreads
    ) {
    }

    public record SyncRolesResponse(
            UUID folderId,
            String folderName,
            List<ThreadRoleItem> threadRoles,
            int created,
            int synced
    ) {
    }

    public record PatchRoleRequest(
            @Size(max = 160) String name,
            @Size(max = 160) String alias,
            @Size(max = 80) String tag,
            @Size(max = 1200) String description,
            @Size(max = 500) String shortDescription,
            String status,
            JsonNode config
    ) {
    }

    public record RoleItem(
            UUID id,
            String clientKey,
            String name,
            String alias,
            String tag,
            String description,
            String shortDescription,
            String status,
            boolean isBuiltin,
            JsonNode config,
            List<BoundThread> boundThreads
    ) {
    }

    public record BoundThread(
            UUID threadId,
            String threadClientKey,
            String threadLabel,
            UUID folderId,
            String folder,
            boolean isFocus,
            String status,
            int sortOrder
    ) {
    }

    public record SyncedThread(
            UUID threadId,
            String threadClientKey,
            boolean labelUpdated,
            boolean summaryUpdated
    ) {
    }

    public record ThreadRoleItem(
            UUID threadId,
            String threadClientKey,
            String threadLabel,
            UUID roleId,
            String roleClientKey,
            String roleName,
            boolean isFocus,
            String status,
            int sortOrder
    ) {
    }
}
