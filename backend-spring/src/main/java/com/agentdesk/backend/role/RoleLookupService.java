package com.agentdesk.backend.role;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RoleLookupService {

    private final RoleRepository roleRepository;

    public RoleLookupService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Optional<RoleDtos.RoleItem> findRole(UUID roleId) {
        if (roleId == null) {
            return Optional.empty();
        }
        return roleRepository.findRole(roleId, false);
    }

    public Optional<RoleDtos.RoleItem> findRoleByClientKey(UUID projectId, String clientKey) {
        if (projectId == null || !StringUtils.hasText(clientKey)) {
            return Optional.empty();
        }
        return roleRepository.listRoles(projectId, false).stream()
                .filter(role -> clientKey.equals(role.clientKey()))
                .findFirst();
    }

    public List<RoleDtos.RoleItem> listRoles(UUID projectId) {
        return roleRepository.listRoles(projectId, false);
    }
}
