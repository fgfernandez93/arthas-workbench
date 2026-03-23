package com.alibaba.arthas.idea.workbench.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.alibaba.arthas.idea.workbench.model.JvmProcessInfo;
import com.alibaba.arthas.idea.workbench.model.ProcessOrigin;
import com.alibaba.arthas.idea.workbench.model.ProcessSnapshot;
import com.alibaba.arthas.idea.workbench.service.ArthasSessionService;
import java.util.List;
import org.junit.Test;

/**
 * {@link ArthasWorkbenchPanel.ProcessTableModel} 的基本回归测试。
 */
public class ArthasWorkbenchPanelTest {

    @Test
    /**
     * 验证空表格在刷新会话状态时不会抛异常。
     */
    public void shouldRefreshEmptyProcessTableModelWithoutThrowing() {
        ArthasWorkbenchPanel.ProcessTableModel model = new ArthasWorkbenchPanel.ProcessTableModel();

        model.refreshSessionState(new ArthasSessionService());

        assertEquals(0, model.getRowCount());
    }

    @Test
    /**
     * 验证进程分页会按来源稳定拆分为 IDEA 与本地 JVM 两组。
     */
    public void shouldSplitProcessSnapshotByOriginTabs() {
        JvmProcessInfo ideaProcess = new JvmProcessInfo(1001L, "demo.idea.Main", ProcessOrigin.IDEA_RUN_DEBUG);
        JvmProcessInfo localProcess = new JvmProcessInfo(2002L, "demo.local.Main", ProcessOrigin.LOCAL_JVM);
        ProcessSnapshot snapshot = new ProcessSnapshot(List.of(ideaProcess), List.of(ideaProcess, localProcess));

        List<JvmProcessInfo> ideaProcesses = ArthasWorkbenchPanel.ProcessListTab.IDEA.filter(snapshot);
        List<JvmProcessInfo> localProcesses = ArthasWorkbenchPanel.ProcessListTab.LOCAL.filter(snapshot);

        assertEquals(1, ideaProcesses.size());
        assertEquals(ideaProcess, ideaProcesses.get(0));
        assertEquals(1, localProcesses.size());
        assertEquals(localProcess, localProcesses.get(0));
    }

    @Test
    /**
     * 验证超长 JVM 显示名会在表格中被压缩，避免把整条命令行直接渲染到 Swing 组件里。
     */
    public void shouldCompactVeryLongProcessDisplayNameForTableCell() {
        ArthasWorkbenchPanel.ProcessTableModel model = new ArthasWorkbenchPanel.ProcessTableModel();
        String longDisplayName =
                "org.jetbrains.jps.cmdline.Launcher /Users/weil/.gradle/caches/modules-2/files-2.1/some-very-long-path/"
                        + "another-segment/and-even-more-segments/demo.jar --classpath /Users/weil/Desktop/workspaces/"
                        + "opensource/arthas/idea-plugin/arthas-workbench/build/generated/really/long/value";

        model.update(
                List.of(new JvmProcessInfo(3003L, longDisplayName, ProcessOrigin.LOCAL_JVM)),
                new ArthasSessionService());

        Object displayValue = model.getValueAt(0, 2);

        assertEquals(String.class, displayValue.getClass());
        assertTrue(displayValue.toString().length() < longDisplayName.length());
        assertTrue(displayValue.toString().contains("..."));
    }

    @Test
    /**
     * 验证端口字段的悬停提示会明确区分 HTTP 和 Telnet 两个端口。
     */
    public void shouldBuildStructuredPortsTooltip() {
        String tooltip = ArthasWorkbenchPanel.buildPortsTooltip(8563, 3658);

        assertTrue(tooltip.contains("HTTP"));
        assertTrue(tooltip.contains("Telnet"));
        assertTrue(tooltip.contains("8563"));
        assertTrue(tooltip.contains("3658"));
    }
}
