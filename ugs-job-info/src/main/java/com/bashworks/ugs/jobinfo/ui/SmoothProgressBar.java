package com.bashworks.ugs.jobinfo.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;

/**
 * Custom anti-aliased gradient progress bar with rounded corners and high-contrast typography.
 */
public class SmoothProgressBar extends JComponent {

    private double progressPercent = 0.0;
    private String customText = null;

    public SmoothProgressBar() {
        setPreferredSize(new Dimension(300, 26));
        setMinimumSize(new Dimension(150, 22));
    }

    public void setProgressPercent(double percent) {
        this.progressPercent = Math.max(0.0, Math.min(100.0, percent));
        repaint();
    }

    public void setCustomText(String text) {
        this.customText = text;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = h;

        // Background track
        g2.setColor(UITheme.CARD_BG);
        g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g2.setColor(UITheme.CARD_BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

        // Progress fill
        int fillWidth = (int) Math.round((w - 2) * (progressPercent / 100.0));
        if (fillWidth > 2) {
            GradientPaint gradient = new GradientPaint(
                    0, 0, UITheme.ACCENT_BLUE,
                    fillWidth, 0, UITheme.ACCENT_PURPLE
            );
            g2.setPaint(gradient);
            g2.fillRoundRect(1, 1, fillWidth, h - 2, arc, arc);
        }

        // Percentage text
        String text = (customText != null) ? customText : String.format(Locale.US, "%.1f %%", progressPercent);
        g2.setFont(UITheme.FONT_TITLE);
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textX = (w - textWidth) / 2;
        int textY = ((h - fm.getHeight()) / 2) + fm.getAscent();

        // Text shadow / glow for readability
        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawString(text, textX + 1, textY + 1);

        g2.setColor(UITheme.TEXT_PRIMARY);
        g2.drawString(text, textX, textY);

        g2.dispose();
    }
}
