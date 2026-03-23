package com.alibaba.arthas.idea.workbench.model;

/**
 * 标记 JVM 进程的来源，用于在列表中区分 IDEA 管理进程与普通本地进程。
 */
public enum ProcessOrigin {
    IDEA_RUN_DEBUG,
    LOCAL_JVM
}
