package controllers;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import security.JwtAction;
import security.JwtService;
import services.UrlShortenerService;

import javax.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@With(JwtAction.class)  // Tất cả action trong class cần JWT
public class UrlController extends Controller {

    private static final Logger log = LoggerFactory.getLogger(UrlController.class);
    private final UrlShortenerService urlService;

    @Inject
    public UrlController(UrlShortenerService urlService) {
        this.urlService = urlService;
    }

    // GET /api/urls
    public CompletionStage<Result> list(Http.Request request) {
        Long userId = request.attrs().get(JwtService.USER_ID_KEY);
        return urlService.listForUser(userId)
            .thenApply(urls -> ok(Json.toJson(urls)))
            .exceptionally(t -> {
                log.error("List URLs error", t);
                return internalServerError(errorJson("Failed to list URLs"));
            });
    }

    // POST /api/urls
    public CompletionStage<Result> create(Http.Request request) {
        Long userId = request.attrs().get(JwtService.USER_ID_KEY);
        JsonNode body = request.body().asJson();

        if (body == null) {
            return CompletableFuture.completedFuture(badRequest(errorJson("JSON body required")));
        }

        String url = body.path("url").asText("").trim();
        if (url.isEmpty()) {
            return CompletableFuture.completedFuture(badRequest(errorJson("Field 'url' is required")));
        }

        return urlService.shorten(url, userId)
            .thenApply(shortUrl -> created(Json.toJson(shortUrl)))
            .exceptionally(t -> {
                if (t.getCause() instanceof IllegalArgumentException) {
                    return badRequest(errorJson(t.getCause().getMessage()));
                }
                log.error("Create URL error", t);
                return internalServerError(errorJson("Failed to create short URL"));
            });
    }

    // GET /api/urls/:code/stats
    public CompletionStage<Result> stats(String code, Http.Request request) {
        Long userId = request.attrs().get(JwtService.USER_ID_KEY);
        return urlService.getStats(code, userId)
            .thenApply(opt -> opt
                .map(url -> ok(Json.toJson(url)))
                .orElse(notFound(errorJson("URL not found or not owned by you")))
            )
            .exceptionally(t -> {
                log.error("Stats error for code: " + code, t);
                return internalServerError(errorJson("Failed to get stats"));
            });
    }

    // DELETE /api/urls/:code
    public CompletionStage<Result> delete(String code, Http.Request request) {
        Long userId = request.attrs().get(JwtService.USER_ID_KEY);
        return urlService.delete(code, userId)
            .thenApply(deleted -> deleted
                ? noContent()
                : notFound(errorJson("URL not found or not owned by you"))
            )
            .exceptionally(t -> {
                log.error("Delete error for code: " + code, t);
                return internalServerError(errorJson("Failed to delete URL"));
            });
    }

    // GET /health (không cần JWT vì không có @With ở method level)
    // Nhưng class có @With(JwtAction.class) → health cũng cần JWT!
    // Giải pháp: move health sang HomeController hoặc remove class-level @With
    public Result health() {
        ObjectNode json = Json.newObject();
        json.put("status", "UP");
        json.put("service", "url-shortener");
        json.put("timestamp", System.currentTimeMillis());
        return ok(json);
    }

    private ObjectNode errorJson(String message) {
        return Json.newObject().put("error", message);
    }
}
