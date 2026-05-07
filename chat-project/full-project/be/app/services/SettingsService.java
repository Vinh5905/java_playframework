package services;

import models.Settings;
import play.cache.AsyncCacheApi;
import repositories.SettingsRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletionStage;

@Singleton
public class SettingsService {

    private final SettingsRepository settingsRepo;
    private final AsyncCacheApi cache;

    @Inject
    public SettingsService(SettingsRepository settingsRepo, AsyncCacheApi cache) {
        this.settingsRepo = settingsRepo;
        this.cache = cache;
    }

    public CompletionStage<Settings> getSettings(Long userId) {
        return cache.getOrElseUpdate(
            "settings:" + userId,
            () -> settingsRepo.findByUserId(userId),
            300
        );
    }

    public CompletionStage<Void> updateSetting(Long userId, String key, boolean value) {
        return settingsRepo.updateSetting(userId, key, value)
            .thenCompose(v -> cache.remove("settings:" + userId));
    }
}
