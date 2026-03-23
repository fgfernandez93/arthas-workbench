package com.alibaba.arthas.idea.workbench.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

/**
 * 创建 Workbench 主工具窗口，用于进程发现、 Attach 和统一操作入口。
 */
public final class ArthasWorkbenchToolWindowFactory implements ToolWindowFactory {

    private static final String INITIALIZED_PROPERTY = "arthas.workbench.main.initialized";

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        if (Boolean.TRUE.equals(toolWindow.getComponent().getClientProperty(INITIALIZED_PROPERTY))) {
            return;
        }
        ArthasWorkbenchPanel panel = new ArthasWorkbenchPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, null, false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
        toolWindow.getComponent().putClientProperty(INITIALIZED_PROPERTY, Boolean.TRUE);
    }

    @Override
    public boolean shouldBeAvailable(Project project) {
        return true;
    }
}
