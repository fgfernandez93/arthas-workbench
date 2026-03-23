package com.alibaba.arthas.idea.workbench.service.attach;

import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.AttachStrategyType;
import java.util.function.Consumer;

/**
 * attach 策略抽象。
 * 当前默认仅保留 arthas-boot 实现，后续如需扩展其他实现，仍可复用这层接口。
 */
public interface AttachStrategy {

    /**
     * 返回策略类型，用于回显给 UI 和日志。
     */
    AttachStrategyType getType();

    /**
     * 使用当前策略将 Arthas  Attach 到目标 JVM。
     */
    ArthasSession attach(AttachExecutionContext context, Consumer<String> logLine);
}
