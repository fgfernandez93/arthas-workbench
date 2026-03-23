package com.alibaba.arthas.idea.workbench.model;

import java.util.List;
import java.util.Objects;

/**
 * 一次 JVM 进程扫描后的结果快照，同时保留全部进程和 IDEA 管理进程子集。
 */
public final class ProcessSnapshot {

    private final List<JvmProcessInfo> ideaProcesses;
    private final List<JvmProcessInfo> allProcesses;

    public ProcessSnapshot(List<JvmProcessInfo> ideaProcesses, List<JvmProcessInfo> allProcesses) {
        this.ideaProcesses = List.copyOf(Objects.requireNonNull(ideaProcesses, "ideaProcesses"));
        this.allProcesses = List.copyOf(Objects.requireNonNull(allProcesses, "allProcesses"));
    }

    public List<JvmProcessInfo> getIdeaProcesses() {
        return ideaProcesses;
    }

    public List<JvmProcessInfo> getAllProcesses() {
        return allProcesses;
    }
}
