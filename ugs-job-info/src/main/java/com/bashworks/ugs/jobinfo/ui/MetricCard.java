package com.bashworks.ugs.jobinfo.ui;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;

/**
 * High-contrast, rounded dashboard metric card with colored accent marker,
 * prominent value typography, and contextual subtitle.
 */
public class MetricCard extends JPanel {

    private final Color accentColor;
    private final JLabel titleLabel;
    private final JLabel valueLabel;
    private final JLabel subtitleLabel;

    public MetricCard(String title, Color accentColor) {
        this.accentColor = accentColor;
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        setPreferredSize(new Dimension(200, 95));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Title row with accent bullet
        JPanel titleRow = new JPanel();
        titleRow.setOpaque(false);
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));

        titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(UITheme.FONT_TITLE);
        titleLabel.setForeground(UITheme.TEXT_MUTED);
        titleRow.add(titleLabel);
        titleRow.add(Box.createHorizontalGlue());

        // Value row
        valueLabel = new JLabel("--");
        valueLabel.setFont(UITheme.FONT_VALUE);
        valueLabel.setForeground(UITheme.TEXT_PRIMARY);

        // Subtitle row
        subtitleLabel = new JLabel(" ");
        subtitleLabel.setFont(UITheme.FONT_SUBTITLE);
        subtitleLabel.setForeground(UITheme.TEXT_DIM);

        content.add(titleRow);
        content.add(Box.createVerticalStrut(4));
        content.add(valueLabel);
        content.add(Box.createVerticalStrut(2));
        content.add(subtitleLabel);

        add(content, BorderLayout.CENTER);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public void setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle != null ? subtitle : " ");
    }

    public void setValueColor(Color color) {
        valueLabel.setForeground(color != null ? color : UITheme.TEXT_PRIMARY);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Card background
        g2.setColor(UITheme.CARD_BG);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);

        // Card border
        g2.setColor(UITheme.CARD_BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);

        // Left accent bar
        g2.setColor(accentColor);
        g2.fillRoundRect(0, 6, 4, h - 13, 3, 3);

        g2.dispose();
        super.paintComponent(g);
    }
}
