# Day 32 - Error Handling: Global & Per-Controller

## Mục tiêu
- Custom global error handler (404, 500, v.v.)
- Per-controller error handling với exceptionally
- Structured error responses

---

## 1. Global Error Handler

```java
// app/ErrorHandler.java
import play.http.DefaultHttpErrorHandler;
import play.mvc.*;
import play.libs.Json;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class ErrorHandler extends DefaultHttpErrorHandler {

    @Inject
    public ErrorHandler(play.api.OptionalSourceMapper sourceMapper,
                       com.typesafe.config.Config config,
                       play.api.routing.Router router) {
        super(config, sourceMapper, () -> router);
    }

    // 404 - Route không tồn tại
    @Override
    protected CompletionStage<Result> onNotFound(Http.RequestHeader request, String message) {
        return CompletableFuture.completedFuture(
            Results.notFound(Json.newObject()
                .put("error", "Not Found")
                .put("path", request.path())
            )
        );
    }

    // 500 - Unhandled server error trong production
    @Override
    protected CompletionStage<Result> onProdServerError(
            Http.RequestHeader request, play.api.http.HttpErrorInfo error) {
        // LOG nhưng không expose stack trace cho client
        return CompletableFuture.completedFuture(
            Results.internalServerError(Json.newObject()
                .put("error", "Internal Server Error")
                .put("requestId", request.id())
            )
        );
    }

    // 400 - Bad request (routing errors)
    @Override
    protected CompletionStage<Result> onBadRequest(Http.RequestHeader request, String message) {
        return CompletableFuture.completedFuture(
            Results.badRequest(Json.newObject()
                .put("error", "Bad Request")
                .put("message", message)
            )
        );
    }
}
```

```hocon
# application.conf
play.http.errorHandler = "ErrorHandler"
```

---

## 2. Custom Exceptions

```java
// app/exceptions/AppExceptions.java
public class AppExceptions {

    public static class NotFoundException extends RuntimeException {
        public final String resourceType;
        public final Object resourceId;

        public NotFoundException(String type, Object id) {
            super(type + " not found: " + id);
            this.resourceType = type;
            this.resourceId = id;
        }
    }

    public static class ValidationException extends RuntimeException {
        public final Map<String, List<String>> errors;

        public ValidationException(Map<String, List<String>> errors) {
            super("Validation failed");
            this.errors = errors;
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
```

---

## 3. Per-Controller Error Handling

```java
public CompletionStage<Result> create(Http.Request request) {
    return todoService.create(request.body().asJson())
        .thenApply(todo -> created(Json.toJson(todo)))
        .exceptionally(throwable -> {
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

            if (cause instanceof ValidationException) {
                ValidationException ve = (ValidationException) cause;
                ObjectNode response = Json.newObject();
                response.put("error", "Validation Error");
                response.putPOJO("fields", ve.errors);
                return badRequest(response);
            }

            if (cause instanceof NotFoundException) {
                NotFoundException nfe = (NotFoundException) cause;
                return notFound(Json.newObject()
                    .put("error", nfe.resourceType + " not found")
                );
            }

            if (cause instanceof ConflictException) {
                return status(409, Json.newObject().put("error", cause.getMessage()));
            }

            log.error("Unexpected error in create", cause);
            return internalServerError(Json.newObject().put("error", "Internal server error"));
        });
}
```

---

## 4. Error Response Format Chuẩn

```json
// 400 Validation Error
{
  "error": "Validation Error",
  "fields": {
    "title": ["Title is required", "Max 255 characters"],
    "email": ["Invalid email format"]
  }
}

// 404 Not Found
{
  "error": "Todo not found",
  "id": 999
}

// 409 Conflict
{
  "error": "Email already registered"
}

// 500 Server Error (production - không expose detail)
{
  "error": "Internal Server Error",
  "requestId": "abc123"  // Dùng để trace trong logs
}
```

---

## 5. Built-in Error Handlers (Không Cần Custom)

Play có sẵn các error handler cho nhiều use case:

```hocon
# application.conf

# Option 1: JSON error handler (REST API thuần)
play.http.errorHandler = "play.http.JsonHttpErrorHandler"
# → Tất cả lỗi (404, 500, etc.) đều trả JSON

# Option 2: Smart handler - HTML cho browser, JSON cho API client
play.http.errorHandler = "play.http.HtmlOrJsonHttpErrorHandler"
# → Nếu Accept: text/html → HTML error page
# → Nếu Accept: application/json → JSON error

# Option 3: Custom handler (xem Section 1 ở trên)
play.http.errorHandler = "ErrorHandler"
```

**Khi nào dùng built-in vs custom:**
| Trường hợp | Giải pháp |
|-----------|-----------|
| REST API thuần | `JsonHttpErrorHandler` |
| Hybrid app (HTML + API) | `HtmlOrJsonHttpErrorHandler` |
| Custom error format / logging | Extend `DefaultHttpErrorHandler` |

---

## 6. Bài Tập

1. Setup global error handler trả JSON (không HTML)
2. Test: curl URL không tồn tại → JSON 404
3. Test: curl với invalid JSON → JSON 400
4. Thêm `X-Request-Id` header vào mọi error response
5. Log error với request ID để dễ trace
