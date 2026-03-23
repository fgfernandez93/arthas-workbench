package com.alibaba.arthas.idea.workbench.util;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.PortAllocationMode;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * 负责端口选择、占用检测以及 attach 阶段的端口就绪等待。
 */
public final class PortToolkit {

    private PortToolkit() {}

    public static int resolvePort(Integer preferred, int fallback, PortAllocationMode mode, int... reservedPorts) {
        PortAllocationMode allocationMode = mode == null ? PortAllocationMode.PREFER_CONFIGURED : mode;
        return switch (allocationMode) {
            case ALWAYS_AUTO -> randomAvailablePort(reservedPorts);
            case STRICT_CONFIGURED -> resolveStrictPort(preferred, fallback, reservedPorts);
            case PREFER_CONFIGURED -> resolvePreferredPort(preferred, fallback, reservedPorts);
        };
    }

    private static int resolveStrictPort(Integer preferred, int fallback, int... reservedPorts) {
        int candidate = normalizeCandidate(preferred, fallback);
        if (!canUse(candidate, reservedPorts)) {
            throw new IllegalStateException(message("util.port.error.occupied", candidate));
        }
        return candidate;
    }

    private static int resolvePreferredPort(Integer preferred, int fallback, int... reservedPorts) {
        int candidate = normalizeCandidate(preferred, fallback);
        if (canUse(candidate, reservedPorts)) {
            return candidate;
        }
        return randomAvailablePort(reservedPorts);
    }

    private static int normalizeCandidate(Integer preferred, int fallback) {
        return preferred != null && preferred > 0 ? preferred : fallback;
    }

    private static int randomAvailablePort(int... reservedPorts) {
        for (int attempt = 0; attempt < 32; attempt++) {
            try (ServerSocket socket = new ServerSocket(0)) {
                int candidate = socket.getLocalPort();
                if (!isReserved(candidate, reservedPorts)) {
                    return candidate;
                }
            } catch (IOException exception) {
                // 继续尝试随机端口。
            }
        }
        throw new IllegalStateException(message("util.port.error.auto_allocate"));
    }

    private static boolean canUse(int port, int... reservedPorts) {
        return !isReserved(port, reservedPorts) && isAvailable(port);
    }

    private static boolean isReserved(int port, int... reservedPorts) {
        if (reservedPorts == null) {
            return false;
        }
        for (int reservedPort : reservedPorts) {
            if (reservedPort == port) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public static boolean waitForPortOpen(String host, int port, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 500);
                return true;
            } catch (IOException ignored) {
                // 端口还未开放时继续等待。
            }
            try {
                Thread.sleep(300L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }
}
