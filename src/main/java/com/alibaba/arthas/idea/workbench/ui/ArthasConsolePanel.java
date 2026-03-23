package com.alibaba.arthas.idea.workbench.ui;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.ArthasSession;
import com.alibaba.arthas.idea.workbench.model.SessionStatus;
import com.alibaba.arthas.idea.workbench.service.ArthasTelnetTtyConnector;
import com.alibaba.arthas.idea.workbench.util.ArthasSessionUiSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.ui.JediTermWidget;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.Objects;
import javax.swing.JPanel;

/**
 * 真正承载 Arthas Console 的终端面板。
 * 终端组件只在会话运行中才创建，其余状态显示说明文本。
 */
public final class ArthasConsolePanel extends JPanel implements Disposable {

    private static final String STATUS_CARD = "status";
    private static final String TERMINAL_CARD = "terminal";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final JPanel terminalHostPanel = new JPanel(new BorderLayout());
    private final JBTextArea statusArea = createStatusArea();

    private ArthasSession session;
    private ArthasTelnetTtyConnector ttyConnector;
    private JediTermWidget terminalWidget;
    private int connectionToken;
    private boolean disposed;

    public ArthasConsolePanel(Project project) {
        super(new BorderLayout());
        contentPanel.add(new JBScrollPane(statusArea), STATUS_CARD);
        contentPanel.add(terminalHostPanel, TERMINAL_CARD);
        add(contentPanel, BorderLayout.CENTER);
        showWaiting(message("console.status.not_running"));
    }

    /**
     * 根据会话状态在“说明文本”和“真实终端”之间切换。
     */
    public void bindSession(ArthasSession session) {
        this.session = session;
        if (session.getStatus() == SessionStatus.RUNNING) {
            if (terminalWidget != null && ttyConnector != null && ttyConnector.isConnected()) {
                return;
            }
            connect(session);
            return;
        }
        if (session.getStatus() == SessionStatus.FAILED) {
            showWaiting(message("console.status.failed"));
        } else if (session.getStatus() == SessionStatus.STOPPED) {
            showWaiting(message("console.status.stopped"));
        } else {
            showWaiting(message("console.status.attaching"));
        }
    }

    /**
     * 在终端不可用时显示统一的等待/说明文案。
     */
    public void showWaiting(String message) {
        connectionToken++;
        closeTerminal();
        statusArea.setText(message);
        statusArea.setCaretPosition(0);
        cardLayout.show(contentPanel, STATUS_CARD);
    }

    @Override
    public void dispose() {
        disposed = true;
        connectionToken++;
        closeTerminal();
    }

    /**
     * 连接动作放到后台线程执行，再回到 EDT 安装终端组件。
     */
    private void connect(ArthasSession targetSession) {
        int token = ++connectionToken;
        closeTerminal();
        statusArea.setText(message("console.status.connecting", String.valueOf(targetSession.getTelnetPort())));
        statusArea.setCaretPosition(0);
        cardLayout.show(contentPanel, STATUS_CARD);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ArthasTelnetTtyConnector connector =
                    new ArthasTelnetTtyConnector("127.0.0.1", targetSession.getTelnetPort());
            try {
                connector.connect();
            } catch (IOException exception) {
                connector.close();
                runOnEdt(() -> {
                    if (!isCurrentConnectRequest(token, targetSession)) {
                        return;
                    }
                    showWaiting(message("console.status.connect_failed", exception.getMessage()));
                });
                return;
            }

            runOnEdt(() -> {
                if (!isCurrentConnectRequest(token, targetSession)) {
                    connector.close();
                    return;
                }
                installTerminal(connector);
            });
        });
    }

    private void installTerminal(ArthasTelnetTtyConnector connector) {
        closeTerminal();
        ttyConnector = connector;
        terminalWidget = new JediTermWidget(160, 40, new ConsoleSettingsProvider());
        terminalWidget.setTtyConnector(connector);
        terminalHostPanel.removeAll();
        terminalHostPanel.add(terminalWidget, BorderLayout.CENTER);
        terminalHostPanel.revalidate();
        terminalHostPanel.repaint();
        cardLayout.show(contentPanel, TERMINAL_CARD);
        try {
            terminalWidget.start();
            terminalWidget.requestFocusInWindow();
        } catch (RuntimeException exception) {
            showWaiting(message("console.status.init_failed", exception.getMessage()));
        }
    }

    /**
     * 切换会话或销毁组件前，必须先关闭旧终端与连接器，避免悬挂连接。
     */
    private void closeTerminal() {
        if (terminalWidget != null) {
            terminalWidget.close();
            terminalWidget = null;
        }
        if (ttyConnector != null) {
            ttyConnector.close();
            ttyConnector = null;
        }
        terminalHostPanel.removeAll();
        terminalHostPanel.revalidate();
        terminalHostPanel.repaint();
    }

    private boolean isCurrentConnectRequest(int token, ArthasSession targetSession) {
        return !disposed
                && token == connectionToken
                && session != null
                && session.getStatus() == SessionStatus.RUNNING
                && Objects.equals(session.getId(), targetSession.getId());
    }

    private void runOnEdt(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable);
    }

    private JBTextArea createStatusArea() {
        JBTextArea area = new JBTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        return area;
    }

    private String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }

    /**
     * 终端颜色和字体统一跟随 IDEA。
     */
    private final class ConsoleSettingsProvider extends JBTerminalSystemSettingsProviderBase {

        @Override
        public TerminalColor getDefaultForeground() {
            return toTerminalColor(ArthasSessionUiSettings.resolveConsoleForeground());
        }

        @Override
        public TerminalColor getDefaultBackground() {
            return toTerminalColor(ArthasSessionUiSettings.resolveConsoleBackground());
        }

        @Override
        public Font getTerminalFont() {
            return ArthasSessionUiSettings.resolveTerminalFont();
        }

        @Override
        public float getTerminalFontSize() {
            return ArthasSessionUiSettings.resolveTerminalFont().getSize2D();
        }

        private TerminalColor toTerminalColor(java.awt.Color awtColor) {
            return TerminalColor.fromColor(
                    new Color(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue(), awtColor.getAlpha()));
        }
    }
}
