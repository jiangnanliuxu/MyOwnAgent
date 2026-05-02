package com.agentdesk.backend.role;

import com.agentdesk.backend.auth.AuthRepository;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RoleService {

    private final AuthRepository authRepository;
    private final RoleRepository roleRepository;

    public RoleService(AuthRepository authRepository, RoleRepository roleRepository) {
        this.authRepository = authRepository;
        this.roleRepository = roleRepository;
    }

    public RoleDtos.RoleListResponse listRoles(
            AuthenticatedUser user,
            UUID projectId,
            boolean includeThreadRoles
    ) {
        assertProjectAccess(user, projectId);
        roleRepository.ensureSeeded(projectId);
        return new RoleDtos.RoleListResponse(roleRepository.listRoles(projectId, includeThreadRoles));
    }

    public RoleDtos.RoleResponse getRole(AuthenticatedUser user, UUID roleId) {
        UUID projectId = projectIdForRole(roleId);
        assertProjectAccess(user, projectId);
        return new RoleDtos.RoleResponse(roleRepository.findRole(roleId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Role is not available.")));
    }

    public RoleDtos.RoleUpdateResponse patchRole(
            AuthenticatedUser user,
            UUID roleId,
            RoleDtos.PatchRoleRequest request
    ) {
        UUID projectId = projectIdForRole(roleId);
        assertProjectAccess(user, projectId);
        return roleRepository.patchRole(roleId, request);
    }

    public RoleDtos.SyncRolesResponse syncFolderRoles(AuthenticatedUser user, UUID folderId) {
        UUID projectId = roleRepository.findProjectIdByFolder(folderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Folder is not available."));
        assertProjectAccess(user, projectId);
        roleRepository.ensureSeeded(projectId);
        return roleRepository.syncFolderRoles(folderId);
    }

    private UUID projectIdForRole(UUID roleId) {
        return roleRepository.findProjectIdByRole(roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Role is not available."));
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
