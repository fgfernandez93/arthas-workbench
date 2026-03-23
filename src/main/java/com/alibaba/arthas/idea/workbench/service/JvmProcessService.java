package com.alibaba.arthas.idea.workbench.service;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.JvmProcessInfo;
import com.alibaba.arthas.idea.workbench.model.ProcessOrigin;
import com.alibaba.arthas.idea.workbench.model.ProcessSnapshot;
import com.intellij.execution.ExecutionManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 发现当前机器上的本地 JVM，并尽量标记出由 IDEA Run/Debug 启动的进程。
 */
@Service(Service.Level.PROJECT)
public final class JvmProcessService {

    private final Project project;

    public JvmProcessService(Project project) {
        this.project = project;
    }

    /**
     * 返回给 Workbench 展示的 JVM 快照，结果会按 IDEA 进程优先、PID 次序排序。
     */
    public ProcessSnapshot listProcesses() {
        Set<Long> ideaPids = detectIdeaManagedPids();
        List<JvmProcessInfo> all = new ArrayList<>();
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            Long pid = parsePid(descriptor.id());
            if (pid == null) {
                continue;
            }
            String displayName = descriptor.displayName().isBlank()
                    ? ArthasWorkbenchBundle.message("process.display.unknown")
                    : descriptor.displayName();
            ProcessOrigin origin = ideaPids.contains(pid) ? ProcessOrigin.IDEA_RUN_DEBUG : ProcessOrigin.LOCAL_JVM;
            all.add(new JvmProcessInfo(pid, displayName, origin));
        }
        all.sort(Comparator.comparing((JvmProcessInfo info) -> info.getOrigin() != ProcessOrigin.IDEA_RUN_DEBUG)
                .thenComparingLong(JvmProcessInfo::getPid));

        List<JvmProcessInfo> ideaProcesses = all.stream()
                .filter(info -> info.getOrigin() == ProcessOrigin.IDEA_RUN_DEBUG)
                .toList();
        return new ProcessSnapshot(ideaProcesses, all);
    }

    private Set<Long> detectIdeaManagedPids() {
        try {
            ExecutionManager executionManager = ExecutionManager.getInstance(project);
            Method method = null;
            for (Method item : executionManager.getClass().getMethods()) {
                if ("getRunningProcesses".equals(item.getName()) && item.getParameterCount() == 0) {
                    method = item;
                    break;
                }
            }
            if (method == null) {
                return Collections.emptySet();
            }
            Object result = method.invoke(executionManager);
            return extractPids(result);
        } catch (Exception exception) {
            return Collections.emptySet();
        }
    }

    private Set<Long> extractPids(Object result) {
        List<?> handlers;
        if (result instanceof Object[] array) {
            handlers = List.of(array);
        } else if (result instanceof Collection<?> collection) {
            handlers = new ArrayList<>(collection);
        } else {
            handlers = Collections.emptyList();
        }

        Set<Long> pids = new LinkedHashSet<>();
        for (Object handler : handlers) {
            if (handler == null) {
                continue;
            }
            Long pid = extractPidByReflection(handler);
            if (pid != null) {
                pids.add(pid);
            }
        }
        return pids;
    }

    private Long extractPidByReflection(Object handler) {
        Method[] methods = handler.getClass().getMethods();
        Method pidMethod = null;
        Method processMethod = null;
        Method processHandlerMethod = null;
        for (Method method : methods) {
            if ("getPid".equals(method.getName()) && method.getParameterCount() == 0) {
                pidMethod = method;
            }
            if ("getProcess".equals(method.getName())
                    && method.getParameterCount() == 0
                    && Process.class.isAssignableFrom(method.getReturnType())) {
                processMethod = method;
            }
            if ("getProcessHandler".equals(method.getName()) && method.getParameterCount() == 0) {
                processHandlerMethod = method;
            }
        }

        try {
            if (pidMethod != null) {
                Object pid = pidMethod.invoke(handler);
                if (pid instanceof Long value) {
                    return value;
                }
                if (pid instanceof Integer value) {
                    return value.longValue();
                }
            }
            if (processMethod != null) {
                Object process = processMethod.invoke(handler);
                if (process instanceof Process value) {
                    return value.pid();
                }
            }
            if (processHandlerMethod != null) {
                Object next = processHandlerMethod.invoke(handler);
                if (next != null && next != handler) {
                    return extractPidByReflection(next);
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Long parsePid(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
