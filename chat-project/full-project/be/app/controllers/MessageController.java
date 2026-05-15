package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Message;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import repositories.MessageRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class MessageController extends Controller {
    private final MessageRepository messageRepo;

    @Inject
    public MessageController(MessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    public CompletionStage<Result> getMessages(String convId, int limit) {
        return messageRepo.getMessages(convId, Math.min(Math.max(limit, 1), 100))
            .thenApply(messages -> ok(Json.toJson(messages)))
            .exceptionally(t -> internalServerError(Json.newObject().put("error", t.getMessage())));
    }

    public CompletionStage<Result> sendMessage(String convId, Http.Request request) {
        JsonNode body = request.body().asJson();
        if (body == null || !body.hasNonNull("text")) {
            return CompletableFuture.completedFuture(
                badRequest(Json.newObject().put("error", "text field required"))
            );
        }

        String text = body.get("text").asText("").trim();
        if (text.isEmpty()) {
            return CompletableFuture.completedFuture(
                badRequest(Json.newObject().put("error", "text cannot be empty"))
            );
        }

        Long senderId = parseLongOrDefault(request.getQueryString("senderId"), 1L);

        return messageRepo.saveMessage(new Message(convId, senderId, text))
            .thenApply(saved -> created(Json.toJson(saved)))
            .exceptionally(t -> internalServerError(Json.newObject().put("error", t.getMessage())));
    }

    private Long parseLongOrDefault(String value, Long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
