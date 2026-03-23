package com.alibaba.arthas.idea.workbench.model;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;

/**
 * 端口分配策略枚举。
 */
public enum PortAllocationMode {
    PREFER_CONFIGURED("enum.port.allocation.prefer_configured.name", "enum.port.allocation.prefer_configured.hint"),
    ALWAYS_AUTO("enum.port.allocation.always_auto.name", "enum.port.allocation.always_auto.hint"),
    STRICT_CONFIGURED("enum.port.allocation.strict_configured.name", "enum.port.allocation.strict_configured.hint");

    private final String displayNameKey;
    private final String hintKey;

    PortAllocationMode(String displayNameKey, String hintKey) {
        this.displayNameKey = displayNameKey;
        this.hintKey = hintKey;
    }

    public String getDisplayName() {
        return ArthasWorkbenchBundle.message(displayNameKey);
    }

    public String getHint() {
        return ArthasWorkbenchBundle.message(hintKey);
    }

    public static PortAllocationMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PREFER_CONFIGURED;
        }
        try {
            return PortAllocationMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return PREFER_CONFIGURED;
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
