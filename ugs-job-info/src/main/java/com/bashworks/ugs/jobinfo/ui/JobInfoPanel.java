package com.bashworks.ugs.jobinfo.ui;

import com.bashworks.ugs.jobinfo.kinematics.MachineProfile;
import com.bashworks.ugs.jobinfo.kinematics.SimulationResult;
import com.bashworks.ugs.jobinfo.machine.JobInfoMachineGateway;
import com.bashworks.ugs.jobinfo.tracker.JobSnapshot;
import com.bashworks.ugs.jobinfo.tracker.JobState;
import com.bashworks.ugs.jobinfo.tracker.JobTracker;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.Locale;

/**
 * Main dashboard panel for UGS Better Job Info.
 * Displays high-accuracy total time, elapsed time, true progress %, remaining time countdown,
 * live ETA, gradient progress bar, and machine kinematics telemetry.
 */
public class JobInfoPanel extends JPanel {

    private final JobInfoMachineGateway gateway;
    private final JobTracker tracker;

    // Header components
    private final JLabel lblStatusBadge = new JLabel(" IDLE ");
    private final JLabel lblFileName = new JLabel("No file loaded");
    private final JLabel lblFeedOverrideBadge = new JLabel("100% Feed");

    // 4 Primary Metric Cards
    private final MetricCard cardTotalTime = new MetricCard("Total Estimated Time", UITheme.ACCENT_BLUE);
    private final MetricCard cardRemainingTime = new MetricCard("Time Remaining", UITheme.ACCENT_AMBER);
    private final MetricCard cardElapsedTime = new MetricCard("Time Passed (Elapsed)", UITheme.ACCENT_GREEN);
    private final MetricCard cardProgressPercent = new MetricCard("Progress Completed", UITheme.ACCENT_PURPLE);

    // Progress bar
    private final SmoothProgressBar progressBar = new SmoothProgressBar();

    // Footer info labels
    private final JLabel lblMachineLimits = new JLabel("Machine limits loading…");
    private final JLabel lblDistanceStats = new JLabel("Distances loading…");
    private final JLabel lblBoundsStats = new JLabel("Work envelope loading…");

    public JobInfoPanel(JobInfoMachineGateway gateway) {
        this.gateway = gateway;
        this.tracker = new JobTracker(gateway);

        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.BG_DARK);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildFooterPanel(), BorderLayout.SOUTH);

        tracker.addSnapshotListener(this::updateFromSnapshot);
        tracker.start();
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);

        // Title and Status
        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftHeader.setOpaque(false);

        JLabel title = new JLabel("UGS BETTER JOB INFO");
        title.setFont(UITheme.FONT_HEADER);
        title.setForeground(UITheme.TEXT_PRIMARY);

        setupBadge(lblStatusBadge, UITheme.CARD_BORDER, UITheme.TEXT_MUTED);
        setupBadge(lblFeedOverrideBadge, UITheme.CARD_BORDER, UITheme.ACCENT_BLUE);

        lblFileName.setFont(UITheme.FONT_SUBTITLE);
        lblFileName.setForeground(UITheme.TEXT_MUTED);

        leftHeader.add(title);
        leftHeader.add(lblStatusBadge);
        leftHeader.add(lblFeedOverrideBadge);
        leftHeader.add(lblFileName);

        // Action Buttons using DarkButton (custom-painted, immune to OS L&F white backgrounds)
        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightHeader.setOpaque(false);

        DarkButton btnSettings = new DarkButton("Machine Limits");
        btnSettings.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, this);
            KinematicsConfigDialog dlg = new KinematicsConfigDialog(owner, gateway);
            dlg.setVisible(true);
        });

        DarkButton btnRescan = new DarkButton("Re-Calculate");
        btnRescan.addActionListener(e -> tracker.triggerResimulation());

        rightHeader.add(btnSettings);
        rightHeader.add(btnRescan);

        header.add(leftHeader, BorderLayout.WEST);
        header.add(rightHeader, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenterPanel() {
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        // 2x2 Metric Cards Grid
        JPanel cardsGrid = new JPanel(new GridLayout(2, 2, 12, 12));
        cardsGrid.setOpaque(false);
        cardsGrid.add(cardTotalTime);
        cardsGrid.add(cardRemainingTime);
        cardsGrid.add(cardElapsedTime);
        cardsGrid.add(cardProgressPercent);

        center.add(cardsGrid);
        center.add(Box.createVerticalStrut(12));
        center.add(progressBar);

        return center;
    }

    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UITheme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(UITheme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        lblMachineLimits.setFont(UITheme.FONT_SUBTITLE);
        lblMachineLimits.setForeground(UITheme.TEXT_MUTED);

        lblDistanceStats.setFont(UITheme.FONT_SUBTITLE);
        lblDistanceStats.setForeground(UITheme.TEXT_MUTED);

        lblBoundsStats.setFont(UITheme.FONT_SUBTITLE);
        lblBoundsStats.setForeground(UITheme.TEXT_DIM);

        footer.add(lblMachineLimits);
        footer.add(Box.createVerticalStrut(3));
        footer.add(lblDistanceStats);
        footer.add(Box.createVerticalStrut(3));
        footer.add(lblBoundsStats);

        return footer;
    }

    private void updateFromSnapshot(JobSnapshot s) {
        SwingUtilities.invokeLater(() -> {
            // Update status badge
            updateStatusBadge(s.state());

            // Update file name
            lblFileName.setText(s.fileName());

            // Update feed override badge
            lblFeedOverrideBadge.setText(s.feedOverridePercent() + "% Feed");
            if (s.feedOverridePercent() != 100) {
                lblFeedOverrideBadge.setForeground(UITheme.ACCENT_AMBER);
            } else {
                lblFeedOverrideBadge.setForeground(UITheme.ACCENT_BLUE);
            }

            // Card 1: Total Time
            if (s.totalTimeSeconds() > 0) {
                cardTotalTime.setValue(UITheme.formatDuration(s.totalTimeSeconds()));
                SimulationResult sim = s.simulationResult();
                cardTotalTime.setSubtitle(String.format("Cut: %s | Rapid: %s",
                        UITheme.formatDuration(sim.getCutDurationSeconds()),
                        UITheme.formatDuration(sim.getRapidDurationSeconds())));
            } else {
                cardTotalTime.setValue("--:--:--");
                cardTotalTime.setSubtitle(s.state() == JobState.SIMULATING ? "Simulating trajectory…" : "No job planned");
            }

            // Card 2: Time Remaining
            if (s.state() == JobState.COMPLETED) {
                cardRemainingTime.setValue("00s");
                cardRemainingTime.setSubtitle("Job completed");
            } else if (s.remainingTimeSeconds() > 0) {
                cardRemainingTime.setValue(UITheme.formatDuration(s.remainingTimeSeconds()));
                cardRemainingTime.setSubtitle("Estimated finish: " + s.etaString());
            } else {
                cardRemainingTime.setValue("--:--:--");
                cardRemainingTime.setSubtitle(s.totalTimeSeconds() > 0 ? "Ready to run" : "Awaiting job");
            }

            // Card 3: Elapsed Time
            cardElapsedTime.setValue(UITheme.formatDuration(s.elapsedTimeSeconds()));
            if (s.state() == JobState.RUNNING) {
                cardElapsedTime.setSubtitle("Actively streaming in UGS");
            } else if (s.state() == JobState.PAUSED) {
                cardElapsedTime.setSubtitle("Stream held / paused in UGS");
            } else if (s.state() == JobState.COMPLETED) {
                cardElapsedTime.setSubtitle("Finished");
            } else {
                cardElapsedTime.setSubtitle("Stopped");
            }

            // Card 4: Progress %
            cardProgressPercent.setValue(String.format(Locale.US, "%.1f %%", s.progressPercent()));
            if (s.totalCommands() > 0) {
                cardProgressPercent.setSubtitle(String.format("Command %d / %d", s.completedCommands(), s.totalCommands()));
            } else {
                cardProgressPercent.setSubtitle("Kinematic time basis");
            }

            // Progress bar
            progressBar.setProgressPercent(s.progressPercent());
            if (s.totalCommands() > 0) {
                progressBar.setCustomText(String.format(Locale.US, "%.1f %%  (Cmd %d / %d)",
                        s.progressPercent(), s.completedCommands(), s.totalCommands()));
            } else {
                progressBar.setCustomText(null);
            }

            // Footer telemetry
            MachineProfile p = s.machineProfile();
            lblMachineLimits.setText(String.format(Locale.US,
                    "Machine: %s | Accel: X:%.0f Y:%.0f Z:%.0f mm/s² | Max: X:%.0f Y:%.0f Z:%.0f mm/min | Junction Dev: %.3f mm",
                    p.getName(), p.getAccelX(), p.getAccelY(), p.getAccelZ(),
                    p.getMaxRateX(), p.getMaxRateY(), p.getMaxRateZ(), p.getJunctionDeviation()));

            SimulationResult sim = s.simulationResult();
            if (sim.getTotalDistanceMm() > 0) {
                lblDistanceStats.setText(String.format(Locale.US,
                        "Travel: Total: %s | Cutting: %s | Rapid: %s | Blocks: %d",
                        UITheme.formatDistance(sim.getTotalDistanceMm()),
                        UITheme.formatDistance(sim.getCutDistanceMm()),
                        UITheme.formatDistance(sim.getRapidDistanceMm()),
                        sim.getTotalSegments()));

                lblBoundsStats.setText(String.format(Locale.US,
                        "Work Envelope: X: [%.1f to %.1f] | Y: [%.1f to %.1f] | Z: [%.1f to %.1f] mm",
                        sim.getMinX(), sim.getMaxX(), sim.getMinY(), sim.getMaxY(), sim.getMinZ(), sim.getMaxZ()));
            } else {
                lblDistanceStats.setText("Travel: No trajectory planned");
                lblBoundsStats.setText("Work Envelope: Awaiting file");
            }
        });
    }

    private void updateStatusBadge(JobState st) {
        lblStatusBadge.setText(" " + st.name() + " ");
        switch (st) {
            case RUNNING:
                lblStatusBadge.setBackground(UITheme.ACCENT_GREEN);
                lblStatusBadge.setForeground(Color.BLACK);
                break;
            case PAUSED:
                lblStatusBadge.setBackground(UITheme.ACCENT_AMBER);
                lblStatusBadge.setForeground(Color.BLACK);
                break;
            case SIMULATING:
                lblStatusBadge.setBackground(UITheme.ACCENT_BLUE);
                lblStatusBadge.setForeground(Color.BLACK);
                break;
            case READY:
                lblStatusBadge.setBackground(new Color(0x23, 0x86, 0x36));
                lblStatusBadge.setForeground(Color.WHITE);
                break;
            case COMPLETED:
                lblStatusBadge.setBackground(UITheme.ACCENT_PURPLE);
                lblStatusBadge.setForeground(Color.BLACK);
                break;
            case DISCONNECTED:
                lblStatusBadge.setBackground(new Color(0x3a, 0x3d, 0x45));
                lblStatusBadge.setForeground(UITheme.TEXT_MUTED);
                break;
            default:
                lblStatusBadge.setBackground(UITheme.CARD_BORDER);
                lblStatusBadge.setForeground(UITheme.TEXT_MUTED);
                break;
        }

    }

    private void setupBadge(JLabel badge, Color bg, Color fg) {
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setForeground(fg);
        badge.setFont(UITheme.FONT_BADGE);
        badge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
    }

    public void close() {
        tracker.stop();
    }
}
