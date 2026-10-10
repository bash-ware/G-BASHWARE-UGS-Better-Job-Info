package com.bashworks.ugs.jobinfo.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Custom-painted flat dark button that bypasses native Look-and-Feel styling.
 * Guaranteed to render with crisp, high-contrast text and dark slate background
 * across all platforms (Windows, Linux, NetBeans RCP).
 */
public class DarkButton extends JButton {

    private boolean hovered = false;
    private boolean pressed = false;

    private static final Color BG_NORMAL = new Color(0x21, 0x26, 0x2D);
    private static final Color BG_HOVER = new Color(0x30, 0x36, 0x3D);
    private static final Color BG_PRESSED = new Color(0x16, 0x1B, 0x22);
    private static final Color BORDER_NORMAL = new Color(0x36, 0x3B, 0x42);
    private static final Color BORDER_HOVER = new Color(0x58, 0xA6, 0xFF);
    private static final Color TEXT_COLOR = new Color(0xF0, 0xF6, 0xFC);

    public DarkButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setFont(UITheme.FONT_SUBTITLE);
        setForeground(TEXT_COLOR);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressed = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Background
        Color bg = BG_NORMAL;
        if (pressed) {
            bg = BG_PRESSED;
        } else if (hovered) {
            bg = BG_HOVER;
        }
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);

        // Border
        g2.setColor(hovered ? BORDER_HOVER : BORDER_NORMAL);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

        // Text
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics(getFont());
        int tx = (w - fm.stringWidth(getText())) / 2;
        int ty = ((h - fm.getHeight()) / 2) + fm.getAscent();

        g2.setColor(hovered ? Color.WHITE : TEXT_COLOR);
        g2.drawString(getText(), tx, ty);

        g2.dispose();
    }
}
