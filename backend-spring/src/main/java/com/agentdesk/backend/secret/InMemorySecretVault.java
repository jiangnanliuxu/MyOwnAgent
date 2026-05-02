package com.agentdesk.backend.secret;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemorySecretVault {

    private final ConcurrentMap<String, String> secrets = new ConcurrentHashMap<>();

    public void put(String secretRef, String secretValue) {
        if (!StringUtils.hasText(secretRef) || !StringUtils.hasText(secretValue) || isDisplayPlaceholder(secretValue)) {
            return;
        }
        secrets.put(secretRef, secretValue.trim());
    }

    public Optional<String> resolve(String secretRef) {
        if (!StringUtils.hasText(secretRef)) {
            return Optional.empty();
        }
        return Optional.ofNullable(secrets.get(secretRef));
    }

    private boolean isDisplayPlaceholder(String value) {
        String normalized = value.trim();
        return normalized.startsWith("已")
                || normalized.equals("待配置")
                || normalized.equalsIgnoreCase("secret_ref")
                || normalized.toLowerCase().contains("secret_ref");
    }
}
