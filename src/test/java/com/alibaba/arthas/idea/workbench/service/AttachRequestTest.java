package com.alibaba.arthas.idea.workbench.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.alibaba.arthas.idea.workbench.model.McpPasswordMode;
import com.alibaba.arthas.idea.workbench.model.PackageSourceSpec;
import com.alibaba.arthas.idea.workbench.model.PackageSourceType;
import com.alibaba.arthas.idea.workbench.model.PortAllocationMode;
import org.junit.Test;

/**
 * {@link AttachRequest} 的默认值行为测试。
 */
public class AttachRequestTest {

    @Test
    /**
     * 验证未显式指定端口策略和密码策略时，会自动回退到默认值。
     */
    public void shouldFallbackToDefaultModesWhenOptionalModesAreNull() {
        AttachRequest request = new AttachRequest(
                "session-1",
                48619L,
                "demo.MathGame",
                new PackageSourceSpec(PackageSourceType.OFFICIAL_LATEST, ""),
                null,
                null,
                null,
                "/mcp",
                null,
                "",
                false);

        assertEquals(PortAllocationMode.PREFER_CONFIGURED, request.getPortAllocationMode());
        assertEquals(McpPasswordMode.RANDOM, request.getMcpPasswordMode());
        assertEquals("/mcp", request.getMcpEndpoint());
        assertEquals("demo.MathGame", request.getProcessDisplayName());
        assertFalse(request.isForcePackageUpdate());
    }
}
