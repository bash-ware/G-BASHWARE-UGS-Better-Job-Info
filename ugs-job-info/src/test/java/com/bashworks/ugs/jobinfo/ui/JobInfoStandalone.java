package com.bashworks.ugs.jobinfo.ui;

import com.bashworks.ugs.jobinfo.machine.SimulatedJobInfoMachineGateway;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Standalone Swing preview runner for UGS Better Job Info.
 * Displays the exact plugin panel layout as it appears when docked inside UGS Platform.
 */
public class JobInfoStandalone {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            JFrame frame = new JFrame("UGS Better Job Info — Panel Preview");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            SimulatedJobInfoMachineGateway gateway = new SimulatedJobInfoMachineGateway();
            JobInfoPanel panel = new JobInfoPanel(gateway);

            frame.add(panel, BorderLayout.CENTER);

            frame.setSize(820, 480);
            frame.setMinimumSize(new Dimension(650, 400));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
