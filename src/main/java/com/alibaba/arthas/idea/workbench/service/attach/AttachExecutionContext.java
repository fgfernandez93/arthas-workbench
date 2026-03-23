package com.alibaba.arthas.idea.workbench.service.attach;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.ResolvedArthasPackage;
import com.intellij.openapi.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 汇总单次 attach 执行所需的项目、包和会话上下文。
 */
public final class AttachExecutionContext {

    private final Project project;
    private final ResolvedArthasPackage resolvedPackage;
    private final ArthasSession session;

    public AttachExecutionContext(Project project, ResolvedArthasPackage resolvedPackage, ArthasSession session) {
        this.project = Objects.requireNonNull(project, "project");
        this.resolvedPackage = Objects.requireNonNull(resolvedPackage, "resolvedPackage");
        this.session = Objects.requireNonNull(session, "session");
    }

    public Project getProject() {
        return project;
    }

    public ResolvedArthasPackage getResolvedPackage() {
        return resolvedPackage;
    }

    public ArthasSession getSession() {
        return session;
    }

    /**
     * 返回完整的 Arthas Home 目录；某些 attach 策略依赖其中的 agent/core jar。
     */
    public Path requireArthasHome() {
        Path arthasHome = resolvedPackage.getArthasHome();
        if (arthasHome == null) {
            throw new IllegalStateException(message("service.attach.context.error.home_missing"));
        }
        return arthasHome;
    }

    public Path requireArthasAgentJar() {
        Path agentJar = requireArthasHome().resolve("arthas-agent.jar");
        if (!Files.isRegularFile(agentJar)) {
            throw new IllegalStateException(message("service.attach.context.error.agent_missing", agentJar));
        }
        return agentJar;
    }

    public Path requireArthasCoreJar() {
        Path coreJar = requireArthasHome().resolve("arthas-core.jar");
        if (!Files.isRegularFile(coreJar)) {
            throw new IllegalStateException(message("service.attach.context.error.core_missing", coreJar));
        }
        return coreJar;
    }

    private static String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }
}
