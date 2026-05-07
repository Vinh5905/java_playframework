package controllers;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import services.UrlShortenerService;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý redirect từ short URL → original URL
 * Public endpoint, không cần authentication.
 */
public class RedirectController extends Controller {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);
    private final UrlShortenerService urlService;

    @Inject
    public RedirectController(UrlShortenerService urlService) {
        this.urlService = urlService;
    }

    // GET /:code → redirect to original URL
    public CompletionStage<Result> redirect(String code) {
        return urlService.resolve(code)
            .thenApply(opt -> {
                if (opt.isEmpty()) {
                    return notFound(Json.newObject().put("error", "Short URL not found: " + code));
                }
                // 301 Permanent redirect
                // Dùng 302 nếu muốn track mỗi click (301 bị browser cache)
                return movedPermanently(opt.get());
            })
            .exceptionally(t -> {
                log.error("Redirect error for code: " + code, t);
                return internalServerError(Json.newObject().put("error", "Redirect failed"));
            });
    }
}
