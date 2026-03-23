package com.alibaba.arthas.idea.workbench.ui;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.ArthasSessionViewType;
import com.alibaba.arthas.idea.workbench.model.AttachStrategyType;
import com.alibaba.arthas.idea.workbench.model.JvmProcessInfo;
import com.alibaba.arthas.idea.workbench.model.McpPasswordMode;
import com.alibaba.arthas.idea.workbench.model.PackageSourceSpec;
import com.alibaba.arthas.idea.workbench.model.PortAllocationMode;
import com.alibaba.arthas.idea.workbench.model.ProcessOrigin;
import com.alibaba.arthas.idea.workbench.model.ProcessSnapshot;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import com.alibaba.arthas.idea.workbench.service.ArthasAttachService;
import com.alibaba.arthas.idea.workbench.service.ArthasMcpGatewayService;
import com.alibaba.arthas.idea.workbench.service.ArthasSessionService;
import com.alibaba.arthas.idea.workbench.service.ArthasWorkbenchSettingsService;
import com.alibaba.arthas.idea.workbench.service.AttachRequest;
import com.alibaba.arthas.idea.workbench.service.JvmProcessService;
import com.alibaba.arthas.idea.workbench.settings.ArthasWorkbenchConfigurable;
import com.alibaba.arthas.idea.workbench.util.McpConfigFormatter;
import com.alibaba.arthas.idea.workbench.util.UiToolkit;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.BaseProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Workbench 主面板。
 * 这里负责本地 JVM 发现、会话状态汇总、Attach 入口以及与其他 Tool Window 的协同。
 */
public final class ArthasWorkbenchPanel extends JPanel implements Disposable {

    static final String TOOL_WINDOW_ID = "Arthas Workbench";
    static final String SESSIONS_TOOL_WINDOW_ID = "Arthas Sessions";
    private static final int PROCESS_TABLE_TEXT_MAX_LENGTH = 96;
    private static final int PROCESS_TITLE_TEXT_MAX_LENGTH = 72;
    private static final int PROCESS_TOOLTIP_TEXT_MAX_LENGTH = 160;

    private final Project project;
    private final ArthasWorkbenchSettingsService settings;
    private final JvmProcessService processService;
    private final ArthasAttachService attachService;
    private final ArthasSessionService sessionService;
    private final ArthasMcpGatewayService mcpGatewayService;

    /**
     * 进程列表按来源拆成 IDEA / Local 两个分页，避免混在一起难以浏览。
     */
    private final Map<ProcessListTab, ProcessTableModel> processTableModels = new EnumMap<>(ProcessListTab.class);

    private final Map<ProcessListTab, JTable> processTables = new EnumMap<>(ProcessListTab.class);
    private final JTabbedPane processTabs = new JTabbedPane();
    private final JBLabel processStatusLabel = new JBLabel(message("workbench.process.refreshing"));
    private final JBLabel selectionTitleLabel = new JBLabel(message("workbench.selection.none.title"));
    private final JBLabel selectionSubtitleLabel = createSecondaryLabel(message("workbench.selection.none.summary"));
    private final JBLabel selectionOriginValueLabel = createSelectionValueLabel();
    private final JBLabel selectionStatusValueLabel = createSelectionValueLabel();
    private final JBLabel selectionStrategyValueLabel = createSelectionValueLabel();
    private final JBLabel selectionPortsValueLabel = createSelectionValueLabel();
    private final JBLabel selectionSessionValueLabel = createSelectionValueLabel();
    private final JButton refreshProcessesButton = createButton("workbench.button.refresh", this::refreshProcesses);
    private final JButton copyMcpButton = createButton("workbench.button.copy_mcp", this::copySelectedMcp);
    private final JButton settingsButton = createButton("workbench.button.settings", this::openSettings);
    private final JButton attachButton = createButton("workbench.button.start_attach", this::attachSelectedProcess);
    private final JButton openSessionButton = createButton("workbench.button.open_session", this::openSelectedSession);
    private final JButton stopAttachButton = createButton("workbench.button.stop_attach", this::stopSelectedAttach);
    private final JButton otherActionsButton = createButton("workbench.button.more", this::showOtherActionsMenu);

    public ArthasWorkbenchPanel(Project project) {
        super(new BorderLayout(0, 8));
        this.project = project;
        this.settings = ApplicationManager.getApplication().getService(ArthasWorkbenchSettingsService.class);
        this.processService = project.getService(JvmProcessService.class);
        this.attachService = project.getService(ArthasAttachService.class);
        this.sessionService = project.getService(ArthasSessionService.class);
        this.mcpGatewayService = ApplicationManager.getApplication().getService(ArthasMcpGatewayService.class);

        add(createToolbar(), BorderLayout.NORTH);
        add(createProcessTabs(), BorderLayout.CENTER);
        add(createBottomBar(), BorderLayout.SOUTH);

        sessionService.addListener(this::refreshSessionDerivedState, this);
        subscribeExecutionLifecycle();
        refreshProcesses();
        refreshSessionDerivedState();
    }

    /**
     * 顶部工具栏分成两行：
     * 第一行是主要操作按钮，第二行是识别结果，避免窄宽度时文字和按钮互相挤压。
     */
    private JComponent createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(0, 6));

        JPanel actionRow = new JPanel(new BorderLayout(8, 0));
        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftActions.add(refreshProcessesButton);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.add(copyMcpButton);
        rightActions.add(settingsButton);

        processStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));

        actionRow.add(leftActions, BorderLayout.WEST);
        actionRow.add(rightActions, BorderLayout.EAST);
        toolbar.add(actionRow, BorderLayout.NORTH);
        toolbar.add(processStatusLabel, BorderLayout.SOUTH);
        return toolbar;
    }

    /**
     * 底部栏只保留与当前选中项直接相关的核心操作。
     * 按钮区使用固定网格，避免在窄宽度下出现折叠和错位。
     */
    private JComponent createBottomBar() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(message("workbench.selection.border")),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JPanel summaryPanel = new JPanel(new BorderLayout(0, 4));
        summaryPanel.add(selectionTitleLabel, BorderLayout.NORTH);
        summaryPanel.add(selectionSubtitleLabel, BorderLayout.CENTER);
        summaryPanel.add(createSelectionInfoGrid(), BorderLayout.SOUTH);

        JPanel actionPanel = new JPanel(new GridLayout(1, 4, 8, 0));
        actionPanel.add(attachButton);
        actionPanel.add(openSessionButton);
        actionPanel.add(stopAttachButton);
        actionPanel.add(otherActionsButton);

        panel.add(summaryPanel, BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * 进程分页固定为 IDEA 在前、本地 JVM 在后，并为每个分页分别维护滚动表格。
     */
    private JComponent createProcessTabs() {
        // 当前只有两个固定分页，不需要滚动导航按钮，直接使用换行模式即可。
        processTabs.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        processTabs.setBorder(BorderFactory.createEmptyBorder());
        for (ProcessListTab tab : ProcessListTab.values()) {
            ProcessTableModel model = new ProcessTableModel();
            JTable table = createProcessTable(model);
            processTableModels.put(tab, model);
            processTables.put(tab, table);
            processTabs.addTab(message(tab.titleKey(), 0), new JBScrollPane(table));
        }
        processTabs.addChangeListener(event -> {
            ensureTableSelection(activeProcessTab());
            refreshSelectionState();
        });
        processTabs.setSelectedIndex(ProcessListTab.IDEA.ordinal());
        return processTabs;
    }

    /**
     * 进程表是 Workbench 的主视图。
     * 双击一行会尝试直接开启 Arthas，右键则弹出当前进程相关的操作菜单。
     */
    private JTable createProcessTable(ProcessTableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.setDefaultRenderer(Object.class, new ProcessTableCellRenderer(model));
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                refreshSelectionState();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showProcessMenuIfNeeded(table, event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showProcessMenuIfNeeded(table, event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1 && attachButton.isEnabled()) {
                    attachSelectedProcess();
                }
            }
        });
        return table;
    }

    /**
     * 选中信息使用固定字段卡片，避免继续拼接大段自然语言。
     */
    private JPanel createSelectionInfoGrid() {
        JPanel panel = new JPanel(new GridLayout(0, 3, 8, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        panel.add(createSelectionInfoItem("workbench.selection.field.origin", selectionOriginValueLabel));
        panel.add(createSelectionInfoItem("workbench.selection.field.status", selectionStatusValueLabel));
        panel.add(createSelectionInfoItem("workbench.selection.field.strategy", selectionStrategyValueLabel));
        panel.add(createSelectionInfoItem("workbench.selection.field.ports", selectionPortsValueLabel));
        panel.add(createSelectionInfoItem("workbench.selection.field.session", selectionSessionValueLabel));
        panel.add(new JPanel());
        return panel;
    }

    private JPanel createSelectionInfoItem(String key, JBLabel valueLabel) {
        JPanel itemPanel = new JPanel(new BorderLayout(0, 2));
        itemPanel.add(createSecondaryLabel(message(key)), BorderLayout.NORTH);
        itemPanel.add(valueLabel, BorderLayout.CENTER);
        return itemPanel;
    }

    private JBLabel createSelectionValueLabel() {
        JBLabel label = new JBLabel(message("workbench.selection.value.empty"));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private JBLabel createSecondaryLabel(String text) {
        JBLabel label = new JBLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder());
        return label;
    }

    /**
     * 统一构建按钮，所有可见文本都从国际化资源中读取。
     */
    private JButton createButton(String key, Runnable action) {
        JButton button = new JButton(message(key));
        button.addActionListener(event -> action.run());
        return button;
    }

    /**
     * 监听 IDEA Run/Debug 生命周期。
     * 当 JVM 启停时同步刷新列表，并尽量自动标记会话停止状态。
     */
    private void subscribeExecutionLifecycle() {
        project.getMessageBus().connect(this).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(String executorId, ExecutionEnvironment env, ProcessHandler handler) {
                scheduleProcessRefresh();
            }

            @Override
            public void processNotStarted(String executorId, ExecutionEnvironment env, Throwable cause) {
                scheduleProcessRefresh();
            }

            @Override
            public void processTerminated(
                    String executorId, ExecutionEnvironment env, ProcessHandler handler, int exitCode) {
                Long pid = extractPid(handler);
                if (pid != null) {
                    sessionService.markStoppedByPid(pid);
                }
                scheduleProcessRefresh();
            }
        });
    }

    private void scheduleProcessRefresh() {
        ApplicationManager.getApplication().invokeLater(this::refreshProcesses);
    }

    /**
     * 在后台线程刷新 JVM 列表，避免阻塞 EDT。
     */
    private void refreshProcesses() {
        processStatusLabel.setText(message("workbench.process.refreshing"));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ProcessSnapshot snapshot = processService.listProcesses();
                sessionService.markStoppedByMissingProcesses(activePids(snapshot));
                ApplicationManager.getApplication().invokeLater(() -> applyProcessSnapshot(snapshot));
            } catch (Exception exception) {
                ApplicationManager.getApplication()
                        .invokeLater(() -> processStatusLabel.setText(
                                message("workbench.process.refresh_failed", exception.getMessage())));
            }
        });
    }

    /**
     * 刷新表格数据后尽量恢复用户原来的选中行，减少界面抖动。
     */
    private void applyProcessSnapshot(ProcessSnapshot snapshot) {
        Map<ProcessListTab, Long> selectedPids = captureSelectedPids();
        updateProcessTables(snapshot);
        processStatusLabel.setText(message(
                "workbench.process.summary",
                snapshot.getAllProcesses().size(),
                snapshot.getIdeaProcesses().size()));
        updateProcessTabTitles();
        restoreSelections(selectedPids);
        refreshSelectionState();
    }

    private void refreshSessionDerivedState() {
        Map<ProcessListTab, Long> selectedPids = captureSelectedPids();
        for (ProcessTableModel model : processTableModels.values()) {
            model.refreshSessionState(sessionService);
        }
        updateProcessTabTitles();
        restoreSelections(selectedPids);
        refreshSelectionState();
    }

    /**
     * 排序器尚未稳定时，行号转换可能抛出异常，这里做保护性恢复。
     */
    private void restoreSelections(Map<ProcessListTab, Long> selectedPids) {
        ProcessListTab activeTab = activeProcessTab();
        for (ProcessListTab tab : ProcessListTab.values()) {
            restoreSelection(tab, selectedPids.get(tab), tab == activeTab);
        }
    }

    private void restoreSelection(ProcessListTab tab, Long selectedPid, boolean preferFirstRow) {
        JTable table = tableOf(tab);
        ProcessTableModel model = modelOf(tab);
        int targetIndex = model.indexOfPid(selectedPid);
        if (targetIndex < 0 && preferFirstRow && model.getRowCount() > 0) {
            targetIndex = 0;
        }
        if (targetIndex >= 0 && table.getRowCount() > 0) {
            try {
                int viewIndex = table.convertRowIndexToView(targetIndex);
                if (viewIndex >= 0 && viewIndex < table.getRowCount()) {
                    table.getSelectionModel().setSelectionInterval(viewIndex, viewIndex);
                    return;
                }
            } catch (IndexOutOfBoundsException ignored) {
                // 排序器尚未稳定时直接清空选择，避免 Tool Window 初始化阶段抛错。
            }
        }
        table.clearSelection();
    }

    /**
     * 当用户切换分页时，如果当前分页还没有选中项，就自动选中第一行，保证底部操作区始终有明确上下文。
     */
    private void ensureTableSelection(ProcessListTab tab) {
        JTable table = tableOf(tab);
        if (table.getSelectedRow() >= 0 || table.getRowCount() == 0) {
            return;
        }
        restoreSelection(tab, null, true);
    }

    private void updateProcessTables(ProcessSnapshot snapshot) {
        modelOf(ProcessListTab.IDEA).update(ProcessListTab.IDEA.filter(snapshot), sessionService);
        modelOf(ProcessListTab.LOCAL).update(ProcessListTab.LOCAL.filter(snapshot), sessionService);
    }

    private void updateProcessTabTitles() {
        for (ProcessListTab tab : ProcessListTab.values()) {
            processTabs.setTitleAt(
                    tab.ordinal(), message(tab.titleKey(), modelOf(tab).getRowCount()));
        }
    }

    private Map<ProcessListTab, Long> captureSelectedPids() {
        Map<ProcessListTab, Long> selectedPids = new EnumMap<>(ProcessListTab.class);
        for (ProcessListTab tab : ProcessListTab.values()) {
            selectedPids.put(tab, selectedPid(tab));
        }
        return selectedPids;
    }

    private Long selectedPid() {
        return selectedPid(activeProcessTab());
    }

    private Long selectedPid(ProcessListTab tab) {
        ProcessRow row = selectedProcessRow(tab);
        return row == null ? null : row.process().getPid();
    }

    private ProcessListTab activeProcessTab() {
        int selectedIndex = processTabs.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= ProcessListTab.values().length) {
            return ProcessListTab.IDEA;
        }
        return ProcessListTab.values()[selectedIndex];
    }

    private JTable tableOf(ProcessListTab tab) {
        return processTables.get(tab);
    }

    private ProcessTableModel modelOf(ProcessListTab tab) {
        return processTableModels.get(tab);
    }

    /**
     * 底部状态区跟随当前选中项变化，而统一 MCP 复制按钮会始终保持可用。
     */
    private void refreshSelectionState() {
        ProcessRow row = selectedProcessRow();
        ArthasSessionService.SessionSnapshot snapshot = selectedSessionSnapshot();
        if (row == null) {
            selectionTitleLabel.setText(message("workbench.selection.none.title"));
            selectionTitleLabel.setToolTipText(null);
            String subtitle = message("workbench.selection.none.summary");
            selectionSubtitleLabel.setText(subtitle);
            selectionSubtitleLabel.setToolTipText(subtitle);
            applySelectionDetails(null, null);
            attachButton.setEnabled(false);
            openSessionButton.setEnabled(false);
            stopAttachButton.setEnabled(false);
            copyMcpButton.setEnabled(true);
            otherActionsButton.setEnabled(true);
            return;
        }

        selectionTitleLabel.setText(message(
                "workbench.selection.title",
                String.valueOf(row.process().getPid()),
                compactProcessTitle(row.process().getDisplayName()),
                originLabel(row.process().getOrigin())));
        selectionTitleLabel.setFont(selectionTitleLabel.getFont().deriveFont(Font.BOLD));
        selectionTitleLabel.setToolTipText(compactProcessTooltip(row.process().getDisplayName()));
        String subtitle = buildSelectionSubtitle(snapshot);
        selectionSubtitleLabel.setText(subtitle);
        selectionSubtitleLabel.setToolTipText(snapshot == null ? subtitle : buildSelectionSummary(snapshot));
        applySelectionDetails(row, snapshot);

        attachButton.setEnabled(canAttach(snapshot));
        openSessionButton.setEnabled(snapshot != null);
        stopAttachButton.setEnabled(snapshot != null && snapshot.getSession().getStatus() == SessionStatus.RUNNING);
        copyMcpButton.setEnabled(true);
        otherActionsButton.setEnabled(true);
    }

    /**
     * 选中摘要统一输出为两行文本：
     * 第一行关注状态与策略，第二行关注端口与下一步操作。
     */
    private String buildSelectionSummary(ArthasSessionService.SessionSnapshot snapshot) {
        if (snapshot == null) {
            return message("workbench.summary.no_session");
        }
        ArthasSession session = snapshot.getSession();
        String primary = message(
                "workbench.summary.primary", statusLabel(session.getStatus()), session.getAttachStrategyLabel());
        if (snapshot.isSessionWindowOpen()) {
            primary =
                    primary + "  |  " + message("workbench.summary.window", message("workbench.session.window.opened"));
        }
        if (session.getStatus() == SessionStatus.RUNNING) {
            return primary + "\n"
                    + message(
                            "workbench.summary.secondary.running",
                            String.valueOf(session.getHttpPort()),
                            String.valueOf(session.getTelnetPort()),
                            snapshot.getSelectedViewType().getDisplayName());
        }
        return primary + "\n" + message("workbench.summary.secondary.idle");
    }

    private String buildSelectionSubtitle(ArthasSessionService.SessionSnapshot snapshot) {
        if (snapshot == null) {
            return message("workbench.summary.no_session.compact");
        }
        SessionStatus status = snapshot.getSession().getStatus();
        return switch (status) {
            case RUNNING -> message("workbench.summary.subtitle.running");
            case ATTACHING -> message("workbench.summary.subtitle.attaching");
            case FAILED, STOPPED -> message("workbench.summary.subtitle.idle");
        };
    }

    /**
     * 将底部摘要映射到固定字段，避免用户反复阅读一整段自由文本。
     */
    private void applySelectionDetails(ProcessRow row, ArthasSessionService.SessionSnapshot snapshot) {
        if (row == null) {
            setSelectionValue(selectionOriginValueLabel, null);
            setSelectionValue(selectionStatusValueLabel, null);
            setSelectionValue(selectionStrategyValueLabel, null);
            setSelectionPortsValue(null);
            setSelectionValue(selectionSessionValueLabel, null);
            return;
        }

        setSelectionValue(selectionOriginValueLabel, originLabel(row.process().getOrigin()));
        if (snapshot == null) {
            setSelectionValue(selectionStatusValueLabel, message("workbench.selection.value.not_attached"));
            setSelectionValue(selectionStrategyValueLabel, null);
            setSelectionPortsValue(null);
            setSelectionValue(selectionSessionValueLabel, message("workbench.selection.value.session_unopened"));
            return;
        }

        ArthasSession session = snapshot.getSession();
        setSelectionValue(selectionStatusValueLabel, statusLabel(session.getStatus()));
        setSelectionValue(selectionStrategyValueLabel, session.getAttachStrategyLabel());
        setSelectionPortsValue(session);
        setSelectionValue(
                selectionSessionValueLabel,
                snapshot.isSessionWindowOpen()
                        ? snapshot.getSelectedViewType().getDisplayName()
                                + " · "
                                + message("workbench.selection.value.window_opened")
                        : message("workbench.selection.value.session_unopened"));
    }

    private void setSelectionValue(JBLabel label, String value) {
        String displayValue = value == null || value.isBlank() ? message("workbench.selection.value.empty") : value;
        label.setText(displayValue);
        label.setToolTipText(displayValue);
    }

    /**
     * 端口字段始终显示为 `HTTP / Telnet` 的紧凑值，悬停时再展开为明确的端口说明。
     */
    private void setSelectionPortsValue(ArthasSession session) {
        if (session == null) {
            setSelectionValue(selectionPortsValueLabel, null);
            return;
        }
        selectionPortsValueLabel.setText(buildPortsDisplayValue(session));
        selectionPortsValueLabel.setToolTipText(buildPortsTooltip(session.getHttpPort(), session.getTelnetPort()));
    }

    static String buildPortsDisplayValue(ArthasSession session) {
        return String.valueOf(session.getHttpPort()) + " / " + String.valueOf(session.getTelnetPort());
    }

    static String buildPortsTooltip(int httpPort, int telnetPort) {
        return ArthasWorkbenchBundle.message(
                "workbench.selection.tooltip.ports", String.valueOf(httpPort), String.valueOf(telnetPort));
    }

    /**
     * 用户从按钮或右键菜单触发“开启 Arthas”的统一入口。
     */
    private void attachSelectedProcess() {
        ProcessRow row = selectedProcessRow();
        if (row == null) {
            UiToolkit.notifyWarn(project, message("workbench.notify.select_process"));
            return;
        }
        attachProcess(row.process(), false);
    }

    /**
     * 构造临时会话并启动后台 Attach。
     * Attach 过程中先打开 Log 视图，成功后自动切换到 Console 视图。
     */
    private void attachProcess(JvmProcessInfo process, boolean forcePackageUpdate) {
        ArthasWorkbenchSettingsService.SettingsState state = settings.getState();
        AttachStrategyType attachStrategyType = AttachStrategyType.ARTHAS_BOOT;
        PortAllocationMode portAllocationMode = PortAllocationMode.fromValue(state.portAllocationMode);
        McpPasswordMode mcpPasswordMode = McpPasswordMode.fromValue(state.mcpPasswordMode, state.mcpPassword);
        PackageSourceSpec sourceSpec = settings.toPackageSourceSpec();
        Integer preferredHttpPort = parsePort(state.httpPort);
        Integer preferredTelnetPort = parsePort(state.telnetPort);
        String mcpEndpoint = "/mcp";
        String configuredMcpPassword = state.mcpPassword == null ? "" : state.mcpPassword.trim();
        if (mcpPasswordMode.requiresPassword() && configuredMcpPassword.isBlank()) {
            UiToolkit.notifyError(project, message("workbench.notify.password_required"));
            return;
        }
        String provisionalMcpPassword = mcpPasswordMode.requiresPassword() ? configuredMcpPassword : "";
        String sessionId = UUID.randomUUID().toString();

        ArthasSession provisionalSession = new ArthasSession(
                sessionId,
                process.getPid(),
                process.getDisplayName(),
                preferredPortForPreview(preferredHttpPort, 8563),
                preferredPortForPreview(preferredTelnetPort, 3658),
                mcpEndpoint,
                provisionalMcpPassword,
                buildPackageLabel(sourceSpec),
                attachStrategyType.getDisplayName(),
                currentJavaExecutable(),
                "",
                null,
                SessionStatus.ATTACHING);
        sessionService.addOrUpdateSession(provisionalSession);
        sessionService.openSessionWindow(sessionId, ArthasSessionViewType.LOG);
        activateToolWindow(SESSIONS_TOOL_WINDOW_ID);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ArthasSession session = attachService.attach(
                        new AttachRequest(
                                sessionId,
                                process.getPid(),
                                process.getDisplayName(),
                                sourceSpec,
                                preferredHttpPort,
                                preferredTelnetPort,
                                portAllocationMode,
                                mcpEndpoint,
                                mcpPasswordMode,
                                configuredMcpPassword,
                                forcePackageUpdate),
                        line -> sessionService.appendLog(sessionId, line));
                sessionService.addOrUpdateSession(session);
                sessionService.openSessionWindow(sessionId, ArthasSessionViewType.CONSOLE);
                runOnEdt(() -> {
                    UiToolkit.notifyInfo(project, message("workbench.notify.attach_success", session.getPid()));
                    if (state.autoConnectConsole) {
                        activateToolWindow(SESSIONS_TOOL_WINDOW_ID);
                    }
                    if (state.autoOpenWebUi) {
                        UiToolkit.openInBrowser(project, session.getHttpApiUiUrl());
                    }
                });
            } catch (Exception exception) {
                sessionService.appendLog(sessionId, message("workbench.notify.attach_failed", exception.getMessage()));
                sessionService.addOrUpdateSession(provisionalSession.withStatus(SessionStatus.FAILED));
                sessionService.openSessionWindow(sessionId, ArthasSessionViewType.LOG);
                runOnEdt(() -> {
                    UiToolkit.notifyError(project, message("workbench.notify.attach_failed", exception.getMessage()));
                    activateToolWindow(SESSIONS_TOOL_WINDOW_ID);
                });
            }
        });
    }

    /**
     * 打开当前选中进程对应的会话窗口。
     * 运行中的会话默认进入 Console，其他状态维持上次选中的视图。
     */
    private void openSelectedSession() {
        ArthasSessionService.SessionSnapshot snapshot = selectedSessionSnapshot();
        if (snapshot == null) {
            UiToolkit.notifyWarn(project, message("workbench.notify.session_missing"));
            return;
        }
        ArthasSessionViewType preferredView = snapshot.getSession().getStatus() == SessionStatus.RUNNING
                ? ArthasSessionViewType.CONSOLE
                : snapshot.getSelectedViewType();
        sessionService.openSessionWindow(snapshot.getId(), preferredView);
        activateToolWindow(SESSIONS_TOOL_WINDOW_ID);
    }

    /**
     * 关闭目标 JVM 上的 Arthas 会话，并把界面切回 Log，方便用户查看停止输出。
     */
    private void stopSelectedAttach() {
        ArthasSessionService.SessionSnapshot snapshot = selectedSessionSnapshot();
        if (snapshot == null) {
            UiToolkit.notifyWarn(project, message("workbench.notify.session_missing"));
            return;
        }
        ArthasSession session = snapshot.getSession();
        if (session.getStatus() != SessionStatus.RUNNING) {
            UiToolkit.notifyWarn(project, message("workbench.notify.session_not_running"));
            return;
        }
        sessionService.openSessionWindow(snapshot.getId(), ArthasSessionViewType.LOG);
        activateToolWindow(SESSIONS_TOOL_WINDOW_ID);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ArthasSession stopped =
                        attachService.stop(session, line -> sessionService.appendLog(session.getId(), line));
                sessionService.addOrUpdateSession(stopped);
                runOnEdt(() ->
                        UiToolkit.notifyInfo(project, message("workbench.notify.session_stopped", stopped.getPid())));
            } catch (Exception exception) {
                sessionService.appendLog(
                        session.getId(), message("workbench.notify.stop_failed", exception.getMessage()));
                runOnEdt(() -> UiToolkit.notifyError(
                        project, message("workbench.notify.stop_failed", exception.getMessage())));
            }
        });
    }

    private void showOtherActionsMenu() {
        JPopupMenu menu = createActionMenu();
        menu.show(otherActionsButton, 0, otherActionsButton.getHeight());
    }

    /**
     * 右键菜单会自动同步表格选中项，再弹出对应进程的操作集合。
     */
    private void showProcessMenuIfNeeded(JTable table, MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        int viewRow = table.rowAtPoint(event.getPoint());
        if (viewRow < 0) {
            return;
        }
        table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        refreshSelectionState();
        createActionMenu().show(event.getComponent(), event.getX(), event.getY());
    }

    /**
     * 构建右键菜单或“其他操作”菜单。
     */
    private JPopupMenu createActionMenu() {
        ArthasSessionService.SessionSnapshot snapshot = selectedSessionSnapshot();
        JPopupMenu menu = new JPopupMenu();

        addMenuItem(menu, "workbench.menu.start_attach", canAttach(snapshot), this::attachSelectedProcess);
        addMenuItem(menu, "workbench.menu.open_session", snapshot != null, this::openSelectedSession);
        addMenuItem(
                menu,
                "workbench.menu.stop_attach",
                snapshot != null && snapshot.getSession().getStatus() == SessionStatus.RUNNING,
                this::stopSelectedAttach);
        menu.addSeparator();
        addMenuItem(
                menu,
                "workbench.menu.open_web_ui",
                snapshot != null && snapshot.getSession().getStatus() == SessionStatus.RUNNING,
                this::openSelectedWebUi);
        addMenuItem(
                menu,
                "workbench.menu.open_agent_dir",
                snapshot != null && resolveAgentWorkDirectory(snapshot.getSession()) != null,
                this::openSelectedAgentDirectory);
        addMenuItem(menu, "workbench.menu.attach_details", snapshot != null, this::showSelectedAttachDetails);
        return menu;
    }

    private void addMenuItem(JPopupMenu menu, String key, boolean enabled, Runnable action) {
        JMenuItem item = new JMenuItem(message(key));
        item.setEnabled(enabled);
        item.addActionListener(event -> action.run());
        menu.add(item);
    }

    private void openSelectedWebUi() {
        ArthasSessionService.SessionSnapshot snapshot = selectedSessionSnapshot();
        if (snapshot == null || snapshot.getSession().getStatus() != SessionStatus.RUNNING) {
            UiToolkit.notifyWarn(project, message("workbench.notify.web_ui_unavailable"));
            return;
        }
        UiToolkit.openInBrowser(project, snapshot.getSession().getHttpApiUiUrl());
    }

    /**
     * 复制统一 MCP Gateway 配置；该入口固定不依赖当前选中的 Arthas 会话。
     */
    private void copySelectedMcp() {
        try {
            String gatewayToken = settings.resolveGatewayToken();
            UiToolkit.copyToClipboard(
                    McpConfigFormatter.formatGateway(mcpGatewayService.getUnifiedGatewayMcpUrl(), gatewayToken));
            UiToolkit.notifyInfo(project, message("workbench.notify.mcp_copied"));
        } catch (Exception exception) {
            UiToolkit.notifyError(project, message("workbench.notify.mcp_gateway_unavailable", exception.getMessage()));
        }
    }

    private void openSelectedAgentDirectory() {
        ArthasSessionService.SessionSnapshot snapshot = selectedSessionSnapshot();
        if (snapshot == null) {
            UiToolkit.notifyWarn(project, message("workbench.notify.session_missing"));
            return;
        }
        String directory = resolveAgentWorkDirectory(snapshot.getSession());
        if (directory == null) {
            UiToolkit.notifyWarn(project, message("workbench.notify.agent_dir_unavailable"));
            return;
        }
        UiToolkit.openDirectory(project, directory);
    }

    private void showSelectedAttachDetails() {
        ArthasSessionService.SessionSnapshot snapshot = selectedSessionSnapshot();
        if (snapshot == null) {
            UiToolkit.notifyWarn(project, message("workbench.notify.session_missing"));
            return;
        }
        JBTextArea area = new JBTextArea(buildSessionDetails(snapshot), 18, 80);
        area.setEditable(false);
        area.setLineWrap(false);
        area.setWrapStyleWord(false);
        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle(message("workbench.dialog.attach_details"));
        builder.setCenterPanel(new JBScrollPane(area));
        builder.addOkAction();
        builder.show();
    }

    private void openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ArthasWorkbenchConfigurable.class);
        refreshSelectionState();
    }

    private void activateToolWindow(String toolWindowId) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
        if (toolWindow != null) {
            toolWindow.activate(null, true, true);
        }
    }

    private void runOnEdt(Runnable action) {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            action.run();
            return;
        }
        ApplicationManager.getApplication().invokeLater(action);
    }

    private ProcessRow selectedProcessRow() {
        return selectedProcessRow(activeProcessTab());
    }

    private ProcessRow selectedProcessRow(ProcessListTab tab) {
        JTable table = tableOf(tab);
        ProcessTableModel model = modelOf(tab);
        if (table == null || model == null) {
            return null;
        }
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        try {
            int modelRow = table.convertRowIndexToModel(viewRow);
            return model.rowAt(modelRow);
        } catch (IndexOutOfBoundsException ignored) {
            return null;
        }
    }

    private ArthasSessionService.SessionSnapshot selectedSessionSnapshot() {
        ProcessRow row = selectedProcessRow();
        if (row == null) {
            return null;
        }
        return sessionService.findLatestByPid(row.process().getPid());
    }

    /**
     * 只有未 Attach 、已失败或已停止的进程允许重新开启 Arthas。
     */
    private boolean canAttach(ArthasSessionService.SessionSnapshot snapshot) {
        if (selectedProcessRow() == null) {
            return false;
        }
        if (snapshot == null) {
            return true;
        }
        SessionStatus status = snapshot.getSession().getStatus();
        return status == SessionStatus.FAILED || status == SessionStatus.STOPPED;
    }

    private Set<Long> activePids(ProcessSnapshot snapshot) {
        Set<Long> pids = new LinkedHashSet<>();
        for (JvmProcessInfo process : snapshot.getAllProcesses()) {
            pids.add(process.getPid());
        }
        return pids;
    }

    private Integer parsePort(String value) {
        try {
            String trimmed = value == null ? "" : value.trim();
            return trimmed.isEmpty() ? null : Integer.parseInt(trimmed);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int preferredPortForPreview(Integer preferredPort, int fallback) {
        return preferredPort == null ? fallback : preferredPort;
    }

    private String buildPackageLabel(PackageSourceSpec sourceSpec) {
        String value = sourceSpec.getValue();
        if (value == null || value.isBlank()) {
            return sourceSpec.getType().getDisplayName();
        }
        return sourceSpec.getType().getDisplayName() + "\n" + value;
    }

    private String currentJavaExecutable() {
        return System.getProperty("java.home") == null
                ? "java"
                : new File(System.getProperty("java.home"), "bin/java").getPath();
    }

    private Long extractPid(ProcessHandler handler) {
        if (handler instanceof BaseProcessHandler<?> baseProcessHandler && baseProcessHandler.getProcess() != null) {
            return baseProcessHandler.getProcess().pid();
        }
        return extractPidByReflection(handler);
    }

    /**
     * IDEA 不同运行器的 ProcessHandler 类型不同，这里做一个宽松的反射兜底。
     */
    private Long extractPidByReflection(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Method pidMethod = value.getClass().getMethod("getPid");
            Object pid = pidMethod.invoke(value);
            if (pid instanceof Long longValue) {
                return longValue;
            }
            if (pid instanceof Integer intValue) {
                return intValue.longValue();
            }
        } catch (Exception ignored) {
            // 当前类型没有 getPid 时继续尝试其他路径。
        }
        try {
            Method processMethod = value.getClass().getMethod("getProcess");
            Object process = processMethod.invoke(value);
            if (process instanceof Process localProcess) {
                return localProcess.pid();
            }
            if (process != null && process != value) {
                return extractPidByReflection(process);
            }
        } catch (Exception ignored) {
            // 当前类型没有 getProcess 时继续尝试其他路径。
        }
        try {
            Method handlerMethod = value.getClass().getMethod("getProcessHandler");
            Object nested = handlerMethod.invoke(value);
            if (nested != null && nested != value) {
                return extractPidByReflection(nested);
            }
        } catch (Exception ignored) {
            // 当前类型没有 getProcessHandler 时忽略即可。
        }
        return null;
    }

    private String resolveAgentWorkDirectory(ArthasSession session) {
        if (session.getArthasHomePath() != null && !session.getArthasHomePath().isBlank()) {
            return session.getArthasHomePath();
        }
        if (session.getBootJarPath() == null || session.getBootJarPath().isBlank()) {
            return null;
        }
        File bootJar = new File(session.getBootJarPath());
        File parent = bootJar.getParentFile();
        return parent == null ? null : parent.getAbsolutePath();
    }

    /**
     *  Attach 详情对话框按固定字段顺序输出，便于排查问题时复制粘贴。
     */
    private String buildSessionDetails(ArthasSessionService.SessionSnapshot snapshot) {
        ArthasSession session = snapshot.getSession();
        String gatewayUrl;
        String gatewaySessionsUrl;
        try {
            gatewayUrl = mcpGatewayService.getUnifiedGatewayMcpUrl();
            gatewaySessionsUrl = mcpGatewayService.getSessionsUrl();
        } catch (Exception exception) {
            gatewayUrl = message("workbench.details.unavailable", exception.getMessage());
            gatewaySessionsUrl = gatewayUrl;
        }
        return message("workbench.details.title") + "：" + snapshot.getTitle()
                + "\n" + message("workbench.details.pid") + "：" + session.getPid()
                + "\n" + message("workbench.details.process") + "：" + session.getDisplayName()
                + "\n" + message("workbench.details.status") + "：" + statusLabel(session.getStatus())
                + "\n" + message("workbench.details.attach_strategy") + "：" + session.getAttachStrategyLabel()
                + "\n" + message("workbench.details.http_port") + "：" + String.valueOf(session.getHttpPort())
                + "\n" + message("workbench.details.telnet_port") + "：" + String.valueOf(session.getTelnetPort())
                + "\n" + message("workbench.details.web_ui") + "：" + session.getHttpApiUiUrl()
                + "\n" + message("workbench.details.mcp") + "：" + session.getMcpUrl()
                + "\n" + message("workbench.details.gateway_mcp") + "：" + gatewayUrl
                + "\n" + message("workbench.details.gateway_sessions") + "：" + gatewaySessionsUrl
                + "\n" + message("workbench.details.mcp_password") + "："
                + (session.getMcpPassword().isBlank() ? message("workbench.details.empty") : session.getMcpPassword())
                + "\n" + message("workbench.details.attach_java") + "：" + session.getJavaExecutablePath()
                + "\n" + message("workbench.details.package") + "：" + session.getPackageLabel()
                + "\n" + message("workbench.details.boot_jar") + "：" + session.getBootJarPath()
                + "\n" + message("workbench.details.work_dir") + "："
                + (session.getArthasHomePath() == null
                        ? message("workbench.details.empty")
                        : session.getArthasHomePath())
                + "\n" + message("workbench.details.session_window") + "：" + snapshot.isSessionWindowOpen()
                + "\n" + message("workbench.details.current_view") + "："
                + snapshot.getSelectedViewType().getDisplayName();
    }

    private String originLabel(ProcessOrigin origin) {
        return origin == ProcessOrigin.IDEA_RUN_DEBUG
                ? message("workbench.origin.idea")
                : message("workbench.origin.local");
    }

    private static String compactProcessTableText(String value) {
        return UiToolkit.compactSingleLine(value, PROCESS_TABLE_TEXT_MAX_LENGTH);
    }

    private static String compactProcessTitle(String value) {
        return UiToolkit.compactSingleLine(value, PROCESS_TITLE_TEXT_MAX_LENGTH);
    }

    private static String compactProcessTooltip(String value) {
        return UiToolkit.compactSingleLine(value, PROCESS_TOOLTIP_TEXT_MAX_LENGTH);
    }

    private String statusLabel(SessionStatus status) {
        return switch (status) {
            case ATTACHING -> message("workbench.status.attaching");
            case RUNNING -> message("workbench.status.running");
            case FAILED -> message("workbench.status.failed");
            case STOPPED -> message("workbench.status.stopped");
        };
    }

    private String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }

    @Override
    public void dispose() {
        // 当前面板没有额外的本地资源需要主动释放。
    }

    /**
     * 表格行只保留进程快照和对应的最新会话快照，避免界面层到处重复查找。
     */
    private record ProcessRow(JvmProcessInfo process, ArthasSessionService.SessionSnapshot latestSession) {}

    /**
     * Workbench 的 JVM 列表分页定义。
     */
    enum ProcessListTab {
        IDEA("workbench.tab.idea", ProcessOrigin.IDEA_RUN_DEBUG),
        LOCAL("workbench.tab.local", ProcessOrigin.LOCAL_JVM);

        private final String titleKey;
        private final ProcessOrigin origin;

        ProcessListTab(String titleKey, ProcessOrigin origin) {
            this.titleKey = titleKey;
            this.origin = origin;
        }

        String titleKey() {
            return titleKey;
        }

        List<JvmProcessInfo> filter(ProcessSnapshot snapshot) {
            List<JvmProcessInfo> filtered = new ArrayList<>();
            for (JvmProcessInfo process : snapshot.getAllProcesses()) {
                if (process.getOrigin() == origin) {
                    filtered.add(process);
                }
            }
            return filtered;
        }
    }

    /**
     * 进程列表表格模型。
     */
    static final class ProcessTableModel extends AbstractTableModel {

        private final List<ProcessRow> rows = new ArrayList<>();

        /**
         * 使用新的进程快照整体替换表格内容。
         */
        public void update(List<JvmProcessInfo> processes, ArthasSessionService sessionService) {
            rows.clear();
            for (JvmProcessInfo process : processes) {
                rows.add(new ProcessRow(process, sessionService.findLatestByPid(process.getPid())));
            }
            fireTableDataChanged();
        }

        /**
         * 仅刷新会话状态，不改变现有进程行顺序。
         */
        public void refreshSessionState(ArthasSessionService sessionService) {
            for (int index = 0; index < rows.size(); index++) {
                ProcessRow row = rows.get(index);
                rows.set(
                        index,
                        new ProcessRow(
                                row.process(),
                                sessionService.findLatestByPid(row.process().getPid())));
            }
            fireTableDataChanged();
        }

        public ProcessRow rowAt(int index) {
            return index >= 0 && index < rows.size() ? rows.get(index) : null;
        }

        public int indexOfPid(Long pid) {
            if (pid == null) {
                return -1;
            }
            for (int index = 0; index < rows.size(); index++) {
                if (rows.get(index).process().getPid() == pid) {
                    return index;
                }
            }
            return -1;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> ArthasWorkbenchBundle.message("workbench.table.column.pid");
                case 1 -> ArthasWorkbenchBundle.message("workbench.table.column.origin");
                case 2 -> ArthasWorkbenchBundle.message("workbench.table.column.process");
                default -> ArthasWorkbenchBundle.message("workbench.table.column.arthas");
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ProcessRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.process().getPid();
                case 1 ->
                    row.process().getOrigin() == ProcessOrigin.IDEA_RUN_DEBUG
                            ? ArthasWorkbenchBundle.message("workbench.table.origin.idea")
                            : ArthasWorkbenchBundle.message("workbench.table.origin.local");
                case 2 -> compactProcessTableText(row.process().getDisplayName());
                default ->
                    row.latestSession() == null
                            ? ArthasWorkbenchBundle.message("workbench.table.status.none")
                            : switch (row.latestSession().getSession().getStatus()) {
                                case ATTACHING -> ArthasWorkbenchBundle.message("workbench.status.attaching");
                                case RUNNING -> ArthasWorkbenchBundle.message("workbench.status.running");
                                case FAILED -> ArthasWorkbenchBundle.message("workbench.status.failed");
                                case STOPPED -> ArthasWorkbenchBundle.message("workbench.status.stopped");
                            };
            };
        }
    }

    /**
     * 表格渲染器负责补充提示信息，不参与任何业务判断。
     */
    private static final class ProcessTableCellRenderer extends DefaultTableCellRenderer {

        private final ProcessTableModel model;

        private ProcessTableCellRenderer(ProcessTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (component instanceof JComponent jComponent) {
                int modelRow;
                try {
                    modelRow = table.convertRowIndexToModel(row);
                } catch (IndexOutOfBoundsException ignored) {
                    jComponent.setToolTipText(null);
                    return component;
                }
                ProcessRow processRow = model.rowAt(modelRow);
                if (processRow == null) {
                    jComponent.setToolTipText(null);
                } else if (column == 3 && processRow.latestSession() != null) {
                    ArthasSession session = processRow.latestSession().getSession();
                    jComponent.setToolTipText(ArthasWorkbenchBundle.message(
                            "workbench.table.tooltip",
                            switch (session.getStatus()) {
                                case ATTACHING -> ArthasWorkbenchBundle.message("workbench.status.attaching");
                                case RUNNING -> ArthasWorkbenchBundle.message("workbench.status.running");
                                case FAILED -> ArthasWorkbenchBundle.message("workbench.status.failed");
                                case STOPPED -> ArthasWorkbenchBundle.message("workbench.status.stopped");
                            },
                            String.valueOf(session.getHttpPort()),
                            String.valueOf(session.getTelnetPort()),
                            session.getAttachStrategyLabel()));
                } else if (column == 2) {
                    jComponent.setToolTipText(
                            compactProcessTooltip(processRow.process().getDisplayName()));
                } else {
                    jComponent.setToolTipText(value == null ? null : value.toString());
                }
            }
            return component;
        }
    }
}
