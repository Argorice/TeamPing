package dev.teamping.config;

/** Серверный конфиг — правила, которым подчиняются все клиенты. */
public final class ServerConfig {
    /** Максимальная дистанция от игрока до точки пинга, блоки. */
    public double maxPingDistance = 256.0D;
    /** Не чаще одного пинга в этот интервал, мс. */
    public long rateLimitMs = 1000L;
    /** Сколько вейпоинтов одновременно может держать один игрок. */
    public int maxWaypointsPerPlayer = 8;
    /** Радиус получателей, когда команд нет вообще. */
    public double soloModeRadius = 512.0D;
    /**
     * Откуда брать команды: {@code auto}, {@code ftb}, {@code scoreboard}, {@code solo}.
     *
     * <p>{@code auto} — все источники сразу: состав команды это объединение, а
     * «одна команда» означает совпадение хотя бы по одному источнику. Так надо
     * потому, что в сборках команду нередко заводят дважды — пати в FTB и заодно
     * ванильную. Обратная сторона: выход из одной команды не отменяет вторую,
     * и метка, помеченная обеими, останется видимой.
     *
     * <p>Если такое поведение мешает, {@code ftb} или {@code scoreboard} оставляют
     * ровно один источник, а {@code solo} игнорирует команды вовсе.
     */
    public String teamProvider = "auto";

    public void clamp() {
        if (!"auto".equals(teamProvider) && !"ftb".equals(teamProvider)
                && !"scoreboard".equals(teamProvider) && !"solo".equals(teamProvider)) {
            teamProvider = "auto";
        }
        maxPingDistance = Math.max(16.0D, Math.min(1024.0D, maxPingDistance));
        rateLimitMs = Math.max(0L, Math.min(60_000L, rateLimitMs));
        maxWaypointsPerPlayer = Math.max(1, Math.min(64, maxWaypointsPerPlayer));
        soloModeRadius = Math.max(16.0D, Math.min(4096.0D, soloModeRadius));
    }
}
