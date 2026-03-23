package com.alibaba.arthas.idea.workbench.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alibaba.arthas.idea.workbench.model.GatewayAuthMode;
import org.junit.Test;

/**
 * {@link ArthasWorkbenchSettingsService} 的设置解析测试。
 */
public class ArthasWorkbenchSettingsServiceTest {

    @Test
    /**
     * 验证 Gateway 随机认证模式在首次解析时会生成并保留一个可复用的 Token。
     */
    public void shouldGenerateAndPersistGatewayTokenWhenRandomModeIsEnabled() {
        ArthasWorkbenchSettingsService settingsService = new ArthasWorkbenchSettingsService();
        ArthasWorkbenchSettingsService.SettingsState state = new ArthasWorkbenchSettingsService.SettingsState();
        state.mcpGatewayAuthMode = GatewayAuthMode.RANDOM.name();
        state.mcpGatewayToken = "";
        settingsService.updateState(state);

        String firstToken = settingsService.resolveGatewayToken();
        String secondToken = settingsService.resolveGatewayToken();

        assertFalse(firstToken.isBlank());
        assertEquals(firstToken, secondToken);
        assertEquals(firstToken, settingsService.getState().mcpGatewayToken);
    }

    @Test
    /**
     * 验证关闭 Gateway 认证时，即使历史上保存过 Token，也不会继续对外生效。
     */
    public void shouldIgnoreStoredGatewayTokenWhenGatewayAuthIsDisabled() {
        ArthasWorkbenchSettingsService settingsService = new ArthasWorkbenchSettingsService();
        ArthasWorkbenchSettingsService.SettingsState state = new ArthasWorkbenchSettingsService.SettingsState();
        state.mcpGatewayAuthMode = GatewayAuthMode.DISABLED.name();
        state.mcpGatewayToken = "stored-token";
        settingsService.updateState(state);

        assertEquals("", settingsService.resolveGatewayToken());
        assertTrue(settingsService.getState().mcpGatewayToken.equals("stored-token"));
    }
}
