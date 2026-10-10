package com.bashworks.ugs.jobinfo.ui;

import com.bashworks.ugs.jobinfo.kinematics.MachineProfile;
import com.bashworks.ugs.jobinfo.machine.JobInfoMachineGateway;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.Locale;

/**
 * Read-only dialog displaying detected machine kinematic parameters ($110-$122, $11, $12).
 * Values are automatically loaded from controller firmware settings on startup.
 */
public class KinematicsConfigDialog extends JDialog {

    private final JobInfoMachineGateway gateway;

    private final JLabel lblProfileName = new JLabel("Machine Profile: Loading…");
    private final JLabel lblAccelX = createValueLabel();
    private final JLabel lblAccelY = createValueLabel();
    private final JLabel lblAccelZ = createValueLabel();

    private final JLabel lblMaxRateX = createValueLabel();
    private final JLabel lblMaxRateY = createValueLabel();
    private final JLabel lblMaxRateZ = createValueLabel();

    private final JLabel lblJunctionDev = createValueLabel();
    private final JLabel lblArcTol = createValueLabel();
    private final JLabel lblSpindleDelay = createValueLabel();

    public KinematicsConfigDialog(Frame owner, JobInfoMachineGateway gateway) {
        super(owner, "Machine Kinematic Limits (Read-Only)", true);
        this.gateway = gateway;

        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BG_DARK);

        JPanel mainPanel = new JPanel();
        mainPanel.setOpaque(false);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        // Header info
        lblProfileName.setFont(UITheme.FONT_TITLE);
        lblProfileName.setForeground(UITheme.ACCENT_BLUE);
        mainPanel.add(lblProfileName);

        JLabel lblSub = new JLabel("Values automatically queried from controller firmware settings ($$):");
        lblSub.setFont(UITheme.FONT_SUBTITLE);
        lblSub.setForeground(UITheme.TEXT_MUTED);
        mainPanel.add(lblSub);
        mainPanel.add(Box.createVerticalStrut(16));

        // Read-only values grid
        JPanel formGrid = new JPanel(new GridLayout(5, 2, 16, 10));
        formGrid.setOpaque(false);

        formGrid.add(createParamRow("X Acceleration ($120):", lblAccelX));
        formGrid.add(createParamRow("X Max Velocity ($110):", lblMaxRateX));

        formGrid.add(createParamRow("Y Acceleration ($121):", lblAccelY));
        formGrid.add(createParamRow("Y Max Velocity ($111):", lblMaxRateY));

        formGrid.add(createParamRow("Z Acceleration ($122):", lblAccelZ));
        formGrid.add(createParamRow("Z Max Velocity ($112):", lblMaxRateZ));

        formGrid.add(createParamRow("Junction Deviation ($11):", lblJunctionDev));
        formGrid.add(createParamRow("Arc Tolerance ($12):", lblArcTol));

        formGrid.add(createParamRow("Spindle Spin-up Delay:", lblSpindleDelay));
        formGrid.add(new JLabel("")); // spacer

        mainPanel.add(formGrid);
        mainPanel.add(Box.createVerticalStrut(20));

        // Button row
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);

        DarkButton btnRefresh = new DarkButton("Query Machine ($$)");
        btnRefresh.addActionListener(e -> {
            gateway.requestSettingsRefresh();
            loadFromProfile(gateway.getLiveMachineProfile());
        });

        DarkButton btnClose = new DarkButton("Close");
        btnClose.addActionListener(e -> dispose());

        buttonRow.add(btnRefresh);
        buttonRow.add(btnClose);
        mainPanel.add(buttonRow);

        add(mainPanel, BorderLayout.CENTER);

        loadFromProfile(gateway.getLiveMachineProfile());
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static JLabel createValueLabel() {
        JLabel l = new JLabel("--");
        l.setFont(UITheme.FONT_MONO);
        l.setForeground(UITheme.TEXT_PRIMARY);
        return l;
    }

    private JPanel createParamRow(String labelText, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(UITheme.FONT_SUBTITLE);
        lbl.setForeground(UITheme.TEXT_MUTED);
        row.add(lbl, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private void loadFromProfile(MachineProfile p) {
        if (p == null) return;
        lblProfileName.setText("Machine Profile: " + p.getName());
        lblAccelX.setText(String.format(Locale.US, "%.1f mm/s²", p.getAccelX()));
        lblAccelY.setText(String.format(Locale.US, "%.1f mm/s²", p.getAccelY()));
        lblAccelZ.setText(String.format(Locale.US, "%.1f mm/s²", p.getAccelZ()));
        lblMaxRateX.setText(String.format(Locale.US, "%,.0f mm/min", p.getMaxRateX()));
        lblMaxRateY.setText(String.format(Locale.US, "%,.0f mm/min", p.getMaxRateY()));
        lblMaxRateZ.setText(String.format(Locale.US, "%,.0f mm/min", p.getMaxRateZ()));
        lblJunctionDev.setText(String.format(Locale.US, "%.4f mm", p.getJunctionDeviation()));
        lblArcTol.setText(String.format(Locale.US, "%.4f mm", p.getArcTolerance()));
        lblSpindleDelay.setText(String.format(Locale.US, "%.1f s", p.getSpindleSpinUpDelaySeconds()));
    }
}
