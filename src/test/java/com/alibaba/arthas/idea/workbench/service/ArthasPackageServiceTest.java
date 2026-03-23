package com.alibaba.arthas.idea.workbench.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.alibaba.arthas.idea.workbench.model.PackageSourceSpec;
import com.alibaba.arthas.idea.workbench.model.PackageSourceType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * {@link ArthasPackageService} 的路径相关测试。
 */
public class ArthasPackageServiceTest {

    @Test
    /**
     * 验证插件默认使用稳定的用户目录缓存路径。
     */
    public void shouldUseStableUserHomeCacheDirectory() {
        Path userHome = Path.of("/Users/demo");
        Path expected = Path.of("/Users/demo/.arthas-workbench-plugin/packages");

        assertEquals(expected, ArthasPackageService.defaultCacheRoot(userHome));
    }

    @Test
    /**
     * 验证“本地 arthas-bin 目录”来源现在只接受目录路径，不再接受单文件路径。
     */
    public void shouldRejectLocalPathWhenItPointsToAFile() throws Exception {
        ArthasPackageService packageService = new ArthasPackageService();
        Path temporaryFile = Files.createTempFile("arthas-workbench", ".jar");

        try {
            packageService.resolve(
                    new PackageSourceSpec(PackageSourceType.LOCAL_PATH, temporaryFile.toString()), false);
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("目录"));
            return;
        } finally {
            Files.deleteIfExists(temporaryFile);
        }

        throw new AssertionError("Expected LOCAL_PATH to reject file input.");
    }
}
