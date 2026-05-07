package controllers;

import models.Todo;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import repositories.AsyncTodoRepository;

import javax.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Day 14 - Todo Controller (Async Version)
 *
 * Thay đổi so với Day 07:
 * - Tất cả method return CompletionStage<Result>
 * - Dùng thenApply/thenCompose thay vì get thẳng giá trị
 * - Thêm exceptionally để handle errors
 */
public class TodoController extends Controller {

    private static final Logger log = LoggerFactory.getLogger(TodoController.class);
    private final AsyncTodoRepository repository;

    @Inject
    public TodoController(AsyncTodoRepository repository) {
        this.repository = repository;
    }

    // GET /todos
    public CompletionStage<Result> list() {
        return repository.findAll()
            .thenApply(todos -> ok(Json.toJson(todos)))
            .exceptionally(t -> {
                log.error("Failed to list todos", t);
                return internalServerError(errorJson("Failed to retrieve todos"));
            });
    }

    // GET /todos/:id
    public CompletionStage<Result> get(Long id) {
        return repository.findById(id)
            .thenApply(opt -> opt
                .map(todo -> ok(Json.toJson(todo)))
                .orElse(notFound(errorJson("Todo not found: " + id)))
            )
            .exceptionally(t -> {
                log.error("Failed to get todo " + id, t);
                return internalServerError(errorJson("Failed to retrieve todo"));
            });
    }

    // POST /todos
    public CompletionStage<Result> create(Http.Request request) {
        JsonNode body = request.body().asJson();

        if (body == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                badRequest(errorJson("Request body must be JSON"))
            );
        }
        if (!body.has("title") || body.get("title").asText().isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                badRequest(errorJson("Field 'title' is required"))
            );
        }

        String title = body.get("title").asText().trim();
        return repository.save(title)
            .thenApply(todo -> created(Json.toJson(todo)))
            .exceptionally(t -> {
                log.error("Failed to create todo", t);
                return internalServerError(errorJson("Failed to create todo"));
            });
    }

    // PUT /todos/:id
    public CompletionStage<Result> update(Long id, Http.Request request) {
        JsonNode body = request.body().asJson();

        if (body == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                badRequest(errorJson("Request body must be JSON"))
            );
        }

        String title = body.has("title") ? body.get("title").asText().trim() : "";
        if (title.isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                badRequest(errorJson("Field 'title' is required"))
            );
        }
        boolean done = body.has("done") && body.get("done").asBoolean();

        return repository.update(id, title, done)
            .thenApply(opt -> opt
                .map(todo -> ok(Json.toJson(todo)))
                .orElse(notFound(errorJson("Todo not found: " + id)))
            )
            .exceptionally(t -> {
                log.error("Failed to update todo " + id, t);
                return internalServerError(errorJson("Failed to update todo"));
            });
    }

    // DELETE /todos/:id
    public CompletionStage<Result> delete(Long id) {
        return repository.delete(id)
            .thenApply(deleted -> deleted
                ? noContent()
                : notFound(errorJson("Todo not found: " + id))
            )
            .exceptionally(t -> {
                log.error("Failed to delete todo " + id, t);
                return internalServerError(errorJson("Failed to delete todo"));
            });
    }

    // GET /todos/stats
    public CompletionStage<Result> stats() {
        return repository.stats()
            .thenApply(s -> ok(Json.toJson(s)))
            .exceptionally(t -> internalServerError(errorJson("Failed to get stats")));
    }

    // GET /health
    public Result health() {
        ObjectNode json = Json.newObject();
        json.put("status", "UP");
        json.put("service", "todo-api-async");
        json.put("timestamp", System.currentTimeMillis());
        return ok(json);
    }

    private ObjectNode errorJson(String message) {
        return Json.newObject().put("error", message);
    }
}
