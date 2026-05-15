package repositories;

import models.Settings;
import org.apache.pekko.actor.ActorSystem;
import play.db.Database;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class SettingsRepository {
    private final Database db;
    private final ExecutionContext dbEc;
    private final Map<Long, Settings> memorySettings = new ConcurrentHashMap<>();

    @Inject
    public SettingsRepository(Database db, ActorSystem system) {
        this.db = db;
        this.dbEc = system.dispatchers().lookup("blocking-db-dispatcher");
    }

    public CompletionStage<Settings> findByUserId(Long userId) {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                String sql = """
                    SELECT user_id, typing_indicators, show_online_status, notifications, sound_enabled
                    FROM user_settings
                    WHERE user_id = ?
                    """;
                try (var ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, userId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        Settings settings = new Settings();
                        settings.userId = rs.getLong("user_id");
                        settings.typingIndicators = rs.getBoolean("typing_indicators");
                        settings.showOnlineStatus = rs.getBoolean("show_online_status");
                        settings.notifications = rs.getBoolean("notifications");
                        settings.soundEnabled = rs.getBoolean("sound_enabled");
                        memorySettings.put(userId, settings);
                        return settings;
                    }
                    return memorySettings.computeIfAbsent(userId, Settings::new);
                }
            }),
            (ExecutionContextExecutor) dbEc
        ).exceptionally(t -> memorySettings.computeIfAbsent(userId, Settings::new));
    }

    public CompletionStage<Void> updateSetting(Long userId, String key, boolean value) {
        String column = columnFor(key);
        return CompletableFuture.runAsync(
            () -> db.withTransaction(conn -> {
                String sql = "UPDATE user_settings SET " + column + " = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
                try (var ps = conn.prepareStatement(sql)) {
                    ps.setBoolean(1, value);
                    ps.setLong(2, userId);
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        String insert = "INSERT INTO user_settings (user_id, " + column + ") VALUES (?, ?) " +
                            "ON CONFLICT (user_id) DO UPDATE SET " + column + " = EXCLUDED." + column +
                            ", updated_at = CURRENT_TIMESTAMP";
                        try (var insertPs = conn.prepareStatement(insert)) {
                            insertPs.setLong(1, userId);
                            insertPs.setBoolean(2, value);
                            insertPs.executeUpdate();
                        }
                    }
                    updateMemory(userId, key, value);
                    return null;
                }
            }),
            (ExecutionContextExecutor) dbEc
        ).exceptionally(t -> {
            updateMemory(userId, key, value);
            return null;
        });
    }

    private void updateMemory(Long userId, String key, boolean value) {
        Settings settings = memorySettings.computeIfAbsent(userId, Settings::new);
        switch (normalizeKey(key)) {
            case "typingIndicators" -> settings.typingIndicators = value;
            case "showOnlineStatus" -> settings.showOnlineStatus = value;
            case "notifications" -> settings.notifications = value;
            case "soundEnabled" -> settings.soundEnabled = value;
            default -> throw new IllegalArgumentException("Unknown setting: " + key);
        }
    }

    private String columnFor(String key) {
        return switch (normalizeKey(key)) {
            case "typingIndicators" -> "typing_indicators";
            case "showOnlineStatus" -> "show_online_status";
            case "notifications" -> "notifications";
            case "soundEnabled" -> "sound_enabled";
            default -> throw new IllegalArgumentException("Unknown setting: " + key);
        };
    }

    private String normalizeKey(String key) {
        return switch (key) {
            case "typing_indicators", "typingIndicators" -> "typingIndicators";
            case "show_online_status", "showOnlineStatus" -> "showOnlineStatus";
            case "notifications" -> "notifications";
            case "sound_enabled", "soundEnabled" -> "soundEnabled";
            default -> key;
        };
    }
}
