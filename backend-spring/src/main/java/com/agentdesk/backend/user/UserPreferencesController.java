package com.agentdesk.backend.user;

import com.agentdesk.backend.common.api.ApiResponse;
import com.agentdesk.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/preferences")
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;

    public UserPreferencesController(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
    }

    @GetMapping
    public ApiResponse<UserPreferencesResponse> getPreferences(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ApiResponse.success(userPreferencesService.getPreferences(authenticatedUser.id()));
    }

    @PatchMapping
    public ApiResponse<UserPreferencesResponse> patchPreferences(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody JsonNode request
    ) {
        return ApiResponse.success(userPreferencesService.patchPreferences(authenticatedUser.id(), request));
    }
}
