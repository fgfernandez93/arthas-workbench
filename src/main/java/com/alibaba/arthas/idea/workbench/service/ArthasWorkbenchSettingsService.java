package com.alibaba.arthas.idea.workbench.service;

import com.alibaba.arthas.idea.workbench.model.GatewayAuthMode;
import com.alibaba.arthas.idea.workbench.model.McpPasswordMode;
import com.alibaba.arthas.idea.workbench.model.PackageSourceSpec;
import com.alibaba.arthas.idea.workbench.model.PackageSourceType;
import com.alibaba.arthas.idea.workbench.model.PortAllocationMode;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.util.UUID;

/**
 * 持久化保存插件的全局设置，供 Workbench、Session 和 Gateway 共享。
 */
@Service(Service.Level.APP)
@State(name = "ArthasWorkbenchSettings", storages = @Storage("arthas-workbench.xml"))
public final class ArthasWorkbenchSettingsService
        implements PersistentStateComponent<ArthasWorkbenchSettingsService.SettingsState> {

    private SettingsState state = new SettingsState();

    @Override
    public SettingsState getState() {
        return state;
    }

    @Override
    public void loadState(SettingsState state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    /**
     * 将持久化状态转换为可供包管理器直接消费的来源配置。
     */
    public PackageSourceSpec toPackageSourceSpec() {
        PackageSourceType sourceType;
        try {
            sourceType = PackageSourceType.valueOf(state.sourceType);
        } catch (IllegalArgumentException exception) {
            sourceType = PackageSourceType.OFFICIAL_LATEST;
        }
        return new PackageSourceSpec(sourceType, state.sourceValue);
    }

    public void updateState(SettingsState newState) {
        XmlSerializerUtil.copyBean(newState, state);
    }

    public McpPasswordMode resolveMcpPasswordMode() {
        return McpPasswordMode.fromValue(state.mcpPasswordMode, state.mcpPassword);
    }

    public GatewayAuthMode resolveGatewayAuthMode() {
        return GatewayAuthMode.fromValue(state.mcpGatewayAuthMode, state.mcpGatewayToken);
    }

    /**
     * 解析当前 Gateway 应生效的认证 Token。
     * 随机模式会在首次需要时生成并保留到持久化状态中，保证外部客户端可稳定复用。
     */
    public synchronized String resolveGatewayToken() {
        GatewayAuthMode authMode = resolveGatewayAuthMode();
        String configuredToken = state.mcpGatewayToken == null ? "" : state.mcpGatewayToken.trim();
        return switch (authMode) {
            case FIXED -> configuredToken;
            case DISABLED -> "";
            case RANDOM -> {
                if (configuredToken.isBlank()) {
                    configuredToken = UUID.randomUUID().toString().replace("-", "");
                    state.mcpGatewayToken = configuredToken;
                }
                yield configuredToken;
            }
        };
    }

    /**
     * 插件设置持久化载体；字段名会直接序列化到 IDE 配置文件中。
     */
    public static final class SettingsState {
        public String sourceType = PackageSourceType.OFFICIAL_LATEST.name();
        public String sourceValue = "";
        public String portAllocationMode = PortAllocationMode.PREFER_CONFIGURED.name();
        public String httpPort = "8563";
        public String telnetPort = "3658";
        public String mcpGatewayPort = "18765";
        public String mcpGatewayAuthMode = GatewayAuthMode.DISABLED.name();
        public String mcpGatewayToken = "";
        public String mcpPasswordMode = McpPasswordMode.RANDOM.name();
        public String mcpPassword = "";
        public boolean autoConnectConsole = true;
        public boolean autoOpenWebUi = true;
    }
}
