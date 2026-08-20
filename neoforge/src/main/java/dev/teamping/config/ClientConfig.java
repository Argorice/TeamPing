package dev.teamping.config;

/** Клиентский конфиг — у каждого игрока свой, сервер на него не влияет. */
public final class ClientConfig {
    /** Множитель размера иконок, 0.5 – 2.0. */
    public double pingScale = 1.0D;
    /** Рисовать ли иконки сквозь стены (полупрозрачным проходом). */
    public boolean showThroughWalls = true;
    /** Стрелки-указатели по краю экрана для пингов вне поля зрения. */
    public boolean showOffscreenArrows = true;
    /** Сколько стрелок максимум, 1 – 8. */
    public int maxOffscreenArrows = 4;
    /** Громкость звука пинга, 0.0 – 1.0. */
    public double soundVolume = 1.0D;
    /** Не показывать собственные пинги. */
    public boolean hideOwnPings = false;
    /** Короткая строка над хотбаром вместо сообщения в чат. */
    public boolean showActionbarNotice = true;
    /** Показывать дистанцию до пинга под иконкой. */
    public boolean showDistance = true;
    /**
     * Что дублировать метками на карту (Xaero и прочие, если они стоят):
     * {@code all} — пинги и вейпоинты, {@code waypoints} — только вейпоинты,
     * {@code none} — ничего.
     */
    public String mapMarkers = "all";

    public void clamp() {
        if (!"all".equals(mapMarkers) && !"waypoints".equals(mapMarkers) && !"none".equals(mapMarkers)) {
            mapMarkers = "all";
        }
        pingScale = clamp(pingScale, 0.5D, 2.0D);
        maxOffscreenArrows = (int) clamp(maxOffscreenArrows, 1, 8);
        soundVolume = clamp(soundVolume, 0.0D, 1.0D);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
