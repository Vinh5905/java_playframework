package controllers;

import models.Todo;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import repositories.TodoRepository;

import javax.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Day 07 - Todo Controller (Sync version)
 *
 * Tuần 2 sẽ refactor sang async và đo benchmark.
 */
public class TodoController extends Controller {

    private final TodoRepository repository;

    @Inject
    public TodoController(TodoRepository repository) {
        this.repository = repository;
    }

    // GET /todos
    public Result list() {
        List<Todo> todos = repository.findAll();
        return ok(Json.toJson(todos));
    }

    // GET /todos/:id
    public Result get(Long id) {
        return repository.findById(id)
            .map(todo -> ok(Json.toJson(todo)))
            .orElse(notFound(errorJson("Todo not found: " + id)));
    }

    // POST /todos
    // Body: {"title": "Learn Play", "done": false}
    public Result create(Http.Request request) {
        JsonNode body = request.body().asJson();

        if (body == null) {
            return badRequest(errorJson("Request body must be JSON"));
        }
        if (!body.has("title") || body.get("title").asText().isBlank()) {
            return badRequest(errorJson("Field 'title' is required and cannot be empty"));
        }

        String title = body.get("title").asText().trim();
        Todo todo = repository.save(title);
        return created(Json.toJson(todo));
    }

    // PUT /todos/:id
    // Body: {"title": "Updated title", "done": true}
    public Result update(Long id, Http.Request request) {
        JsonNode body = request.body().asJson();

        if (body == null) {
            return badRequest(errorJson("Request body must be JSON"));
        }

        String title = body.has("title") ? body.get("title").asText().trim() : null;
        boolean done = body.has("done") && body.get("done").asBoolean();

        if (title == null || title.isBlank()) {
            return badRequest(errorJson("Field 'title' is required"));
        }

        return repository.update(id, title, done)
            .map(updated -> ok(Json.toJson(updated)))
            .orElse(notFound(errorJson("Todo not found: " + id)));
    }

    // DELETE /todos/:id
    public Result delete(Long id) {
        if (repository.delete(id)) {
            return noContent();  // 204 - standard cho DELETE thành công
        }
        return notFound(errorJson("Todo not found: " + id));
    }

    // GET /todos/stats
    public Result stats() {
        return ok(Json.toJson(repository.stats()));
    }

    // GET /health
    public Result health() {
        ObjectNode json = Json.newObject();
        json.put("status", "UP");
        json.put("service", "todo-api-sync");
        json.put("timestamp", System.currentTimeMillis());
        return ok(json);
    }

    // Helper: tạo JSON error response chuẩn
    private ObjectNode errorJson(String message) {
        ObjectNode error = Json.newObject();
        error.put("error", message);
        return error;
    }
}
