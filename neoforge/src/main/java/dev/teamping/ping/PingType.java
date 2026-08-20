package dev.teamping.ping;

import dev.teamping.TeamPing;
import net.minecraft.resources.ResourceLocation;

/**
 * Тип пинга задаёт цвет по умолчанию, время жизни, иконку и питч звука.
 * Порядок констант — часть протокола (пишутся как enum-ординал), не менять местами.
 */
public enum PingType {
    NORMAL(0xFFFFFF, 20_000L, true, 1.60F),
    DANGER(0xFF4444, 20_000L, false, 0.72F),
    RESOURCE(0x44FF88, 30_000L, false, 1.35F),
    WAYPOINT(0x44AAFF, 0L, true, 1.10F),
    ALLY(0x55AAFF, 20_000L, false, 1.45F),
    ENEMY(0xFF3355, 20_000L, false, 0.85F),
    VESSEL(0xFFCC44, 30_000L, false, 1.20F);

    private final int defaultColor;
    private final long lifetimeMs;
    private final boolean useTeamColor;
    private final float pitch;
    private final ResourceLocation texture;

    PingType(int defaultColor, long lifetimeMs, boolean useTeamColor, float pitch) {
        this.defaultColor = defaultColor;
        this.lifetimeMs = lifetimeMs;
        this.useTeamColor = useTeamColor;
        this.pitch = pitch;
        this.texture = TeamPing.id("textures/ping/" + name().toLowerCase(java.util.Locale.ROOT) + ".png");
    }

    public int defaultColor() {
        return defaultColor;
    }

    /** 0 — бессрочно (вейпоинт). */
    public long lifetimeMs() {
        return lifetimeMs;
    }

    public boolean useTeamColor() {
        return useTeamColor;
    }

    public float pitch() {
        return pitch;
    }

    public ResourceLocation texture() {
        return texture;
    }

    public boolean isPermanent() {
        return lifetimeMs <= 0L;
    }

    public String translationKey() {
        return "teamping.type." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Односимвольный значок типа. Нужен там, где иконку не вставить,
     * а место есть ровно под пару символов — например в вейпоинтах карт.
     */
    public String symbol() {
        return switch (this) {
            case NORMAL -> "\u25C6";
            case DANGER -> "\u26A0";
            case RESOURCE -> "\u26CF";
            case WAYPOINT -> "\u2691";
            case ALLY -> "\u25B2";
            case ENEMY -> "\u2716";
            case VESSEL -> "\u2693";
        };
    }
}
