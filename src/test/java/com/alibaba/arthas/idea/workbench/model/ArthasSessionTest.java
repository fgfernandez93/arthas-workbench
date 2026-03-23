package com.alibaba.arthas.idea.workbench.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * {@link ArthasSession} 的核心行为测试。
 */
public class ArthasSessionTest {

    @Test
    /**
     * 验证 MCP endpoint 会自动补齐前导斜杠，避免生成非法 URL。
     */
    public void shouldNormalizeMcpEndpointWhenBuildingMcpUrl() {
        ArthasSession session = new ArthasSession(
                "session-1",
                1001L,
                "demo.MathGame",
                8563,
                3658,
                "mcp",
                "secret",
                "官方最新版本",
                "Arthas Boot",
                "/tmp/java",
                "/tmp/arthas-boot.jar",
                "/tmp/arthas",
                SessionStatus.RUNNING);

        assertEquals("http://127.0.0.1:8563/mcp", session.getMcpUrl());
    }

    @Test
    /**
     * 验证状态切换时其余会话字段保持不变。
     */
    public void shouldKeepSessionFieldsWhenStatusChanges() {
        ArthasSession session = new ArthasSession(
                "session-1",
                1001L,
                "demo.MathGame",
                8563,
                3658,
                "/mcp",
                "secret",
                "官方最新版本",
                "Arthas Boot",
                "/tmp/java",
                "/tmp/arthas-boot.jar",
                "/tmp/arthas",
                SessionStatus.ATTACHING);

        ArthasSession stopped = session.withStatus(SessionStatus.STOPPED);

        assertEquals("session-1", stopped.getId());
        assertEquals(1001L, stopped.getPid());
        assertEquals("demo.MathGame", stopped.getDisplayName());
        assertEquals(8563, stopped.getHttpPort());
        assertEquals(3658, stopped.getTelnetPort());
        assertEquals("/mcp", stopped.getMcpEndpoint());
        assertEquals("secret", stopped.getMcpPassword());
        assertEquals("官方最新版本", stopped.getPackageLabel());
        assertEquals("Arthas Boot", stopped.getAttachStrategyLabel());
        assertEquals("/tmp/java", stopped.getJavaExecutablePath());
        assertEquals("/tmp/arthas-boot.jar", stopped.getBootJarPath());
        assertEquals("/tmp/arthas", stopped.getArthasHomePath());
        assertEquals(SessionStatus.STOPPED, stopped.getStatus());
    }
}
