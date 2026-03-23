package com.alibaba.arthas.idea.workbench.model;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;

/**
 * MCP Gateway 认证策略枚举。
 */
public enum GatewayAuthMode {
    RANDOM("enum.gateway.auth.random.name", "enum.gateway.auth.random.hint"),
    FIXED("enum.gateway.auth.fixed.name", "enum.gateway.auth.fixed.hint"),
    DISABLED("enum.gateway.auth.disabled.name", "enum.gateway.auth.disabled.hint");

    private final String displayNameKey;
    private final String hintKey;

    GatewayAuthMode(String displayNameKey, String hintKey) {
        this.displayNameKey = displayNameKey;
        this.hintKey = hintKey;
    }

    public String getHint() {
        return ArthasWorkbenchBundle.message(hintKey);
    }

    public boolean requiresPassword() {
        return this == FIXED;
    }

    public boolean usesGeneratedPassword() {
        return this == RANDOM;
    }

    public static GatewayAuthMode fromValue(String value, String configuredToken) {
        if (value == null || value.isBlank()) {
            return configuredToken == null || configuredToken.isBlank() ? DISABLED : FIXED;
        }
        try {
            return GatewayAuthMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return configuredToken == null || configuredToken.isBlank() ? DISABLED : FIXED;
        }
    }

    @Override
    public String toString() {
        return ArthasWorkbenchBundle.message(displayNameKey);
    }
}
