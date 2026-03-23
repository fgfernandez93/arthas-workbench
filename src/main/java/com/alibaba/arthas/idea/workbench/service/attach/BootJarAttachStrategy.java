package com.alibaba.arthas.idea.workbench.service.attach;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.AttachStrategyType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 使用 arthas-boot.jar 的官方 attach 模式 Attach 目标 JVM。
 */
public final class BootJarAttachStrategy extends AbstractCommandAttachStrategy {

    @Override
    public AttachStrategyType getType() {
        return AttachStrategyType.ARTHAS_BOOT;
    }

    @Override
    public ArthasSession attach(AttachExecutionContext context, Consumer<String> logLine) {
        ArthasSession session = context.getSession();
        List<String> command = buildAttachCommand(
                context.getResolvedPackage().getBootJar(),
                context.getResolvedPackage().getArthasHome(),
                session.getPid(),
                session.getHttpPort(),
                session.getTelnetPort(),
                session.getMcpEndpoint(),
                session.getMcpPassword(),
                session.getJavaExecutablePath());
        logLine.accept(message("service.attach.strategy.log.type", getType().getDisplayName()));
        logLine.accept(message("service.attach.strategy.log.java", session.getJavaExecutablePath()));
        logLine.accept(message("service.attach.strategy.log.command", String.join(" ", command)));
        return runAttachCommand(context, command, logLine);
    }

    /**
     * 构造 Arthas Boot attach 命令行。
     */
    private List<String> buildAttachCommand(
            Path bootJar,
            Path arthasHome,
            long pid,
            int httpPort,
            int telnetPort,
            String mcpEndpoint,
            String mcpPassword,
            String javaExecutable) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add("-Darthas.mcpEndpoint=" + mcpEndpoint);
        command.add("-Darthas.config.overrideAll=true");
        if (!mcpPassword.isBlank()) {
            command.add("-Darthas.password=" + mcpPassword);
        }
        command.add("-jar");
        command.add(bootJar.toAbsolutePath().toString());
        command.add("--attach-only");
        command.add("--target-ip");
        command.add("127.0.0.1");
        command.add("--http-port");
        command.add(String.valueOf(httpPort));
        command.add("--telnet-port");
        command.add(String.valueOf(telnetPort));
        command.add("--session-timeout");
        command.add("86400");
        if (arthasHome != null) {
            command.add("--arthas-home");
            command.add(arthasHome.toAbsolutePath().toString());
        }
        command.add(String.valueOf(pid));
        return command;
    }

    private static String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }
}
