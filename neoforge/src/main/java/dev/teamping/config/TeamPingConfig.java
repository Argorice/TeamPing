package dev.teamping.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.teamping.TeamPing;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Конфиг — простой JSON руками, без config-библиотек.
 * Файл отсутствует или битый — берём значения по умолчанию и перезаписываем.
 */
public final class TeamPingConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ClientConfig client = new ClientConfig();
    private static ServerConfig server = new ServerConfig();

    private TeamPingConfig() {
    }

    public static ClientConfig client() {
        return client;
    }

    public static ServerConfig server() {
        return server;
    }

    public static void loadClient() {
        client = load("teamping-client.json", ClientConfig.class, new ClientConfig());
        client.clamp();
        save("teamping-client.json", client);
    }

    public static void loadServer() {
        server = load("teamping-server.json", ServerConfig.class, new ServerConfig());
        server.clamp();
        save("teamping-server.json", server);
    }

    public static void saveClient() {
        client.clamp();
        save("teamping-client.json", client);
    }

    private static <T> T load(String fileName, Class<T> type, T fallback) {
        Path path = configPath(fileName);
        if (!Files.isRegularFile(path)) {
            return fallback;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T parsed = GSON.fromJson(reader, type);
            return parsed == null ? fallback : parsed;
        } catch (Exception e) {
            TeamPing.LOGGER.warn("Could not read {}, falling back to defaults", fileName, e);
            return fallback;
        }
    }

    private static void save(String fileName, Object value) {
        Path path = configPath(fileName);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
        } catch (IOException e) {
            TeamPing.LOGGER.warn("Could not save {}", fileName, e);
        }
    }

    private static Path configPath(String fileName) {
        return FMLPaths.CONFIGDIR.get().resolve(fileName);
    }
}
