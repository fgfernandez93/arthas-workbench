package com.alibaba.arthas.idea.workbench.model;

import java.util.Objects;

/**
 * 用于 Workbench 列表展示的 JVM 进程信息。
 */
public final class JvmProcessInfo {

    private final long pid;
    private final String displayName;
    private final ProcessOrigin origin;

    public JvmProcessInfo(long pid, String displayName, ProcessOrigin origin) {
        this.pid = pid;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    public long getPid() {
        return pid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ProcessOrigin getOrigin() {
        return origin;
    }
}
