package com.bashworks.ugs.jobinfo.ui;

import java.awt.Color;
import java.awt.Font;
import java.util.Locale;

public final class UITheme {

    // Industrial Dark Color Palette
    public static final Color BG_DARK = new Color(0x13, 0x16, 0x1A);
    public static final Color CARD_BG = new Color(0x1C, 0x21, 0x28);
    public static final Color CARD_BORDER = new Color(0x2D, 0x33, 0x3B);
    public static final Color CARD_HOVER = new Color(0x22, 0x27, 0x30);

    // Accent colors
    public static final Color ACCENT_BLUE = new Color(0x58, 0xA6, 0xFF);
    public static final Color ACCENT_GREEN = new Color(0x3F, 0xB9, 0x50);
    public static final Color ACCENT_AMBER = new Color(0xD2, 0x99, 0x22);
    public static final Color ACCENT_PURPLE = new Color(0xBC, 0x8C, 0xFF);
    public static final Color ACCENT_RED = new Color(0xF8, 0x51, 0x49);

    // Text colors
    public static final Color TEXT_PRIMARY = new Color(0xF0, 0xF6, 0xFC);
    public static final Color TEXT_MUTED = new Color(0x8B, 0x94, 0x9E);
    public static final Color TEXT_DIM = new Color(0x6E, 0x76, 0x81);

    // Typography
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_VALUE = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_MONO = new Font("Consolas", Font.BOLD, 12);
    public static final Font FONT_BADGE = new Font("Segoe UI", Font.BOLD, 10);

    private UITheme() {}

    /**
     * Formats seconds into "01h 42m 18s", "14m 20s", or "45s".
     */
    public static String formatDuration(double totalSeconds) {
        if (!Double.isFinite(totalSeconds) || totalSeconds < 0.0) {
            return "--:--:--";
        }

        long secs = (long) Math.round(totalSeconds);
        long hours = secs / 3600;
        long minutes = (secs % 3600) / 60;
        long seconds = secs % 60;

        if (hours > 0) {
            return String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format(Locale.US, "%02dm %02ds", minutes, seconds);
        } else {
            return String.format(Locale.US, "%02ds", seconds);
        }
    }

    /**
     * Formats distance in millimeters.
     */
    public static String formatDistance(double mm) {
        if (!Double.isFinite(mm) || mm < 0.0) {
            return "0 mm";
        }
        if (mm >= 1000.0) {
            return String.format(Locale.US, "%,.1f mm (%.2f m)", mm, mm / 1000.0);
        }
        return String.format(Locale.US, "%,.1f mm", mm);
    }
}
