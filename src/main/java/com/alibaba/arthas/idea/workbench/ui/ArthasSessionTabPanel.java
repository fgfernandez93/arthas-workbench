package com.alibaba.arthas.idea.workbench.ui;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.ArthasSessionViewType;
import com.alibaba.arthas.idea.workbench.service.ArthasSessionService;
import com.alibaba.arthas.idea.workbench.util.ArthasSessionUiSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 * 单个 Arthas 会话对应的内容面板。
 * 每个页签内部只在 Terminal 和 Log 两种视图之间切换，不再拆成多个 Tool Window。
 */
public final class ArthasSessionTabPanel extends JPanel implements Disposable {

    private static final String TERMINAL_CARD = "terminal";
    private static final String LOG_CARD = "log";

    private final String sessionId;
    private final ArthasSessionService sessionService;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final ArthasTerminalPanel terminalPanel;
    private final JBTextArea logArea = new JBTextArea();
    private final JToggleButton terminalToggle =
            new JToggleButton(ArthasWorkbenchBundle.message("enum.session.view.terminal"));
    private final JToggleButton logToggle = new JToggleButton(ArthasWorkbenchBundle.message("enum.session.view.log"));
    private boolean syncingView;

    public ArthasSessionTabPanel(Project project, String sessionId) {
        super(new BorderLayout(0, 8));
        this.sessionId = sessionId;
        this.sessionService = project.getService(ArthasSessionService.class);
        this.terminalPanel = new ArthasTerminalPanel(project);

        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(createSwitchBar(), BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setLineWrap(false);
        logArea.setWrapStyleWord(false);

        cardPanel.add(terminalPanel, TERMINAL_CARD);
        cardPanel.add(new JBScrollPane(logArea), LOG_CARD);
        add(cardPanel, BorderLayout.CENTER);
    }

    public void bindSnapshot(ArthasSessionService.SessionSnapshot snapshot) {
        terminalPanel.bindSession(snapshot.getSession());
        applyLogStyle();
        logArea.setText(snapshot.getLogs());
        logArea.setCaretPosition(logArea.getDocument().getLength());
        selectView(snapshot.getSelectedViewType(), false);
    }

    /**
     * 统一切换页签内视图，并在需要时回写会话服务中的选中状态。
     */
    public void selectView(ArthasSessionViewType viewType, boolean publishSelection) {
        ArthasSessionViewType targetView = viewType == null ? ArthasSessionViewType.LOG : viewType;
        syncingView = true;
        try {
            terminalToggle.setSelected(targetView == ArthasSessionViewType.TERMINAL);
            logToggle.setSelected(targetView == ArthasSessionViewType.LOG);
            cardLayout.show(cardPanel, targetView == ArthasSessionViewType.TERMINAL ? TERMINAL_CARD : LOG_CARD);
        } finally {
            syncingView = false;
        }
        if (publishSelection) {
            sessionService.setSelectedViewType(sessionId, targetView);
        }
    }

    @Override
    public void dispose() {
        terminalPanel.dispose();
    }

    /**
     * 使用轻量的 toggle 作为视图切换器，避免在页签内部再嵌套复杂 tab 组件。
     */
    private JPanel createSwitchBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ButtonGroup group = new ButtonGroup();
        group.add(terminalToggle);
        group.add(logToggle);
        terminalToggle.addActionListener(event -> handleToggleSelection(ArthasSessionViewType.TERMINAL));
        logToggle.addActionListener(event -> handleToggleSelection(ArthasSessionViewType.LOG));
        panel.add(terminalToggle);
        panel.add(logToggle);
        return panel;
    }

    private void handleToggleSelection(ArthasSessionViewType viewType) {
        if (syncingView) {
            return;
        }
        selectView(viewType, true);
    }

    /**
     * Log 面板的颜色和字体始终跟随 IDEA。
     */
    private void applyLogStyle() {
        logArea.setFont(ArthasSessionUiSettings.resolveTerminalFont());
        logArea.setForeground(ArthasSessionUiSettings.resolveLogForeground());
        logArea.setBackground(ArthasSessionUiSettings.resolveLogBackground());
        logArea.setCaretColor(ArthasSessionUiSettings.resolveLogForeground());
    }
}
