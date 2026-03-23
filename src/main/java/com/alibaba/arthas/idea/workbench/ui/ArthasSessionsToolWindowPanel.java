package com.alibaba.arthas.idea.workbench.ui;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import com.alibaba.arthas.idea.workbench.service.ArthasSessionService;
import com.alibaba.arthas.idea.workbench.util.UiToolkit;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一会话 Tool Window 的内容容器。
 * 这里只维护会话页签的生命周期，不承载额外业务逻辑。
 */
public final class ArthasSessionsToolWindowPanel implements Disposable {

    private static final Key<String> SESSION_ID_KEY = Key.create("arthas.workbench.sessions.sessionId");
    private static final int SESSION_DESCRIPTION_TEXT_MAX_LENGTH = 160;

    private final Project project;
    private final ArthasSessionService sessionService;
    private final ContentManager contentManager;
    private final Map<String, ArthasSessionTabPanel> sessionPanels = new LinkedHashMap<>();
    private final Map<String, Content> contents = new LinkedHashMap<>();
    private final ContentManagerListener contentManagerListener = new ContentManagerListener() {
        @Override
        public void contentRemoved(ContentManagerEvent event) {
            if (syncingContent) {
                return;
            }
            String sessionId = event.getContent().getUserData(SESSION_ID_KEY);
            if (sessionId != null) {
                sessionService.closeSessionWindow(sessionId);
            }
        }
    };

    private Content placeholderContent;
    private boolean syncingContent;

    public ArthasSessionsToolWindowPanel(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.sessionService = project.getService(ArthasSessionService.class);
        this.contentManager = toolWindow.getContentManager();
        resetContentManager();
        this.contentManager.addContentManagerListener(contentManagerListener);
        Disposer.register(this, () -> this.contentManager.removeContentManagerListener(contentManagerListener));
        sessionService.addListener(this::refreshContents, this);
        refreshContents();
    }

    /**
     * Tool Window 重新初始化时先清理旧内容，避免出现重复的占位页或会话页。
     */
    private void resetContentManager() {
        syncingContent = true;
        try {
            for (Content content : contentManager.getContents()) {
                contentManager.removeContent(content, true);
            }
        } finally {
            syncingContent = false;
        }
        placeholderContent = null;
        sessionPanels.clear();
        contents.clear();
    }

    /**
     * 统一同步会话页签列表，并尽量保留当前选中的会话。
     */
    private void refreshContents() {
        List<ArthasSessionService.SessionSnapshot> visibleSnapshots = sessionService.snapshots().stream()
                .filter(ArthasSessionService.SessionSnapshot::isSessionWindowOpen)
                .toList();
        String selectedId = selectedSessionId();
        syncingContent = true;
        try {
            if (visibleSnapshots.isEmpty()) {
                removeAllSessionContents();
                ensurePlaceholderContent();
                return;
            }

            removePlaceholderContent();
            for (ArthasSessionService.SessionSnapshot snapshot : visibleSnapshots) {
                ensureSessionContent(snapshot);
            }
            removeStaleContents(visibleSnapshots.stream()
                    .map(ArthasSessionService.SessionSnapshot::getId)
                    .toList());

            if (selectedId != null) {
                Content selected = contents.get(selectedId);
                if (selected != null) {
                    contentManager.setSelectedContent(selected);
                }
            } else if (!visibleSnapshots.isEmpty()) {
                Content selected = contents.get(visibleSnapshots.get(0).getId());
                if (selected != null) {
                    contentManager.setSelectedContent(selected);
                }
            }
        } finally {
            syncingContent = false;
        }
    }

    /**
     * 按需创建或刷新单个会话页签。
     */
    private void ensureSessionContent(ArthasSessionService.SessionSnapshot snapshot) {
        ArthasSessionTabPanel panel = sessionPanels.get(snapshot.getId());
        Content content = contents.get(snapshot.getId());
        if (panel == null || content == null) {
            panel = new ArthasSessionTabPanel(project, snapshot.getId());
            content = ContentFactory.getInstance().createContent(panel, snapshot.getTitle(), false);
            content.setDisposer(panel);
            content.putUserData(SESSION_ID_KEY, snapshot.getId());
            sessionPanels.put(snapshot.getId(), panel);
            contents.put(snapshot.getId(), content);
            contentManager.addContent(content);
        }
        panel.bindSnapshot(snapshot);
        content.setDisplayName(snapshot.getTitle());
        content.setDescription(UiToolkit.compactSingleLine(
                snapshot.getSession().getDisplayName(), SESSION_DESCRIPTION_TEXT_MAX_LENGTH));
        content.setCloseable(isManualCloseAllowed(snapshot));
    }

    private void removeStaleContents(List<String> activeSessionIds) {
        List<String> toRemove = new ArrayList<>();
        for (String sessionId : contents.keySet()) {
            if (!activeSessionIds.contains(sessionId)) {
                toRemove.add(sessionId);
            }
        }
        for (String sessionId : toRemove) {
            Content content = contents.remove(sessionId);
            sessionPanels.remove(sessionId);
            if (content != null) {
                contentManager.removeContent(content, true);
            }
        }
    }

    private void removeAllSessionContents() {
        List<String> sessionIds = new ArrayList<>(contents.keySet());
        for (String sessionId : sessionIds) {
            Content content = contents.remove(sessionId);
            sessionPanels.remove(sessionId);
            if (content != null) {
                contentManager.removeContent(content, true);
            }
        }
    }

    /**
     * 当没有打开任何会话时，显示一个简单的占位说明，避免 Tool Window 变成空白。
     */
    private void ensurePlaceholderContent() {
        if (placeholderContent != null && placeholderContent.getManager() == contentManager) {
            if (!contentManager.isSelected(placeholderContent)) {
                contentManager.setSelectedContent(placeholderContent);
            }
            return;
        }
        JBTextArea area = new JBTextArea(ArthasWorkbenchBundle.message("sessions.placeholder.message"));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        placeholderContent = ContentFactory.getInstance()
                .createContent(
                        new JBScrollPane(area), ArthasWorkbenchBundle.message("sessions.placeholder.title"), false);
        placeholderContent.setCloseable(false);
        contentManager.addContent(placeholderContent);
        contentManager.setSelectedContent(placeholderContent);
    }

    private void removePlaceholderContent() {
        if (placeholderContent != null && placeholderContent.getManager() == contentManager) {
            contentManager.removeContent(placeholderContent, true);
        }
        placeholderContent = null;
    }

    private String selectedSessionId() {
        Content selected = contentManager.getSelectedContent();
        return selected == null ? null : selected.getUserData(SESSION_ID_KEY);
    }

    @Override
    public void dispose() {
        removePlaceholderContent();
        removeAllSessionContents();
    }

    /**
     * 运行中的会话不允许手动关闭，只有结束或失败后才允许点页签 x。
     */
    static boolean isManualCloseAllowed(ArthasSessionService.SessionSnapshot snapshot) {
        SessionStatus status = snapshot.getSession().getStatus();
        return status == SessionStatus.STOPPED || status == SessionStatus.FAILED;
    }
}
