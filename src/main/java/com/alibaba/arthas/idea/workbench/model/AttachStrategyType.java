package com.alibaba.arthas.idea.workbench.model;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;

/**
 * Attach 策略枚举。
 * 枚举本身只保留国际化 key，真正展示给用户的文本在访问时再从 bundle 中读取。
 */
public enum AttachStrategyType {
    ARTHAS_BOOT("enum.attach.strategy.arthas_boot.name", "enum.attach.strategy.arthas_boot.hint");

    private final String displayNameKey;
    private final String hintKey;

    AttachStrategyType(String displayNameKey, String hintKey) {
        this.displayNameKey = displayNameKey;
        this.hintKey = hintKey;
    }

    public String getDisplayName() {
        return ArthasWorkbenchBundle.message(displayNameKey);
    }

    public String getHint() {
        return ArthasWorkbenchBundle.message(hintKey);
    }

    public static AttachStrategyType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ARTHAS_BOOT;
        }
        try {
            return AttachStrategyType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return ARTHAS_BOOT;
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
