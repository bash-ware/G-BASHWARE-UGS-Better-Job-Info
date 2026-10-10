package com.bashworks.ugs.jobinfo.mqtt;

import com.bashworks.ugs.jobinfo.ui.DarkButton;
import com.bashworks.ugs.jobinfo.ui.UITheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;

/**
 * Settings dialog for configuring remote MQTT telemetry broadcasting.
 * Supports 1-click free public broker preset or private HiveMQ Cloud Free (TLS + user/pass).
 */
public class MqttConfigDialog extends JDialog {

    private final MqttTelemetryService service = MqttTelemetryService.getInstance();
    private final MqttConfig currentConfig;

    private final JCheckBox chkEnabled = new JCheckBox("Enable Remote Broadcast over MQTT");
    private final JRadioButton rbPublic = new JRadioButton("Free Public Sandbox (broker.hivemq.com - 0 setup)");
    private final JRadioButton rbHiveCloud = new JRadioButton("HiveMQ Cloud Free (Private TLS + User/Password)");
    private final JRadioButton rbCustom = new JRadioButton("Custom Broker");

    private final JTextField txtHost = new JTextField(24);
    private final JTextField txtPort = new JTextField(6);
    private final JCheckBox chkUseTls = new JCheckBox("Use TLS / SSL Encryption");
    private final JTextField txtTopic = new JTextField(24);
    private final JTextField txtUsername = new JTextField(16);
    private final JPasswordField txtPassword = new JPasswordField(16);

    private final JLabel lblStatus = new JLabel("Status: Loading…");
    private final JLabel lblTestResult = new JLabel(" ");

    public MqttConfigDialog(Frame owner) {
        super(owner, "Remote Monitoring Settings (MQTT)", true);
        this.currentConfig = service.getConfig();

        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BG_DARK);

        JPanel mainPanel = new JPanel();
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        // Header Title
        JLabel lblTitle = new JLabel("REMOTE TELEMETRY BROADCAST (MQTT)");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.ACCENT_BLUE);
        mainPanel.add(lblTitle);

        JLabel lblSub = new JLabel("Publish live job progress (1 update/sec) to Android or mobile dashboard over the internet.");
        lblSub.setFont(UITheme.FONT_SUBTITLE);
        lblSub.setForeground(UITheme.TEXT_MUTED);
        mainPanel.add(lblSub);
        mainPanel.add(Box.createVerticalStrut(14));

        // Master Enable Toggle
        chkEnabled.setOpaque(false);
        chkEnabled.setFont(UITheme.FONT_HEADER);
        chkEnabled.setForeground(UITheme.ACCENT_GREEN);
        chkEnabled.setSelected(currentConfig.isEnabled());
        mainPanel.add(chkEnabled);
        mainPanel.add(Box.createVerticalStrut(14));

        // Presets Group
        JPanel presetPanel = new JPanel();
        presetPanel.setOpaque(false);
        presetPanel.setLayout(new BoxLayout(presetPanel, BoxLayout.Y_AXIS));
        presetPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(UITheme.CARD_BORDER),
                " Broker Preset ", 0, 0, UITheme.FONT_SUBTITLE, UITheme.TEXT_MUTED));

        ButtonGroup group = new ButtonGroup();
        setupRadio(rbPublic, group, presetPanel);
        setupRadio(rbHiveCloud, group, presetPanel);
        setupRadio(rbCustom, group, presetPanel);

        rbPublic.addActionListener(e -> applyPresetUI(true, false));
        rbHiveCloud.addActionListener(e -> applyPresetUI(false, true));
        rbCustom.addActionListener(e -> applyPresetUI(false, false));

        mainPanel.add(presetPanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // Form Fields Grid
        JPanel form = new JPanel(new GridLayout(6, 2, 12, 10));
        form.setOpaque(false);

        form.add(createLabel("Broker Host:"));
        form.add(txtHost);

        form.add(createLabel("Broker Port:"));
        JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        portPanel.setOpaque(false);
        portPanel.add(txtPort);
        chkUseTls.setOpaque(false);
        chkUseTls.setForeground(UITheme.TEXT_PRIMARY);
        portPanel.add(chkUseTls);
        form.add(portPanel);

        form.add(createLabel("MQTT Topic:"));
        JPanel topicPanel = new JPanel(new BorderLayout(6, 0));
        topicPanel.setOpaque(false);
        topicPanel.add(txtTopic, BorderLayout.CENTER);
        DarkButton btnNewTopic = new DarkButton("Randomize");
        btnNewTopic.addActionListener(e -> txtTopic.setText(MqttConfig.generateDefaultTopic()));
        topicPanel.add(btnNewTopic, BorderLayout.EAST);
        form.add(topicPanel);

        form.add(createLabel("Username (optional):"));
        form.add(txtUsername);

        form.add(createLabel("Password (optional):"));
        form.add(txtPassword);

        form.add(createLabel("Connection Status:"));
        lblStatus.setFont(UITheme.FONT_SUBTITLE);
        updateStatusLabel(service.getStatus(), service.getLastDetail());
        form.add(lblStatus);

        mainPanel.add(form);
        mainPanel.add(Box.createVerticalStrut(10));

        // Test result label
        lblTestResult.setFont(UITheme.FONT_SUBTITLE);
        lblTestResult.setForeground(UITheme.TEXT_MUTED);
        mainPanel.add(lblTestResult);
        mainPanel.add(Box.createVerticalStrut(14));

        // Button row
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);

        DarkButton btnTest = new DarkButton("Test Connection");
        btnTest.addActionListener(e -> runTestConnection(btnTest));

        DarkButton btnSave = new DarkButton("Save & Apply");
        btnSave.addActionListener(e -> {
            saveConfig();
            dispose();
        });

        DarkButton btnCancel = new DarkButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        btnRow.add(btnTest);
        btnRow.add(btnSave);
        btnRow.add(btnCancel);
        mainPanel.add(btnRow);

        add(mainPanel, BorderLayout.CENTER);

        // Load current config into inputs
        loadConfigToUI(currentConfig);

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void setupRadio(JRadioButton rb, ButtonGroup g, JPanel p) {
        rb.setOpaque(false);
        rb.setForeground(UITheme.TEXT_PRIMARY);
        rb.setFont(UITheme.FONT_SUBTITLE);
        g.add(rb);
        p.add(rb);
        p.add(Box.createVerticalStrut(4));
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.FONT_SUBTITLE);
        l.setForeground(UITheme.TEXT_MUTED);
        return l;
    }

    private void loadConfigToUI(MqttConfig c) {
        txtHost.setText(c.getHost());
        txtPort.setText(String.valueOf(c.getPort()));
        chkUseTls.setSelected(c.isUseTls());
        txtTopic.setText(c.getTopic());
        txtUsername.setText(c.getUsername());
        txtPassword.setText(c.getPassword());

        if (MqttConfig.DEFAULT_PUBLIC_HOST.equalsIgnoreCase(c.getHost()) && c.getPort() == MqttConfig.DEFAULT_PUBLIC_PORT) {
            rbPublic.setSelected(true);
        } else if (c.isUseTls() && c.getPort() == MqttConfig.DEFAULT_TLS_PORT) {
            rbHiveCloud.setSelected(true);
        } else {
            rbCustom.setSelected(true);
        }
    }

    private void applyPresetUI(boolean isPublic, boolean isHiveCloud) {
        if (isPublic) {
            txtHost.setText(MqttConfig.DEFAULT_PUBLIC_HOST);
            txtPort.setText(String.valueOf(MqttConfig.DEFAULT_PUBLIC_PORT));
            chkUseTls.setSelected(false);
            txtUsername.setText("");
            txtPassword.setText("");
        } else if (isHiveCloud) {
            if (txtHost.getText().trim().isEmpty() || MqttConfig.DEFAULT_PUBLIC_HOST.equalsIgnoreCase(txtHost.getText().trim())) {
                txtHost.setText("your-instance.s1.eu.hivemq.cloud");
            }
            txtPort.setText(String.valueOf(MqttConfig.DEFAULT_TLS_PORT));
            chkUseTls.setSelected(true);
        }
    }

    private void runTestConnection(DarkButton btn) {
        btn.setEnabled(false);
        lblTestResult.setText("Testing connection to " + txtHost.getText().trim() + "…");
        lblTestResult.setForeground(UITheme.ACCENT_BLUE);

        MqttConfig testCfg = buildConfigFromUI();
        new Thread(() -> {
            String error = MqttTelemetryService.testConnection(testCfg);
            SwingUtilities.invokeLater(() -> {
                btn.setEnabled(true);
                if (error == null) {
                    lblTestResult.setText("✓ Connection successful! Broker responded to ping.");
                    lblTestResult.setForeground(UITheme.ACCENT_GREEN);
                } else {
                    lblTestResult.setText("✗ Connection failed: " + error);
                    lblTestResult.setForeground(new Color(0xf8, 0x51, 0x49));
                }
            });
        }, "mqtt-test-thread").start();
    }

    private MqttConfig buildConfigFromUI() {
        MqttConfig c = new MqttConfig();
        c.setEnabled(chkEnabled.isSelected());
        c.setHost(txtHost.getText().trim());
        try {
            c.setPort(Integer.parseInt(txtPort.getText().trim()));
        } catch (NumberFormatException e) {
            c.setPort(chkUseTls.isSelected() ? MqttConfig.DEFAULT_TLS_PORT : MqttConfig.DEFAULT_PUBLIC_PORT);
        }
        c.setUseTls(chkUseTls.isSelected());
        c.setTopic(txtTopic.getText().trim());
        c.setUsername(txtUsername.getText().trim());
        c.setPassword(new String(txtPassword.getPassword()).trim());
        return c;
    }

    private void saveConfig() {
        MqttConfig c = buildConfigFromUI();
        service.updateConfig(c);
    }

    private void updateStatusLabel(MqttTelemetryService.Status st, String detail) {
        switch (st) {
            case CONNECTED:
                lblStatus.setText("● CONNECTED (" + detail + ")");
                lblStatus.setForeground(UITheme.ACCENT_GREEN);
                break;
            case CONNECTING:
                lblStatus.setText("◌ CONNECTING… (" + detail + ")");
                lblStatus.setForeground(UITheme.ACCENT_AMBER);
                break;
            case ERROR:
                lblStatus.setText("▲ ERROR (" + detail + ")");
                lblStatus.setForeground(new Color(0xf8, 0x51, 0x49));
                break;
            case DISABLED:
                lblStatus.setText("○ DISABLED");
                lblStatus.setForeground(UITheme.TEXT_MUTED);
                break;
            default:
                lblStatus.setText("○ DISCONNECTED (" + detail + ")");
                lblStatus.setForeground(UITheme.TEXT_MUTED);
                break;
        }
    }
}
