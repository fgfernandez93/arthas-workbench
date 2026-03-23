package com.alibaba.arthas.idea.workbench.service;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.AttachStrategyType;
import com.alibaba.arthas.idea.workbench.model.PortAllocationMode;
import com.alibaba.arthas.idea.workbench.model.ResolvedArthasPackage;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import com.alibaba.arthas.idea.workbench.service.attach.AttachExecutionContext;
import com.alibaba.arthas.idea.workbench.service.attach.AttachStrategy;
import com.alibaba.arthas.idea.workbench.service.attach.BootJarAttachStrategy;
import com.alibaba.arthas.idea.workbench.util.PortToolkit;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.sun.tools.attach.VirtualMachine;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 统一封装 Arthas attach/stop 生命周期，并负责在真正 Attach 前补齐端口、密码和 Java 可执行文件等上下文。
 */
@Service(Service.Level.PROJECT)
public final class ArthasAttachService {

    private final Project project;
    private final ArthasPackageService packageService;

    public ArthasAttachService(Project project) {
        this.project = project;
        this.packageService = ApplicationManager.getApplication().getService(ArthasPackageService.class);
    }

    /**
     * 根据用户当前设置准备 Arthas 会话，并调用具体的 attach 策略完成 Attach 。
     */
    public ArthasSession attach(AttachRequest request, Consumer<String> logLine) {
        ResolvedArthasPackage resolvedPackage =
                packageService.resolve(request.getPackageSource(), request.isForcePackageUpdate());
        PortAllocationMode portAllocationMode = request.getPortAllocationMode();
        int httpPort = PortToolkit.resolvePort(request.getPreferredHttpPort(), 8563, portAllocationMode);
        int telnetPort = PortToolkit.resolvePort(request.getPreferredTelnetPort(), 3658, portAllocationMode, httpPort);
        String password = resolveMcpPassword(request);
        String javaExecutable = resolveAttachJavaExecutable(request.getProcessPid(), logLine);
        AttachStrategyType strategyType = AttachStrategyType.ARTHAS_BOOT;

        ArthasSession session = new ArthasSession(
                request.getSessionId(),
                request.getProcessPid(),
                request.getProcessDisplayName(),
                httpPort,
                telnetPort,
                normalizeMcpEndpoint(request.getMcpEndpoint()),
                password,
                resolvedPackage.getLabel(),
                strategyType.getDisplayName(),
                javaExecutable,
                resolvedPackage.getBootJar().toAbsolutePath().toString(),
                resolvedPackage.getArthasHome() == null
                        ? null
                        : resolvedPackage.getArthasHome().toAbsolutePath().toString(),
                SessionStatus.ATTACHING);

        logLine.accept(message("service.attach.log.prepare", request.getProcessPid(), request.getProcessDisplayName()));
        logLine.accept(message("service.attach.log.package", resolvedPackage.getLabel()));
        logLine.accept(message("service.attach.log.password_mode", request.getMcpPasswordMode()));
        AttachExecutionContext context = new AttachExecutionContext(project, resolvedPackage, session);
        return createAttachStrategy().attach(context, logLine);
    }

    /**
     * 通过 arthas-boot stop 命令关闭指定会话。
     */
    public ArthasSession stop(ArthasSession session, Consumer<String> logLine) {
        List<String> command = new ArrayList<>();
        command.add(session.getJavaExecutablePath());
        if (!session.getMcpPassword().isBlank()) {
            command.add("-Darthas.password=" + session.getMcpPassword());
        }
        command.add("-jar");
        command.add(session.getBootJarPath());
        command.add("-c");
        command.add("stop");
        command.add("--http-port");
        command.add(String.valueOf(session.getHttpPort()));
        command.add("--telnet-port");
        command.add(String.valueOf(session.getTelnetPort()));
        if (session.getArthasHomePath() != null && !session.getArthasHomePath().isBlank()) {
            command.add("--arthas-home");
            command.add(session.getArthasHomePath());
        }
        command.add(String.valueOf(session.getPid()));

        logLine.accept(message("service.attach.log.stop_command", String.join(" ", command)));
        Process process = startProcess(command);
        Thread outputPump = pumpOutput(process, logLine);
        try {
            process.waitFor(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message("service.attach.error.stop_interrupted"), exception);
        }
        joinOutputPump(outputPump);
        return session.withStatus(SessionStatus.STOPPED);
    }

    private AttachStrategy createAttachStrategy() {
        return new BootJarAttachStrategy();
    }

    private Process startProcess(List<String> command) {
        try {
            return new ProcessBuilder(command)
                    .directory(project.getBasePath() == null ? null : new File(project.getBasePath()))
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new IllegalStateException(message("service.attach.error.start_command"), exception);
        }
    }

    private Thread pumpOutput(Process process, Consumer<String> logLine) {
        Thread thread = new Thread(
                () -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logLine.accept(line);
                        }
                    } catch (IOException exception) {
                        logLine.accept(message("service.attach.log.read_output_failed", exception.getMessage()));
                    }
                },
                "arthas-stop-log-pump");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void joinOutputPump(Thread outputPump) {
        try {
            outputPump.join(1_000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String javaExecutable() {
        return new File(System.getProperty("java.home"), "bin/java").getPath();
    }

    String resolveAttachJavaExecutable(long pid, Consumer<String> logLine) {
        String fallback = javaExecutable();
        Properties properties = readTargetSystemProperties(pid, logLine);
        if (properties == null) {
            logLine.accept(message("service.attach.log.properties_fallback"));
            return fallback;
        }
        String targetJavaHome = properties.getProperty("java.home");
        String targetJavaExecutable = resolveJavaExecutableFromJavaHome(targetJavaHome);
        if (targetJavaExecutable != null) {
            return targetJavaExecutable;
        }
        if (targetJavaHome != null && !targetJavaHome.isBlank()) {
            logLine.accept(message("service.attach.log.target_java_unresolved", targetJavaHome));
        }
        return fallback;
    }

    static String resolveJavaExecutableFromJavaHome(String javaHome) {
        if (javaHome == null || javaHome.isBlank()) {
            return null;
        }
        Path home = Path.of(javaHome);
        Path direct = home.resolve("bin").resolve("java");
        if (Files.isRegularFile(direct)) {
            return direct.toString();
        }
        if (home.getFileName() != null && "jre".equals(home.getFileName().toString()) && home.getParent() != null) {
            Path parentJava = home.getParent().resolve("bin").resolve("java");
            if (Files.isRegularFile(parentJava)) {
                return parentJava.toString();
            }
        }
        return null;
    }

    static String resolveMcpPassword(AttachRequest request) {
        return switch (request.getMcpPasswordMode()) {
            case RANDOM -> UUID.randomUUID().toString().replace("-", "");
            case FIXED -> {
                String configuredPassword = request.getMcpPassword().trim();
                if (configuredPassword.isBlank()) {
                    throw new IllegalArgumentException(message("service.attach.error.password_required"));
                }
                yield configuredPassword;
            }
            case DISABLED -> "";
        };
    }

    private Properties readTargetSystemProperties(long pid, Consumer<String> logLine) {
        VirtualMachine virtualMachine = null;
        try {
            virtualMachine = VirtualMachine.attach(String.valueOf(pid));
            Properties properties = virtualMachine.getSystemProperties();
            String javaHome = properties.getProperty("java.home");
            if (javaHome != null && !javaHome.isBlank()) {
                logLine.accept(message("service.attach.log.target_java_home", javaHome));
            }
            return properties;
        } catch (Exception exception) {
            logLine.accept(message("service.attach.log.read_properties_failed", exception.getMessage()));
            return null;
        } finally {
            if (virtualMachine != null) {
                try {
                    virtualMachine.detach();
                } catch (IOException ignored) {
                    // 读取属性后 detach 失败时忽略。
                }
            }
        }
    }

    private String normalizeMcpEndpoint(String value) {
        String raw = value == null || value.isBlank() ? "/mcp" : value.trim();
        return raw.startsWith("/") ? raw : "/" + raw;
    }

    private static String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }
}
