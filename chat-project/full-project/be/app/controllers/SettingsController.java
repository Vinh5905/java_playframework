package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.SettingsService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class SettingsController extends Controller {
    private final SettingsService settingsService;

    @Inject
    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public CompletionStage<Result> getSettings(Long userId) {
        return settingsService.getSettings(userId)
            .thenApply(settings -> ok(Json.toJson(settings)))
            .exceptionally(t -> internalServerError(Json.newObject().put("error", t.getMessage())));
    }

    public CompletionStage<Result> updateSetting(Long userId, Http.Request request) {
        JsonNode body = request.body().asJson();
        if (body == null || body.isEmpty()) {
            return CompletableFuture.completedFuture(
                badRequest(Json.newObject().put("error", "JSON body required"))
            );
        }

        String key;
        boolean value;
        if (body.hasNonNull("key") && body.has("value")) {
            key = body.get("key").asText();
            value = body.get("value").asBoolean();
        } else {
            Iterator<Map.Entry<String, JsonNode>> fields = body.fields();
            if (!fields.hasNext()) {
                return CompletableFuture.completedFuture(
                    badRequest(Json.newObject().put("error", "setting key required"))
                );
            }
            Map.Entry<String, JsonNode> entry = fields.next();
            key = entry.getKey();
            value = entry.getValue().asBoolean();
        }

        return settingsService.updateSetting(userId, key, value)
            .thenApply(v -> ok(Json.newObject()
                .put("success", true)
                .put("key", key)
                .put("value", value)))
            .exceptionally(t -> badRequest(Json.newObject().put("error", t.getMessage())));
    }
}
