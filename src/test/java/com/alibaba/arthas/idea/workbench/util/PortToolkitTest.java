package com.alibaba.arthas.idea.workbench.util;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.alibaba.arthas.idea.workbench.model.PortAllocationMode;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.Test;

/**
 * {@link PortToolkit} 端口分配策略测试。
 */
public class PortToolkitTest {

    @Test
    /**
     * 验证首选端口被占用时，会在允许自动寻找的模式下分配其他端口。
     */
    public void shouldAllocateRandomPortWhenConfiguredPortIsBusy() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            int preferred = occupied.getLocalPort();
            int resolved = PortToolkit.resolvePort(preferred, preferred, PortAllocationMode.PREFER_CONFIGURED);
            assertNotEquals(preferred, resolved);
            assertTrue(resolved > 0);
        }
    }

    @Test(expected = IllegalStateException.class)
    /**
     * 验证严格模式下端口冲突会直接失败。
     */
    public void shouldFailWhenStrictConfiguredPortIsBusy() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            int preferred = occupied.getLocalPort();
            PortToolkit.resolvePort(preferred, preferred, PortAllocationMode.STRICT_CONFIGURED);
        }
    }

    @Test
    /**
     * 验证自动分配端口时会避开已保留的端口。
     */
    public void shouldAvoidReservedPortWhenAutoAllocating() throws IOException {
        try (ServerSocket reserved = new ServerSocket(0)) {
            int reservedPort = reserved.getLocalPort();
            int allocated = PortToolkit.resolvePort(null, 0, PortAllocationMode.ALWAYS_AUTO, reservedPort);
            assertNotEquals(reservedPort, allocated);
            assertTrue(allocated > 0);
        }
    }
}
