package com.alibaba.arthas.idea.workbench.model;

import java.util.Objects;

/**
 * 描述一次已创建的 Arthas 会话，包括连接端口、包来源和当前运行状态。
 */
public final class ArthasSession {

    private final String id;
    private final long pid;
    private final String displayName;
    private final int httpPort;
    private final int telnetPort;
    private final String mcpEndpoint;
    private final String mcpPassword;
    private final String packageLabel;
    private final String attachStrategyLabel;
    private final String javaExecutablePath;
    private final String bootJarPath;
    private final String arthasHomePath;
    private final SessionStatus status;

    /**
     * 创建会话快照；对象设计为不可变，状态变化通过 {@link #withStatus(SessionStatus)} 派生新实例。
     */
    public ArthasSession(
            String id,
            long pid,
            String displayName,
            int httpPort,
            int telnetPort,
            String mcpEndpoint,
            String mcpPassword,
            String packageLabel,
            String attachStrategyLabel,
            String javaExecutablePath,
            String bootJarPath,
            String arthasHomePath,
            SessionStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.pid = pid;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.httpPort = httpPort;
        this.telnetPort = telnetPort;
        this.mcpEndpoint = Objects.requireNonNull(mcpEndpoint, "mcpEndpoint");
        this.mcpPassword = Objects.requireNonNull(mcpPassword, "mcpPassword");
        this.packageLabel = Objects.requireNonNull(packageLabel, "packageLabel");
        this.attachStrategyLabel = Objects.requireNonNull(attachStrategyLabel, "attachStrategyLabel");
        this.javaExecutablePath = Objects.requireNonNull(javaExecutablePath, "javaExecutablePath");
        this.bootJarPath = Objects.requireNonNull(bootJarPath, "bootJarPath");
        this.arthasHomePath = arthasHomePath;
        this.status = Objects.requireNonNull(status, "status");
    }

    public String getId() {
        return id;
    }

    public long getPid() {
        return pid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public int getTelnetPort() {
        return telnetPort;
    }

    public String getMcpEndpoint() {
        return mcpEndpoint;
    }

    public String getMcpPassword() {
        return mcpPassword;
    }

    public String getPackageLabel() {
        return packageLabel;
    }

    public String getAttachStrategyLabel() {
        return attachStrategyLabel;
    }

    public String getJavaExecutablePath() {
        return javaExecutablePath;
    }

    public String getBootJarPath() {
        return bootJarPath;
    }

    public String getArthasHomePath() {
        return arthasHomePath;
    }

    public SessionStatus getStatus() {
        return status;
    }

    /**
     * 返回 Arthas 根访问地址。
     */
    public String getRootUrl() {
        return "http://127.0.0.1:" + httpPort + "/";
    }

    /**
     * 返回 Arthas Web UI 地址。
     */
    public String getHttpApiUiUrl() {
        return "http://127.0.0.1:" + httpPort + "/ui";
    }

    /**
     * 返回当前会话的 MCP 访问地址。
     */
    public String getMcpUrl() {
        return "http://127.0.0.1:" + httpPort + normalizeEndpoint(mcpEndpoint);
    }

    /**
     * 在保留其余字段不变的前提下生成不同状态的新会话对象。
     */
    public ArthasSession withStatus(SessionStatus newStatus) {
        return new ArthasSession(
                id,
                pid,
                displayName,
                httpPort,
                telnetPort,
                mcpEndpoint,
                mcpPassword,
                packageLabel,
                attachStrategyLabel,
                javaExecutablePath,
                bootJarPath,
                arthasHomePath,
                newStatus);
    }

    private String normalizeEndpoint(String endpoint) {
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }
}
