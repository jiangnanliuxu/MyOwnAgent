package com.agentdesk.backend.health;

import com.agentdesk.backend.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String applicationName;
    private final Optional<BuildProperties> buildProperties;

    public HealthController(
            @Value("${spring.application.name}") String applicationName,
            Optional<BuildProperties> buildProperties
    ) {
        this.applicationName = applicationName;
        this.buildProperties = buildProperties;
    }

    @GetMapping
    public ApiResponse<HealthStatus> health() {
        String version = buildProperties
                .map(BuildProperties::getVersion)
                .orElse("dev");
        return ApiResponse.success(new HealthStatus("UP", applicationName, version, Instant.now()));
    }
}
