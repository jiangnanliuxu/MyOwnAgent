package com.agentdesk.backend.role;

import com.agentdesk.backend.common.api.ApiResponse;
import com.agentdesk.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/projects/{projectId}/roles")
    public ApiResponse<RoleDtos.RoleListResponse> listRoles(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @RequestParam(name = "include_thread_roles", defaultValue = "false") boolean includeThreadRoles
    ) {
        return ApiResponse.success(roleService.listRoles(user, projectId, includeThreadRoles));
    }

    @GetMapping("/roles/{roleId}")
    public ApiResponse<RoleDtos.RoleResponse> getRole(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID roleId
    ) {
        return ApiResponse.success(roleService.getRole(user, roleId));
    }

    @PatchMapping("/roles/{roleId}")
    public ApiResponse<RoleDtos.RoleUpdateResponse> patchRole(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID roleId,
            @Valid @RequestBody RoleDtos.PatchRoleRequest request
    ) {
        return ApiResponse.success(roleService.patchRole(user, roleId, request));
    }

    @PostMapping("/folders/{folderId}/sync-roles")
    public ApiResponse<RoleDtos.SyncRolesResponse> syncFolderRoles(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID folderId
    ) {
        return ApiResponse.success(roleService.syncFolderRoles(user, folderId));
    }
}
