package com.alibaba.arthas.idea.workbench.service;

import com.alibaba.arthas.idea.workbench.model.McpPasswordMode;
import com.alibaba.arthas.idea.workbench.model.PackageSourceSpec;
import com.alibaba.arthas.idea.workbench.model.PortAllocationMode;
import java.util.Objects;

/**
 * 描述一次“开启 Arthas”操作所需的全部输入参数。
 */
public final class AttachRequest {

    private final String sessionId;
    private final long processPid;
    private final String processDisplayName;
    private final PackageSourceSpec packageSource;
    private final Integer preferredHttpPort;
    private final Integer preferredTelnetPort;
    private final PortAllocationMode portAllocationMode;
    private final String mcpEndpoint;
    private final McpPasswordMode mcpPasswordMode;
    private final String mcpPassword;
    private final boolean forcePackageUpdate;

    /**
     * 构造 attach 请求，并在缺省场景下补齐默认策略值。
     */
    public AttachRequest(
            String sessionId,
            long processPid,
            String processDisplayName,
            PackageSourceSpec packageSource,
            Integer preferredHttpPort,
            Integer preferredTelnetPort,
            PortAllocationMode portAllocationMode,
            String mcpEndpoint,
            McpPasswordMode mcpPasswordMode,
            String mcpPassword,
            boolean forcePackageUpdate) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.processPid = processPid;
        this.processDisplayName = Objects.requireNonNull(processDisplayName, "processDisplayName");
        this.packageSource = Objects.requireNonNull(packageSource, "packageSource");
        this.preferredHttpPort = preferredHttpPort;
        this.preferredTelnetPort = preferredTelnetPort;
        this.portAllocationMode =
                portAllocationMode == null ? PortAllocationMode.PREFER_CONFIGURED : portAllocationMode;
        this.mcpEndpoint = Objects.requireNonNull(mcpEndpoint, "mcpEndpoint");
        this.mcpPasswordMode = mcpPasswordMode == null ? McpPasswordMode.RANDOM : mcpPasswordMode;
        this.mcpPassword = Objects.requireNonNull(mcpPassword, "mcpPassword");
        this.forcePackageUpdate = forcePackageUpdate;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getProcessPid() {
        return processPid;
    }

    public String getProcessDisplayName() {
        return processDisplayName;
    }

    public PackageSourceSpec getPackageSource() {
        return packageSource;
    }

    public Integer getPreferredHttpPort() {
        return preferredHttpPort;
    }

    public Integer getPreferredTelnetPort() {
        return preferredTelnetPort;
    }

    public PortAllocationMode getPortAllocationMode() {
        return portAllocationMode;
    }

    public String getMcpEndpoint() {
        return mcpEndpoint;
    }

    public McpPasswordMode getMcpPasswordMode() {
        return mcpPasswordMode;
    }

    public String getMcpPassword() {
        return mcpPassword;
    }

    public boolean isForcePackageUpdate() {
        return forcePackageUpdate;
    }
}
