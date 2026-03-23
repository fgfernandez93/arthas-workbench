package com.alibaba.arthas.idea.workbench.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import org.junit.Test;

/**
 * {@link McpConfigFormatter} 输出内容测试。
 */
public class McpConfigFormatterTest {

    @Test
    /**
     * 验证直连配置在存在密码时会自动带上 Authorization 头。
     */
    public void shouldIncludeBearerHeaderWhenPasswordExists() {
        ArthasSession session = new ArthasSession(
                "1",
                12345L,
                "demo-app",
                8563,
                3658,
                "/mcp",
                "secret",
                "官方最新版本",
                "Arthas Boot",
                "/tmp/java",
                "/tmp/arthas-boot.jar",
                null,
                SessionStatus.RUNNING);

        String config = McpConfigFormatter.format(session);

        assertTrue(config.contains("\"url\": \"http://127.0.0.1:8563/mcp\""));
        assertTrue(config.contains("\"arthas-demo-app-12345\""));
        assertTrue(config.contains("\"Authorization\": \"Bearer secret\""));
    }

    @Test
    /**
     * 验证网关配置默认不会泄漏 agent 自身的密码。
     */
    public void shouldFormatGatewayConfigWithoutAgentPasswordHeader() {
        ArthasSession session = new ArthasSession(
                "1",
                12345L,
                "demo-app",
                8563,
                3658,
                "/mcp",
                "secret",
                "官方最新版本",
                "Arthas Boot",
                "/tmp/java",
                "/tmp/arthas-boot.jar",
                null,
                SessionStatus.RUNNING);

        String config = McpConfigFormatter.formatGateway(session, "http://127.0.0.1:18765/gateway/mcp");

        assertTrue(config.contains("\"url\": \"http://127.0.0.1:18765/gateway/mcp\""));
        assertTrue(config.contains("\"idea-arthas-workbench\""));
        assertFalse(config.contains("Authorization"));
    }

    @Test
    /**
     * 验证网关配置在启用统一 token 时会注入网关层 Authorization 头。
     */
    public void shouldFormatGatewayConfigWithGatewayToken() {
        ArthasSession session = new ArthasSession(
                "1",
                12345L,
                "demo-app",
                8563,
                3658,
                "/mcp",
                "agent-secret",
                "官方最新版本",
                "Arthas Boot",
                "/tmp/java",
                "/tmp/arthas-boot.jar",
                null,
                SessionStatus.RUNNING);

        String config =
                McpConfigFormatter.formatGateway(session, "http://127.0.0.1:18765/gateway/mcp", "gateway-secret");

        assertTrue(config.contains("\"url\": \"http://127.0.0.1:18765/gateway/mcp\""));
        assertTrue(config.contains("\"Authorization\": \"Bearer gateway-secret\""));
        assertFalse(config.contains("agent-secret"));
    }

    @Test
    /**
     * 验证网关配置固定使用稳定的 server key，方便用户在 MCP 客户端长期复用配置。
     */
    public void shouldUseStableGatewayServerName() {
        ArthasSession session = new ArthasSession(
                "1",
                48619L,
                "demo.MathGame",
                8563,
                3658,
                "/mcp",
                "agent-secret",
                "官方最新版本",
                "Arthas Boot",
                "/tmp/java",
                "/tmp/arthas-boot.jar",
                null,
                SessionStatus.RUNNING);

        String config =
                McpConfigFormatter.formatGateway(session, "http://127.0.0.1:18765/gateway/mcp", "gateway-secret");

        assertTrue(config.contains("\"idea-arthas-workbench\""));
        assertFalse(config.contains("demo-mathgame-48619"));
    }

    @Test
    /**
     * 验证显示名中包含空格、路径或符号时，服务名会被清洗成 MCP 客户端更容易识别的短别名。
     */
    public void shouldNormalizeDisplayNameIntoCompactServerNamePart() {
        String normalized = McpConfigFormatter.normalizeServerNamePart(
                " org.jetbrains.jps.cmdline.Launcher /Users/weil/Desktop/math-game ");

        assertTrue(normalized.startsWith("org-jetbrains-jps-cmdline-launcher"));
        assertFalse(normalized.contains("/"));
        assertFalse(normalized.contains(" "));
    }
}
