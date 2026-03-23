package com.alibaba.arthas.idea.workbench.util;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.JBColor;
import java.awt.Color;
import java.awt.Font;

/**
 * 为 Console 与 Log 提供统一的 IDEA 跟随式外观配置。
 */
public final class ArthasSessionUiSettings {

    private ArthasSessionUiSettings() {}

    public static Color resolveConsoleForeground() {
        return ideaForeground();
    }

    public static Color resolveConsoleBackground() {
        return ideaBackground();
    }

    public static Color resolveLogForeground() {
        return ideaForeground();
    }

    public static Color resolveLogBackground() {
        return ideaBackground();
    }

    public static Font resolveTerminalFont() {
        EditorColorsScheme scheme = globalScheme();
        String fontName = scheme == null
                        || scheme.getConsoleFontName() == null
                        || scheme.getConsoleFontName().isBlank()
                ? Font.MONOSPACED
                : scheme.getConsoleFontName();
        int fontSize = scheme == null || scheme.getConsoleFontSize() <= 0 ? 12 : scheme.getConsoleFontSize();
        return new Font(fontName, Font.PLAIN, fontSize);
    }

    private static Color ideaForeground() {
        EditorColorsScheme scheme = globalScheme();
        Color color = scheme == null ? null : scheme.getDefaultForeground();
        return color == null ? JBColor.foreground() : color;
    }

    private static Color ideaBackground() {
        EditorColorsScheme scheme = globalScheme();
        Color color = scheme == null ? null : scheme.getDefaultBackground();
        return color == null ? JBColor.PanelBackground : color;
    }

    private static EditorColorsScheme globalScheme() {
        EditorColorsManager manager = EditorColorsManager.getInstance();
        return manager == null ? null : manager.getGlobalScheme();
    }
}
