package com.alibaba.arthas.idea.workbench.util;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import java.awt.Desktop;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.net.URI;

/**
 * 统一封装插件常用的通知、剪贴板与桌面集成操作。
 */
public final class UiToolkit {

    private static final String NOTIFICATION_GROUP = "Arthas Workbench";
    private static final String ELLIPSIS = "...";

    private UiToolkit() {}

    public static void notifyInfo(Project project, String message) {
        notify(project, message, NotificationType.INFORMATION);
    }

    public static void notifyWarn(Project project, String message) {
        notify(project, message, NotificationType.WARNING);
    }

    public static void notifyError(Project project, String message) {
        notify(project, message, NotificationType.ERROR);
    }

    public static void copyToClipboard(String text) {
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }

    public static void openInBrowser(Project project, String url) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException(message("util.ui.error.browser_unsupported"));
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception exception) {
            notifyError(project, message("util.ui.error.open_browser_failed", exception.getMessage()));
        }
    }

    public static void openDirectory(Project project, String path) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException(message("util.ui.error.directory_unsupported"));
            }
            File target = new File(path);
            if (!target.exists()) {
                throw new IllegalStateException(message("util.ui.error.directory_missing", path));
            }
            File directory = target.isDirectory() ? target : target.getParentFile();
            if (directory == null || !directory.exists()) {
                throw new IllegalStateException(message("util.ui.error.directory_unresolved", path));
            }
            Desktop.getDesktop().open(directory);
        } catch (Exception exception) {
            notifyError(project, message("util.ui.error.open_directory_failed", exception.getMessage()));
        }
    }

    /**
     * 将任意文本压缩为适合界面展示的单行摘要，避免超长 JVM 命令行触发 Swing 文本布局问题。
     */
    public static String compactSingleLine(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        normalized = normalized.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        if (maxLength <= ELLIPSIS.length()) {
            return ELLIPSIS.substring(0, maxLength);
        }
        if (maxLength <= 16) {
            return normalized.substring(0, maxLength - ELLIPSIS.length()) + ELLIPSIS;
        }

        int suffixLength = Math.min(24, Math.max(8, maxLength / 4));
        int prefixLength = maxLength - suffixLength - 5;
        if (prefixLength < 8) {
            prefixLength = 8;
            suffixLength = Math.max(0, maxLength - prefixLength - 5);
        }
        return normalized.substring(0, prefixLength)
                + " ..."
                + (suffixLength == 0 ? "" : " " + normalized.substring(normalized.length() - suffixLength));
    }

    private static void notify(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(message, type)
                .notify(project);
    }

    private static String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }
}
