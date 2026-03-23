package com.alibaba.arthas.idea.workbench.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.alibaba.arthas.idea.workbench.model.McpPasswordMode;
import com.alibaba.arthas.idea.workbench.model.PackageSourceSpec;
import com.alibaba.arthas.idea.workbench.model.PackageSourceType;
import com.alibaba.arthas.idea.workbench.model.PortAllocationMode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * {@link ArthasAttachService} 的核心静态辅助方法测试。
 */
public class ArthasAttachServiceTest {

    @Test
    /**
     * 验证当目标是 JRE 目录时，仍然可以回溯到上层 JDK 的 java 可执行文件。
     */
    public void shouldResolveJavaExecutableFromJreHome() throws Exception {
        Path tempRoot = Files.createTempDirectory("arthas-attach-java-home");
        Path jreHome = tempRoot.resolve("jdk8").resolve("jre");
        Path javaExecutable = tempRoot.resolve("jdk8").resolve("bin").resolve("java");
        Files.createDirectories(javaExecutable.getParent());
        Files.createDirectories(jreHome);
        Files.createFile(javaExecutable);

        assertEquals(
                javaExecutable.toString(), ArthasAttachService.resolveJavaExecutableFromJavaHome(jreHome.toString()));
    }

    @Test
    /**
     * 验证不存在的 java.home 不会误返回错误路径。
     */
    public void shouldReturnNullWhenJavaHomeCannotResolveExecutable() {
        assertNull(ArthasAttachService.resolveJavaExecutableFromJavaHome("/path/not/exist"));
    }

    @Test
    /**
     * 验证固定密码模式会原样使用配置值。
     */
    public void shouldKeepFixedPasswordWhenConfigured() {
        String password = ArthasAttachService.resolveMcpPassword(createRequest(McpPasswordMode.FIXED, "secret"));

        assertEquals("secret", password);
    }

    @Test
    /**
     * 验证关闭认证时不会生成密码。
     */
    public void shouldDisablePasswordWhenConfigured() {
        String password = ArthasAttachService.resolveMcpPassword(createRequest(McpPasswordMode.DISABLED, "ignored"));

        assertEquals("", password);
    }

    @Test
    /**
     * 验证随机模式会生成非空密码。
     */
    public void shouldGenerateRandomPasswordWhenConfigured() {
        String password = ArthasAttachService.resolveMcpPassword(createRequest(McpPasswordMode.RANDOM, ""));

        assertFalse(password.isBlank());
    }

    @Test
    /**
     * 验证固定密码模式下不允许传入空密码。
     */
    public void shouldRejectBlankFixedPassword() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ArthasAttachService.resolveMcpPassword(createRequest(McpPasswordMode.FIXED, "")));
    }

    /**
     * 构造最小可用的 AttachRequest 供密码策略测试复用。
     */
    private AttachRequest createRequest(McpPasswordMode mode, String password) {
        return new AttachRequest(
                "session-1",
                100L,
                "demo-app",
                new PackageSourceSpec(PackageSourceType.OFFICIAL_LATEST, ""),
                8563,
                3658,
                PortAllocationMode.PREFER_CONFIGURED,
                "/mcp",
                mode,
                password,
                false);
    }
}
