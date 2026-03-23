package com.alibaba.arthas.idea.workbench.model;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;

/**
 * Agent MCP 密码策略枚举。
 */
public enum McpPasswordMode {
    RANDOM("enum.mcp.password.random.name", "enum.mcp.password.random.hint"),
    FIXED("enum.mcp.password.fixed.name", "enum.mcp.password.fixed.hint"),
    DISABLED("enum.mcp.password.disabled.name", "enum.mcp.password.disabled.hint");

    private final String displayNameKey;
    private final String hintKey;

    McpPasswordMode(String displayNameKey, String hintKey) {
        this.displayNameKey = displayNameKey;
        this.hintKey = hintKey;
    }

    public String getHint() {
        return ArthasWorkbenchBundle.message(hintKey);
    }

    public boolean requiresPassword() {
        return this == FIXED;
    }

    public static McpPasswordMode fromValue(String value, String configuredPassword) {
        if (value == null || value.isBlank()) {
            return configuredPassword == null || configuredPassword.isBlank() ? RANDOM : FIXED;
        }
        try {
            return McpPasswordMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return configuredPassword == null || configuredPassword.isBlank() ? RANDOM : FIXED;
        }
    }

    @Override
    public String toString() {
        return ArthasWorkbenchBundle.message(displayNameKey);
    }
}
