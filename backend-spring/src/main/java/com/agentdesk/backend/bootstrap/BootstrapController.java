package com.agentdesk.backend.bootstrap;

import com.agentdesk.backend.common.api.ApiResponse;
import com.agentdesk.backend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class BootstrapController {

    private final BootstrapService bootstrapService;

    public BootstrapController(BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @GetMapping("/{projectId}/bootstrap")
    public ApiResponse<BootstrapResponse> bootstrap(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID projectId,
            @RequestParam(name = "thread", required = false) String queryThreadKey
    ) {
        return ApiResponse.success(bootstrapService.getBootstrap(authenticatedUser, projectId, queryThreadKey));
    }
}
