package services;

import models.Settings;
import repositories.SettingsRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class SettingsService {

    private final SettingsRepository settingsRepo;
    private final Map<Long, Settings> cache = new ConcurrentHashMap<>();

    @Inject
    public SettingsService(SettingsRepository settingsRepo) {
        this.settingsRepo = settingsRepo;
    }

    public CompletionStage<Settings> getSettings(Long userId) {
        Settings cached = cache.get(userId);
        if (cached != null) {
            return java.util.concurrent.CompletableFuture.completedFuture(cached);
        }
        return settingsRepo.findByUserId(userId)
            .thenApply(settings -> {
                cache.put(userId, settings);
                return settings;
            });
    }

    public CompletionStage<Void> updateSetting(Long userId, String key, boolean value) {
        return settingsRepo.updateSetting(userId, key, value)
            .thenApply(v -> {
                cache.remove(userId);
                return null;
            });
    }
}
