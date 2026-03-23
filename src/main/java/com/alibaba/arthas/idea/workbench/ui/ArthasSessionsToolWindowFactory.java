package com.alibaba.arthas.idea.workbench.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowFactory;

/**
 * 创建左下角 Arthas Sessions Tool Window，并绑定统一会话页签管理面板。
 */
public final class ArthasSessionsToolWindowFactory implements ToolWindowFactory {

    private static final String PANEL_PROPERTY = "arthas.workbench.sessions.toolWindowPanel";

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        toolWindow.setAnchor(ToolWindowAnchor.BOTTOM, null);
        toolWindow.setSplitMode(false, null);
        if (toolWindow.getComponent().getClientProperty(PANEL_PROPERTY) != null) {
            return;
        }
        ArthasSessionsToolWindowPanel panel = new ArthasSessionsToolWindowPanel(project, toolWindow);
        toolWindow.getComponent().putClientProperty(PANEL_PROPERTY, panel);
        Disposer.register(toolWindow.getContentManager(), panel);
        Disposer.register(
                toolWindow.getContentManager(),
                () -> toolWindow.getComponent().putClientProperty(PANEL_PROPERTY, null));
    }

    @Override
    public boolean shouldBeAvailable(Project project) {
        return true;
    }
}
