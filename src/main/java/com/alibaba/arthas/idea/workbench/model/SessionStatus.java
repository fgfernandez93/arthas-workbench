package com.alibaba.arthas.idea.workbench.model;

/**
 * 描述 Arthas 会话的生命周期状态。
 */
public enum SessionStatus {
    ATTACHING,
    RUNNING,
    FAILED,
    STOPPED
}
