package com.agentdesk.backend.role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RoleRepository {

    void ensureSeeded(UUID projectId);

    Optional<UUID> findProjectIdByRole(UUID roleId);

    Optional<UUID> findProjectIdByFolder(UUID folderId);

    List<RoleDtos.RoleItem> listRoles(UUID projectId, boolean includeThreadRoles);

    Optional<RoleDtos.RoleItem> findRole(UUID roleId, boolean includeThreadRoles);

    RoleDtos.RoleUpdateResponse patchRole(UUID roleId, RoleDtos.PatchRoleRequest request);

    RoleDtos.SyncRolesResponse syncFolderRoles(UUID folderId);
}
