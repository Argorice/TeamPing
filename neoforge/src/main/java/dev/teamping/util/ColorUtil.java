package dev.teamping.util;

import java.awt.Color;

public final class ColorUtil {
    private ColorUtil() {
    }

    /**
     * Стабильный приятный цвет по нику — используется, когда команд нет
     * и брать цвет неоткуда. Насыщенность и яркость фиксированы, чтобы
     * ни у кого не вышло чёрного или ядовитого маркера.
     */
    public static int fromName(String name) {
        int hash = name.hashCode();
        float hue = ((hash % 360) + 360) % 360 / 360.0F;
        return Color.HSBtoRGB(hue, 0.55F, 1.0F) & 0xFFFFFF;
    }

    public static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    public static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    public static int blue(int rgb) {
        return rgb & 0xFF;
    }

    /** Собирает ARGB для рендера из RGB и альфы 0..1. */
    public static int withAlpha(int rgb, float alpha) {
        int a = (int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
