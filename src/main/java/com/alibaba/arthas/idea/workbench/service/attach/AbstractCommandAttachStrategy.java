package com.alibaba.arthas.idea.workbench.service.attach;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import com.alibaba.arthas.idea.workbench.util.PortToolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 复用基于外部命令执行的 attach 流程，统一处理日志采集、端口等待和失败诊断。
 */
abstract class AbstractCommandAttachStrategy implements AttachStrategy {

    /**
     * 执行 attach 命令并等待 HTTP 端口开放，成功后返回 RUNNING 状态的会话。
     */
    protected ArthasSession runAttachCommand(
            AttachExecutionContext context, List<String> command, Consumer<String> logLine) {
        Process process = startProcess(context, command);
        StringBuilder outputBuffer = new StringBuilder();
        Thread outputPump = pumpOutput(process, line -> {
            outputBuffer.append(line).append('\n');
            logLine.accept(line);
        });
        int httpPort = context.getSession().getHttpPort();
        boolean portOpened = PortToolkit.waitForPortOpen("127.0.0.1", httpPort, 45_000L);
        boolean finished;
        try {
            finished = process.waitFor(45, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message("service.attach.strategy.error.interrupted"), exception);
        }
        joinOutputPump(outputPump);

        if (finished && process.exitValue() != 0 && !portOpened) {
            throw new IllegalStateException(buildAttachFailureMessage(process.exitValue(), outputBuffer.toString()));
        }
        if (!portOpened) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            throw new IllegalStateException(buildAttachTimeoutMessage(httpPort, outputBuffer.toString()));
        }

        logLine.accept(message(
                "service.attach.strategy.log.success",
                httpPort,
                context.getSession().getMcpUrl()));
        return context.getSession().withStatus(SessionStatus.RUNNING);
    }

    private Process startProcess(AttachExecutionContext context, List<String> command) {
        try {
            return new ProcessBuilder(command)
                    .directory(
                            context.getProject().getBasePath() == null
                                    ? null
                                    : new File(context.getProject().getBasePath()))
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new IllegalStateException(message("service.attach.strategy.error.start_failed"), exception);
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
                        logLine.accept(
                                message("service.attach.strategy.log.read_output_failed", exception.getMessage()));
                    }
                },
                "arthas-attach-log-pump");
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

    private String buildAttachFailureMessage(int exitCode, String output) {
        if (output.contains("Premature EOF")) {
            return message("service.attach.strategy.error.premature_eof");
        }
        return message("service.attach.strategy.error.exit_code", exitCode);
    }

    private String buildAttachTimeoutMessage(int httpPort, String output) {
        if (output.contains("Premature EOF")) {
            return message("service.attach.strategy.error.timeout_premature_eof", httpPort);
        }
        if (output.contains("Attach process") && output.contains("success")) {
            return message("service.attach.strategy.error.success_but_not_open", httpPort);
        }
        return message("service.attach.strategy.error.timeout", httpPort);
    }

    private static String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }
}
