package com.alibaba.arthas.idea.workbench.model;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;

/**
 * 会话页签内部可切换的视图类型。
 */
public enum ArthasSessionViewType {
    CONSOLE("enum.session.view.console"),
    LOG("enum.session.view.log");

    private final String displayNameKey;

    ArthasSessionViewType(String displayNameKey) {
        this.displayNameKey = displayNameKey;
    }

    public String getDisplayName() {
        return ArthasWorkbenchBundle.message(displayNameKey);
    }
}
