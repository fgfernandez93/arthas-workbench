package com.alibaba.arthas.idea.workbench.settings;

import com.alibaba.arthas.idea.workbench.ArthasWorkbenchBundle;
import com.alibaba.arthas.idea.workbench.model.GatewayAuthMode;
import com.alibaba.arthas.idea.workbench.model.McpPasswordMode;
import com.alibaba.arthas.idea.workbench.model.PackageSourceSpec;
import com.alibaba.arthas.idea.workbench.model.PackageSourceType;
import com.alibaba.arthas.idea.workbench.model.PortAllocationMode;
import com.alibaba.arthas.idea.workbench.service.ArthasMcpGatewayService;
import com.alibaba.arthas.idea.workbench.service.ArthasPackageService;
import com.alibaba.arthas.idea.workbench.service.ArthasWorkbenchSettingsService;
import com.alibaba.arthas.idea.workbench.util.McpConfigFormatter;
import com.alibaba.arthas.idea.workbench.util.UiToolkit;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.EnumMap;
import java.util.Objects;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 插件设置页的具体表单实现。
 */
public final class ArthasWorkbenchSettingsPanel {

    static final String OFFICIAL_LATEST_DOWNLOAD_URL =
            "https://arthas.aliyun.com/download/latest_version?mirror=aliyun";
    static final String OFFICIAL_VERSION_PLACEHOLDER = "4.1.8";
    static final String CUSTOM_REMOTE_ZIP_PLACEHOLDER = OFFICIAL_LATEST_DOWNLOAD_URL;

    private final Project project;
    private final JPanel rootPanel = new JPanel(new BorderLayout());
    private final JPanel contentPanel = new JPanel(new GridBagLayout());
    private final EnumMap<PackageSourceType, String> sourceValueDrafts = new EnumMap<>(PackageSourceType.class);

    private final JComboBox<PackageSourceType> sourceTypeCombo = new JComboBox<>(PackageSourceType.values());
    private final SourceValueField sourceValueField = new SourceValueField();
    private final JPanel packageActionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JButton updateOfficialLatestPackageButton =
            new JButton(message("settings.action.update.official_latest"));

    private final JComboBox<PortAllocationMode> portAllocationModeCombo = new JComboBox<>(PortAllocationMode.values());
    private final JBLabel portAllocationHintLabel = new JBLabel();
    private final JBTextField httpPortField = new JBTextField();
    private final JBTextField telnetPortField = new JBTextField();

    private final JComboBox<McpPasswordMode> mcpPasswordModeCombo = new JComboBox<>(McpPasswordMode.values());
    private final JBLabel mcpPasswordHintLabel = new JBLabel();
    private final JBPasswordField mcpPasswordField = new JBPasswordField();

    private final JComboBox<GatewayAuthMode> mcpGatewayAuthModeCombo = new JComboBox<>(GatewayAuthMode.values());
    private final JBLabel mcpGatewayAuthHintLabel = new JBLabel();
    private final JBTextField mcpGatewayPortField = new JBTextField();
    private final JBTextField mcpGatewayTokenField = new JBTextField();
    private final JButton generateMcpGatewayTokenButton = new JButton(message("settings.action.generate.token"));
    private final JButton copyMcpConfigButton = new JButton(message("settings.action.copy.mcp"));

    private final JBCheckBox autoOpenTerminalCheckBox = new JBCheckBox(message("settings.auto.open.terminal"));
    private final JBCheckBox autoOpenWebUiCheckBox = new JBCheckBox(message("settings.auto.open.web_ui"));
    private final JBTextArea settingsNoteArea = createNoteArea(message("settings.note"));
    private PackageSourceType lastSourceType = PackageSourceType.OFFICIAL_LATEST;
    private boolean syncingSourceUi;

    public ArthasWorkbenchSettingsPanel(Project project) {
        this.project = project;
        buildUi();
        bindActions();
    }

    public JComponent getComponent() {
        return rootPanel;
    }

    /**
     * 用持久化状态回填界面。
     */
    public void resetFrom(ArthasWorkbenchSettingsService.SettingsState state) {
        PackageSourceType sourceType;
        try {
            sourceType = PackageSourceType.valueOf(state.sourceType);
        } catch (IllegalArgumentException exception) {
            sourceType = PackageSourceType.OFFICIAL_LATEST;
        }
        PortAllocationMode portAllocationMode = PortAllocationMode.fromValue(state.portAllocationMode);
        McpPasswordMode mcpPasswordMode = McpPasswordMode.fromValue(state.mcpPasswordMode, state.mcpPassword);
        GatewayAuthMode gatewayAuthMode = GatewayAuthMode.fromValue(state.mcpGatewayAuthMode, state.mcpGatewayToken);

        syncingSourceUi = true;
        sourceValueDrafts.clear();
        sourceValueDrafts.put(sourceType, state.sourceValue == null ? "" : state.sourceValue.trim());
        lastSourceType = sourceType;
        sourceTypeCombo.setSelectedItem(sourceType);
        portAllocationModeCombo.setSelectedItem(portAllocationMode);
        httpPortField.setText(state.httpPort);
        telnetPortField.setText(state.telnetPort);
        mcpGatewayPortField.setText(state.mcpGatewayPort);
        mcpGatewayAuthModeCombo.setSelectedItem(gatewayAuthMode);
        mcpGatewayTokenField.setText(state.mcpGatewayToken);
        mcpPasswordModeCombo.setSelectedItem(mcpPasswordMode);
        mcpPasswordField.setText(state.mcpPassword);
        autoOpenTerminalCheckBox.setSelected(state.autoOpenTerminal);
        autoOpenWebUiCheckBox.setSelected(state.autoOpenWebUi);

        portAllocationHintLabel.setText(portAllocationMode.getHint());
        updatePasswordModeUi();
        updateGatewayAuthModeUi();
        updateSourceValueUi(false);
        syncingSourceUi = false;
    }

    /**
     * 从界面读取最新状态，供 Configurable 持久化。
     */
    public ArthasWorkbenchSettingsService.SettingsState toState() {
        ArthasWorkbenchSettingsService.SettingsState state = new ArthasWorkbenchSettingsService.SettingsState();
        state.sourceType = currentSourceType().name();
        state.sourceValue = normalizeSourceValueForSaving(
                currentSourceType(), sourceValueField.getText().trim());
        state.portAllocationMode = currentPortAllocationMode().name();
        state.httpPort = httpPortField.getText().trim();
        state.telnetPort = telnetPortField.getText().trim();
        state.mcpGatewayPort = mcpGatewayPortField.getText().trim();
        state.mcpGatewayAuthMode = currentGatewayAuthMode().name();
        state.mcpGatewayToken = resolveGatewayTokenForSaving();
        state.mcpPasswordMode = currentMcpPasswordMode().name();
        state.mcpPassword = new String(mcpPasswordField.getPassword());
        state.autoOpenTerminal = autoOpenTerminalCheckBox.isSelected();
        state.autoOpenWebUi = autoOpenWebUiCheckBox.isSelected();
        return state;
    }

    /**
     * 判断当前界面是否相对持久化状态发生了变更。
     */
    public boolean isModified(ArthasWorkbenchSettingsService.SettingsState state) {
        ArthasWorkbenchSettingsService.SettingsState current = toState();
        return !Objects.equals(current.sourceType, state.sourceType)
                || !Objects.equals(current.sourceValue, state.sourceValue)
                || !Objects.equals(current.portAllocationMode, state.portAllocationMode)
                || !Objects.equals(current.httpPort, state.httpPort)
                || !Objects.equals(current.telnetPort, state.telnetPort)
                || !Objects.equals(current.mcpGatewayPort, state.mcpGatewayPort)
                || !Objects.equals(
                        current.mcpGatewayAuthMode,
                        GatewayAuthMode.fromValue(state.mcpGatewayAuthMode, state.mcpGatewayToken)
                                .name())
                || !Objects.equals(current.mcpGatewayToken, state.mcpGatewayToken)
                || !Objects.equals(
                        current.mcpPasswordMode,
                        McpPasswordMode.fromValue(state.mcpPasswordMode, state.mcpPassword)
                                .name())
                || !Objects.equals(current.mcpPassword, state.mcpPassword)
                || current.autoOpenTerminal != state.autoOpenTerminal
                || current.autoOpenWebUi != state.autoOpenWebUi;
    }

    void setSourceType(PackageSourceType type) {
        sourceTypeCombo.setSelectedItem(type);
    }

    void setSourceValue(String value) {
        sourceValueField.setText(value);
        sourceValueDrafts.put(currentSourceType(), value == null ? "" : value.trim());
    }

    void setPortAllocationMode(PortAllocationMode mode) {
        portAllocationModeCombo.setSelectedItem(mode);
    }

    void setHttpPort(String value) {
        httpPortField.setText(value);
    }

    void setTelnetPort(String value) {
        telnetPortField.setText(value);
    }

    void setMcpPasswordMode(McpPasswordMode mode) {
        mcpPasswordModeCombo.setSelectedItem(mode);
    }

    void setMcpGatewayPort(String value) {
        mcpGatewayPortField.setText(value);
    }

    void setGatewayAuthMode(GatewayAuthMode mode) {
        mcpGatewayAuthModeCombo.setSelectedItem(mode);
    }

    void setMcpGatewayToken(String value) {
        mcpGatewayTokenField.setText(value);
    }

    void setMcpPassword(String value) {
        mcpPasswordField.setText(value);
    }

    void setAutoOpenTerminal(boolean value) {
        autoOpenTerminalCheckBox.setSelected(value);
    }

    void setAutoOpenWebUi(boolean value) {
        autoOpenWebUiCheckBox.setSelected(value);
    }

    boolean isSourceValueEditable() {
        return sourceValueField.isEditable();
    }

    String getDisplayedSourceValue() {
        return sourceValueField.getText();
    }

    String getSourceValuePlaceholder() {
        return sourceValueField.getHintText();
    }

    String getSourceValueTooltip() {
        return sourceValueField.getToolTipText();
    }

    boolean isSourceValueFocusable() {
        return sourceValueField.isFocusable();
    }

    boolean isUpdateOfficialLatestPackageButtonVisible() {
        return packageActionPanel.isVisible() && updateOfficialLatestPackageButton.isVisible();
    }

    boolean isMcpPasswordEditable() {
        return mcpPasswordField.isEnabled();
    }

    boolean isGatewayTokenEditable() {
        return mcpGatewayTokenField.isEditable();
    }

    boolean isGatewayRandomButtonEnabled() {
        return generateMcpGatewayTokenButton.isEnabled();
    }

    /**
     * 设置页使用“模块卡片”的方式分区，减少大段平铺表单带来的阅读压力。
     */
    private void buildUi() {
        contentPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints rootGc = baseConstraints();
        addRootSection(rootGc, createPackageSection());
        addRootSection(rootGc, createAttachSection());
        addRootSection(rootGc, createPasswordSection());
        addRootSection(rootGc, createGatewaySection());
        addRootSection(rootGc, createBehaviorSection());
        //        addRootSection(rootGc, createFooterNotePanel());

        rootGc.gridy++;
        rootGc.weighty = 1.0;
        contentPanel.add(new JPanel(), rootGc);

        JBScrollPane scrollPane = new JBScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        rootPanel.add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createPackageSection() {
        JPanel panel = createSectionPanel("settings.section.package");
        GridBagConstraints gc = sectionConstraints();
        addField(panel, gc, "settings.source.type", sourceTypeCombo);
        addField(panel, gc, "settings.source.value", sourceValueField);
        addRow(panel, gc, createPackageActions());
        return panel;
    }

    private JPanel createAttachSection() {
        JPanel panel = createSectionPanel("settings.section.attach");
        GridBagConstraints gc = sectionConstraints();
        addField(panel, gc, "settings.port.allocation", portAllocationModeCombo);
        addRow(panel, gc, portAllocationHintLabel);
        addField(panel, gc, "settings.http.port", httpPortField);
        addField(panel, gc, "settings.telnet.port", telnetPortField);
        addRow(panel, gc, createNoteArea(message("settings.section.attach.note")));
        return panel;
    }

    private JPanel createPasswordSection() {
        JPanel panel = createSectionPanel("settings.section.password");
        GridBagConstraints gc = sectionConstraints();
        addField(panel, gc, "settings.mcp.password.mode", mcpPasswordModeCombo);
        addRow(panel, gc, mcpPasswordHintLabel);
        addField(panel, gc, "settings.mcp.password", mcpPasswordField);
        addRow(panel, gc, createNoteArea(message("settings.section.password.note")));
        return panel;
    }

    private JPanel createGatewaySection() {
        JPanel panel = createSectionPanel("settings.section.gateway");
        GridBagConstraints gc = sectionConstraints();
        addField(panel, gc, "settings.gateway.port", mcpGatewayPortField);
        addField(panel, gc, "settings.gateway.auth.mode", mcpGatewayAuthModeCombo);
        addRow(panel, gc, mcpGatewayAuthHintLabel);
        addField(panel, gc, "settings.gateway.token", mcpGatewayTokenField);
        addRow(panel, gc, createGatewayActions());
        addRow(panel, gc, createNoteArea(message("settings.section.gateway.note")));
        return panel;
    }

    private JPanel createBehaviorSection() {
        JPanel panel = createSectionPanel("settings.section.behavior");
        GridBagConstraints gc = sectionConstraints();
        addRow(panel, gc, autoOpenTerminalCheckBox);
        addRow(panel, gc, autoOpenWebUiCheckBox);
        addRow(panel, gc, createNoteArea(message("settings.section.behavior.note")));
        return panel;
    }

    private JPanel createFooterNotePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(message("settings.section.notes")),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        panel.add(settingsNoteArea, BorderLayout.CENTER);
        return panel;
    }

    private GridBagConstraints baseConstraints() {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.weightx = 1.0;
        gc.insets = new Insets(0, 0, 12, 0);
        return gc;
    }

    private GridBagConstraints sectionConstraints() {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;
        gc.weightx = 1.0;
        gc.insets = new Insets(4, 0, 6, 0);
        return gc;
    }

    private JPanel createSectionPanel(String titleKey) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(message(titleKey)), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        return panel;
    }

    private void addRootSection(GridBagConstraints gc, JComponent component) {
        contentPanel.add(component, gc);
        gc.gridy++;
    }

    private void addField(JPanel panel, GridBagConstraints gc, String labelKey, JComponent field) {
        addRow(panel, gc, new JBLabel(message(labelKey)));
        addRow(panel, gc, field);
    }

    private void addRow(JPanel panel, GridBagConstraints gc, JComponent component) {
        panel.add(component, gc);
        gc.gridy++;
    }

    private JPanel createGatewayActions() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        generateMcpGatewayTokenButton.addActionListener(event -> {
            if (currentGatewayAuthMode().usesGeneratedPassword()) {
                mcpGatewayTokenField.setText(randomToken());
            }
        });
        copyMcpConfigButton.addActionListener(event -> copyMcpConfig());
        panel.add(generateMcpGatewayTokenButton);
        panel.add(copyMcpConfigButton);
        return panel;
    }

    /**
     * 只有在选择“官方最新版”时，才提供“更新 Arthas 包”入口，避免其它来源出现无意义的更新动作。
     */
    private JPanel createPackageActions() {
        updateOfficialLatestPackageButton.addActionListener(event -> updateOfficialLatestPackage());
        packageActionPanel.add(updateOfficialLatestPackageButton);
        packageActionPanel.setVisible(false);
        return packageActionPanel;
    }

    /**
     * 所有联动逻辑都集中在这里，避免 UI 更新散落在各个 setter 中。
     */
    private void bindActions() {
        sourceTypeCombo.addActionListener(event -> {
            if (!syncingSourceUi) {
                updateSourceValueUi(true);
            }
        });
        portAllocationModeCombo.addActionListener(event ->
                portAllocationHintLabel.setText(currentPortAllocationMode().getHint()));
        mcpPasswordModeCombo.addActionListener(event -> updatePasswordModeUi());
        mcpGatewayAuthModeCombo.addActionListener(event -> updateGatewayAuthModeUi());
        sourceValueField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    chooseSourceValueIfNeeded();
                }
            }
        });
    }

    private void chooseSourceValueIfNeeded() {
        PackageSourceType sourceType = currentSourceType();
        if (sourceType != PackageSourceType.LOCAL_ZIP && sourceType != PackageSourceType.LOCAL_PATH) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(
                sourceType == PackageSourceType.LOCAL_ZIP ? JFileChooser.FILES_ONLY : JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(message(
                sourceType == PackageSourceType.LOCAL_ZIP
                        ? "settings.source.dialog.local_zip"
                        : "settings.source.dialog.local_path"));
        if (project != null && project.getBasePath() != null) {
            chooser.setCurrentDirectory(new File(project.getBasePath()));
        }
        if (chooser.showOpenDialog(rootPanel) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            sourceValueField.setText(selected.getAbsolutePath());
            sourceValueDrafts.put(sourceType, selected.getAbsolutePath());
        }
    }

    private PackageSourceType currentSourceType() {
        PackageSourceType type = (PackageSourceType) sourceTypeCombo.getSelectedItem();
        return type == null ? PackageSourceType.OFFICIAL_LATEST : type;
    }

    private PortAllocationMode currentPortAllocationMode() {
        PortAllocationMode mode = (PortAllocationMode) portAllocationModeCombo.getSelectedItem();
        return mode == null ? PortAllocationMode.PREFER_CONFIGURED : mode;
    }

    private McpPasswordMode currentMcpPasswordMode() {
        McpPasswordMode mode = (McpPasswordMode) mcpPasswordModeCombo.getSelectedItem();
        return mode == null ? McpPasswordMode.RANDOM : mode;
    }

    private GatewayAuthMode currentGatewayAuthMode() {
        GatewayAuthMode mode = (GatewayAuthMode) mcpGatewayAuthModeCombo.getSelectedItem();
        return mode == null ? GatewayAuthMode.DISABLED : mode;
    }

    private void updatePasswordModeUi() {
        McpPasswordMode passwordMode = currentMcpPasswordMode();
        mcpPasswordHintLabel.setText(passwordMode.getHint());
        mcpPasswordField.setEnabled(passwordMode.requiresPassword());
    }

    private void updateGatewayAuthModeUi() {
        GatewayAuthMode authMode = currentGatewayAuthMode();
        mcpGatewayAuthHintLabel.setText(authMode.getHint());
        boolean editable = authMode.requiresPassword();
        mcpGatewayTokenField.setEnabled(authMode != GatewayAuthMode.DISABLED);
        mcpGatewayTokenField.setEditable(editable);
        generateMcpGatewayTokenButton.setEnabled(authMode.usesGeneratedPassword());
    }

    /**
     * “版本 / 地址 / 路径”输入框会随包来源切换为只读链接、文本输入或点击选择路径三种模式。
     */
    private void updateSourceValueUi(boolean rememberPreviousValue) {
        PackageSourceType sourceType = currentSourceType();
        if (rememberPreviousValue) {
            sourceValueDrafts.put(
                    lastSourceType,
                    normalizeSourceValueForSaving(
                            lastSourceType, sourceValueField.getText().trim()));
        }
        SourceValuePresentation presentation =
                describeSourceValue(sourceType, sourceValueDrafts.getOrDefault(sourceType, ""));
        sourceValueField.setText(presentation.displayText());
        sourceValueField.setEditable(presentation.editable());
        sourceValueField.setFocusable(presentation.focusable());
        sourceValueField.setRequestFocusEnabled(presentation.focusable());
        int cursorType = presentation.clickToChoose()
                ? Cursor.HAND_CURSOR
                : presentation.editable() ? Cursor.TEXT_CURSOR : Cursor.DEFAULT_CURSOR;
        sourceValueField.setCursor(Cursor.getPredefinedCursor(cursorType));
        sourceValueField.setHintText(presentation.placeholder());
        sourceValueField.putClientProperty("JTextField.showClearButton", presentation.editable());
        sourceValueField.setToolTipText(presentation.tooltip());
        sourceValueField.setCaretPosition(0);
        packageActionPanel.setVisible(sourceType == PackageSourceType.OFFICIAL_LATEST);
        updateOfficialLatestPackageButton.setVisible(sourceType == PackageSourceType.OFFICIAL_LATEST);
        lastSourceType = sourceType;
    }

    private SourceValuePresentation describeSourceValue(PackageSourceType sourceType, String draftValue) {
        String normalizedDraft = draftValue == null ? "" : draftValue.trim();
        return switch (sourceType) {
            case OFFICIAL_LATEST ->
                new SourceValuePresentation(
                        OFFICIAL_LATEST_DOWNLOAD_URL,
                        "",
                        false,
                        false,
                        false,
                        message("settings.source.tooltip.official_latest", OFFICIAL_LATEST_DOWNLOAD_URL));
            case OFFICIAL_VERSION ->
                new SourceValuePresentation(
                        normalizedDraft,
                        message("settings.source.placeholder.official_version", OFFICIAL_VERSION_PLACEHOLDER),
                        true,
                        false,
                        true,
                        message("settings.source.tooltip.official_version"));
            case CUSTOM_REMOTE_ZIP ->
                new SourceValuePresentation(
                        normalizedDraft,
                        message("settings.source.placeholder.custom_remote_zip", CUSTOM_REMOTE_ZIP_PLACEHOLDER),
                        true,
                        false,
                        true,
                        message("settings.source.tooltip.custom_remote_zip"));
            case LOCAL_ZIP ->
                new SourceValuePresentation(
                        normalizedDraft,
                        message("settings.source.placeholder.local_zip"),
                        false,
                        true,
                        false,
                        message("settings.source.tooltip.local_zip"));
            case LOCAL_PATH ->
                new SourceValuePresentation(
                        normalizedDraft,
                        message("settings.source.placeholder.local_path"),
                        false,
                        true,
                        false,
                        message("settings.source.tooltip.local_path"));
        };
    }

    private String normalizeSourceValueForSaving(PackageSourceType sourceType, String rawValue) {
        if (sourceType == PackageSourceType.OFFICIAL_LATEST) {
            return "";
        }
        return rawValue == null ? "" : rawValue.trim();
    }

    private String resolveGatewayTokenForSaving() {
        String configuredToken = mcpGatewayTokenField.getText().trim();
        if (currentGatewayAuthMode().usesGeneratedPassword() && configuredToken.isBlank()) {
            configuredToken = randomToken();
            mcpGatewayTokenField.setText(configuredToken);
        }
        return configuredToken;
    }

    private JBTextArea createNoteArea(String text) {
        JBTextArea area = new JBTextArea(text);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        return area;
    }

    /**
     * 复制固定 Gateway MCP 配置，便于直接粘贴到支持 MCP 的客户端。
     */
    private void copyMcpConfig() {
        ArthasWorkbenchSettingsService.SettingsState state = toState();
        String port = state.mcpGatewayPort == null || state.mcpGatewayPort.isBlank() ? "18765" : state.mcpGatewayPort;
        GatewayAuthMode gatewayAuthMode = GatewayAuthMode.fromValue(state.mcpGatewayAuthMode, state.mcpGatewayToken);
        String baseUrl;
        ArthasMcpGatewayService gatewayService = ApplicationManager.getApplication() == null
                ? null
                : ApplicationManager.getApplication().getService(ArthasMcpGatewayService.class);
        if (gatewayService != null) {
            try {
                baseUrl = gatewayService.getBaseUrl();
            } catch (Exception ignored) {
                baseUrl = "http://127.0.0.1:" + port + "/gateway";
            }
        } else {
            baseUrl = "http://127.0.0.1:" + port + "/gateway";
        }
        String gatewayToken = gatewayAuthMode == GatewayAuthMode.DISABLED ? "" : state.mcpGatewayToken;
        UiToolkit.copyToClipboard(buildGatewayMcpConfig(baseUrl, gatewayToken));
    }

    static String buildGatewayMcpConfig(String baseUrl, String gatewayToken) {
        return McpConfigFormatter.formatGateway(baseUrl + "/mcp", gatewayToken);
    }

    /**
     * 重新下载官方最新版包，并覆盖本地缓存，方便用户在不离开 Settings 的情况下主动刷新最新版本。
     */
    private void updateOfficialLatestPackage() {
        if (currentSourceType() != PackageSourceType.OFFICIAL_LATEST) {
            return;
        }
        updateOfficialLatestPackageButton.setEnabled(false);
        UiToolkit.notifyInfo(project, message("settings.package.updating"));
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ArthasPackageService packageService =
                        ApplicationManager.getApplication().getService(ArthasPackageService.class);
                packageService.resolve(new PackageSourceSpec(PackageSourceType.OFFICIAL_LATEST), true);
                ApplicationManager.getApplication().invokeLater(() -> {
                    updateOfficialLatestPackageButton.setEnabled(true);
                    UiToolkit.notifyInfo(project, message("settings.package.updated"));
                });
            } catch (Exception exception) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    updateOfficialLatestPackageButton.setEnabled(true);
                    UiToolkit.notifyError(project, message("settings.package.update_failed", exception.getMessage()));
                });
            }
        });
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String message(String key, Object... params) {
        return ArthasWorkbenchBundle.message(key, params);
    }

    /**
     * 描述“版本 / 地址 / 路径”输入框当前的显示文本、占位、交互方式和提示。
     */
    private record SourceValuePresentation(
            String displayText,
            String placeholder,
            boolean editable,
            boolean clickToChoose,
            boolean focusable,
            String tooltip) {}

    /**
     * 自定义“版本 / 地址 / 路径”输入框的空态提示绘制，避免依赖不同 IDEA 主题下不稳定的 placeholder clientProperty。
     */
    private static final class SourceValueField extends JBTextField {

        private String hintText = "";

        String getHintText() {
            return hintText;
        }

        void setHintText(String hintText) {
            this.hintText = hintText == null ? "" : hintText;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!getText().isEmpty() || hintText.isBlank()) {
                return;
            }
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                FontMetrics metrics = graphics2D.getFontMetrics(getFont());
                Insets insets = getInsets();
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                int x = insets.left + 2;
                graphics2D.setFont(getFont());
                graphics2D.setColor(resolveHintColor());
                graphics2D.drawString(hintText, x, baseline);
            } finally {
                graphics2D.dispose();
            }
        }

        private Color resolveHintColor() {
            Color color = UIManager.getColor("TextField.inactiveForeground");
            if (color != null) {
                return color;
            }
            Color fallback = UIManager.getColor("Label.disabledForeground");
            return fallback == null ? Color.GRAY : fallback;
        }
    }
}
