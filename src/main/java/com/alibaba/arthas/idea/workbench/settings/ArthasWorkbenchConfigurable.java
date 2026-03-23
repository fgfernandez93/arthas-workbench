package com.alibaba.arthas.idea.workbench.settings;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.GatewayAuthMode;
import com.alibaba.arthas.idea.workbench.model.McpPasswordMode;
import com.alibaba.arthas.idea.workbench.service.ArthasWorkbenchSettingsService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import javax.swing.JComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * 插件设置页入口。
 */
public final class ArthasWorkbenchConfigurable implements SearchableConfigurable {

    private ArthasWorkbenchSettingsPanel settingsPanel;

    @Override
    public @Nls String getDisplayName() {
        return ArthasWorkbenchBundle.message("settings.display.name");
    }

    @Override
    public String getId() {
        return "com.github.wl2027.arthasworkbench.settings";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (settingsPanel == null) {
            settingsPanel = new ArthasWorkbenchSettingsPanel(null);
            settingsPanel.resetFrom(settingsService().getState());
        }
        return settingsPanel.getComponent();
    }

    @Override
    public boolean isModified() {
        return settingsPanel != null
                && settingsPanel.isModified(settingsService().getState());
    }

    @Override
    public void apply() throws ConfigurationException {
        if (settingsPanel != null) {
            ArthasWorkbenchSettingsService.SettingsState state = settingsPanel.toState();
            McpPasswordMode passwordMode = McpPasswordMode.fromValue(state.mcpPasswordMode, state.mcpPassword);
            GatewayAuthMode gatewayAuthMode =
                    GatewayAuthMode.fromValue(state.mcpGatewayAuthMode, state.mcpGatewayToken);
            if (passwordMode.requiresPassword() && state.mcpPassword.isBlank()) {
                throw new ConfigurationException(
                        ArthasWorkbenchBundle.message("settings.validation.password_required"));
            }
            if (gatewayAuthMode.requiresPassword() && state.mcpGatewayToken.isBlank()) {
                throw new ConfigurationException(
                        ArthasWorkbenchBundle.message("settings.validation.gateway_token_required"));
            }
            validatePort(state.mcpGatewayPort, ArthasWorkbenchBundle.message("settings.gateway.port"));
            state.mcpPasswordMode = passwordMode.name();
            state.mcpGatewayAuthMode = gatewayAuthMode.name();
            settingsService().updateState(state);
        }
    }

    @Override
    public void reset() {
        if (settingsPanel != null) {
            settingsPanel.resetFrom(settingsService().getState());
        }
    }

    @Override
    public void disposeUIResources() {
        settingsPanel = null;
    }

    private ArthasWorkbenchSettingsService settingsService() {
        return ApplicationManager.getApplication().getService(ArthasWorkbenchSettingsService.class);
    }

    private void validatePort(String value, String fieldName) throws ConfigurationException {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 0 || port > 65535) {
                throw new ConfigurationException(
                        ArthasWorkbenchBundle.message("settings.validation.port.range", fieldName));
            }
        } catch (NumberFormatException exception) {
            throw new ConfigurationException(
                    ArthasWorkbenchBundle.message("settings.validation.port.integer", fieldName));
        }
    }
}
