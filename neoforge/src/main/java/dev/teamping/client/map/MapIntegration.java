package dev.teamping.client.map;

import dev.teamping.ping.Ping;

import java.util.UUID;

/**
 * Мост к конкретному моду-карте. Реализация обязана быть терпимой:
 * карта — не критичная часть, и любая её поломка не должна ронять пинги.
 */
public interface MapIntegration {

    /** Имя для лога. */
    String name();

    void add(Ping ping);

    void remove(UUID pingId);

    void clear();
}
