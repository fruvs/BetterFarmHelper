package com.jelly.farmhelper.fabric.ui.framework;

import net.minecraft.util.Identifier;

/**
 * Simple theming container used by all custom FarmHelper UI screens.
 * Values are tuned for a dark, modern look similar to common script UIs.
 */
public final class UiTheme {

    public static final UiTheme DEFAULT_DARK = new UiTheme(
            0xE6161B28, // rootPanelBackground
            0xFF0B1019, // sidebarBackground
            0xFF111827, // contentBackground
            0xFF1F2937, // cardBackground
            0xFF374151, // controlBackground
            0xFF4B5563, // controlBackgroundHover
            0xFF22C55E, // accent
            0xFFEF4444, // danger
            0xFFE5E7EB, // textPrimary
            0xFF9CA3AF, // textSecondary
            0xFF4B5563, // borderColor
            0x60000000, // shadowColor
            6,          // cornerRadiusSmall
            10,         // cornerRadiusLarge
            6,          // paddingSmall
            10,         // paddingMedium
            14,         // paddingLarge
            null        // iconAtlas (optional)
    );

    public final int rootPanelBackground;
    public final int sidebarBackground;
    public final int contentBackground;
    public final int cardBackground;
    public final int controlBackground;
    public final int controlBackgroundHover;
    public final int accent;
    public final int danger;
    public final int textPrimary;
    public final int textSecondary;
    public final int borderColor;
    public final int shadowColor;

    public final int cornerRadiusSmall;
    public final int cornerRadiusLarge;

    public final int paddingSmall;
    public final int paddingMedium;
    public final int paddingLarge;

    public final Identifier iconAtlas;

    public UiTheme(
            int rootPanelBackground,
            int sidebarBackground,
            int contentBackground,
            int cardBackground,
            int controlBackground,
            int controlBackgroundHover,
            int accent,
            int danger,
            int textPrimary,
            int textSecondary,
            int borderColor,
            int shadowColor,
            int cornerRadiusSmall,
            int cornerRadiusLarge,
            int paddingSmall,
            int paddingMedium,
            int paddingLarge,
            Identifier iconAtlas
    ) {
        this.rootPanelBackground = rootPanelBackground;
        this.sidebarBackground = sidebarBackground;
        this.contentBackground = contentBackground;
        this.cardBackground = cardBackground;
        this.controlBackground = controlBackground;
        this.controlBackgroundHover = controlBackgroundHover;
        this.accent = accent;
        this.danger = danger;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.borderColor = borderColor;
        this.shadowColor = shadowColor;
        this.cornerRadiusSmall = cornerRadiusSmall;
        this.cornerRadiusLarge = cornerRadiusLarge;
        this.paddingSmall = paddingSmall;
        this.paddingMedium = paddingMedium;
        this.paddingLarge = paddingLarge;
        this.iconAtlas = iconAtlas;
    }
}

