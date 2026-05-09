# Day 04 - Controllers & Actions: Trái Tim của Play

## Mục tiêu
- Hiểu bản chất Action là gì (first-class function)
- Thành thạo các loại Result
- Implement Action Composition (middleware pattern của Play)

---

## 1. Action Là Gì? (Khái Niệm Cốt Lõi)

Trong Spring Boot, controller method là một `@Bean` được gọi thông qua reflection và AOP proxy.

Trong Play, **Action là một function thuần túy**:

```
Action = Http.Request → Result
       (hoặc)
Action = Http.Request → CompletionStage<Result>  (async version)
```

Đây không chỉ là lý thuyết - nó có hệ quả thực tế:

```java
// Action là first-class citizen, có thể:
// 1. Truyền vào method khác
// 2. Lưu vào biến
// 3. Kết hợp (compose) với action khác
// 4. Test trực tiếp không cần mock framework

// Test đơn giản - chỉ gọi method với fake request
@Test
public void testShow() {
    Http.Request fakeReq = Helpers.fakeRequest("GET", "/users/42").build();
    Result result = controller.show(fakeReq, 42L);
    assertEquals(200, result.status());
}
```

---

## 2. Controller Cơ Bản

```java
package controllers;

import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.libs.Json;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class UserController extends Controller {

    // Sync action - đơn giản nhất
    public Result index() {
        return ok("User list");
    }

    // Nhận path parameter
    public Result show(Long id) {
        ObjectNode json = Json.newObject();
        json.put("id", id);
        json.put("name", "Alice");
        return ok(json);
        // Content-Type tự động là application/json khi trả JsonNode
    }

    // Nhận request body
    public Result create(Http.Request request) {
        // Lấy body dạng JSON
        com.fasterxml.jackson.databind.JsonNode body = request.body().asJson();

        if (body == null) {
            return badRequest(Json.newObject().put("error", "Expected JSON body"));
        }

        String name = body.get("name").asText();
        // Xử lý...
        ObjectNode response = Json.newObject();
        response.put("id", 1);
        response.put("name", name);
        return created(response);  // 201 Created
    }
}
```

---

## 3. Tất Cả Loại Result

```java
// 2xx Success
ok("body")                      // 200 OK
ok(jsonNode)                    // 200 OK, Content-Type: application/json
created("body")                 // 201 Created
noContent()                     // 204 No Content (không có body)

// 3xx Redirect
redirect("/login")              // 303 See Other
movedPermanently("/new-url")    // 301 Moved Permanently

// 4xx Client Errors
badRequest("Invalid input")     // 400 Bad Request
unauthorized("Login required")  // 401 Unauthorized
forbidden("Not allowed")        // 403 Forbidden
notFound("Not found")           // 404 Not Found
methodNotAllowed()              // 405 Method Not Allowed

// 5xx Server Errors
internalServerError("Error")    // 500 Internal Server Error

// Custom status
status(418, "I'm a teapot")     // Bất kỳ status code nào

// Thêm headers vào response
ok("data").withHeader("X-Custom-Header", "value")
           .withHeader("Cache-Control", "max-age=3600")

// Thêm cookie
ok("data").withCookies(
    Http.Cookie.builder("session", "abc123")
        .withMaxAge(Duration.ofHours(24))
        .withHttpOnly(true)
        .withSecure(true)
        .build()
)

// Thay đổi Content-Type
ok("plain text").as("text/plain; charset=UTF-8")
ok("<h1>HTML</h1>").as("text/html")
ok(bytes).as("application/octet-stream")
```

---

## 4. Request Body Parsers

Play có sẵn các body parser:

```java
public Result handleBody(Http.Request request) {
    // 1. JSON body
    JsonNode json = request.body().asJson();

    // 2. Form data (application/x-www-form-urlencoded)
    Map<String, String[]> form = request.body().asFormUrlEncoded();

    // 3. Raw bytes
    byte[] bytes = request.body().asBytes().toArray();

    // 4. Text
    String text = request.body().asText();

    // 5. Multipart form data (file upload)
    Http.MultipartFormData<Files.TemporaryFile> multipart =
        request.body().asMultipartFormData();
}
```

**Giới hạn body size mặc định: 100KB**. Thay đổi trong `application.conf`:
```hocon
play.http.parser.maxMemoryBuffer = 10MB
play.http.parser.maxDiskBuffer = 100MB
```

---

## 5. Action Composition - Middleware của Play

Action Composition là cách Play implement middleware/interceptor. Khác Spring AOP (proxy-based), Action Composition là **function composition** thuần túy.

### Ví dụ: Authentication Action

```java
// app/security/AuthenticatedAction.java
package security;

import play.mvc.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

public class AuthenticatedAction extends Action.Simple {

    @Override
    public CompletionStage<Result> call(Http.Request request) {
        // Lấy Authorization header
        return request.header("Authorization")
            .filter(token -> token.startsWith("Bearer "))
            .map(token -> {
                String jwt = token.substring(7);
                // Verify JWT (giả lập)
                if (isValidToken(jwt)) {
                    // Truyền userId xuống controller qua attribute
                    String userId = extractUserId(jwt);
                    Http.Request enrichedRequest = request.addAttr(
                        Security.USER_ID_KEY, userId
                    );
                    return delegate.call(enrichedRequest);  // Tiếp tục xuống action tiếp theo
                } else {
                    return CompletableFuture.completedFuture(
                        Results.unauthorized(play.libs.Json.newObject()
                            .put("error", "Invalid token"))
                    );
                }
            })
            .orElseGet(() -> CompletableFuture.completedFuture(
                Results.unauthorized(play.libs.Json.newObject()
                    .put("error", "Missing Authorization header"))
            ));
    }

    private boolean isValidToken(String jwt) { return jwt.length() > 10; }
    private String extractUserId(String jwt) { return "user-123"; }
}
```

```java
// app/security/Security.java - TypedKey để lưu data an toàn
package security;

import play.libs.typedmap.TypedKey;

public class Security {
    public static final TypedKey<String> USER_ID_KEY = TypedKey.create("userId");
}
```

```java
// app/controllers/SecureController.java
package controllers;

import play.mvc.*;
import security.AuthenticatedAction;
import security.Security;

public class SecureController extends Controller {

    // Áp action composition cho 1 method
    @With(AuthenticatedAction.class)
    public Result dashboard(Http.Request request) {
        String userId = request.attrs().get(Security.USER_ID_KEY);
        return ok("Welcome, user: " + userId);
    }
}
```

### Áp cho toàn bộ Controller

```java
// Áp cho tất cả method trong controller
@With(AuthenticatedAction.class)
public class AdminController extends Controller {

    public Result listUsers() {
        // Tất cả action trong class đều cần auth
        return ok("Admin panel");
    }

    public Result deleteUser(Long id) {
        return noContent();
    }
}
```

### Chain nhiều Action

```java
// Áp theo thứ tự: Auth → RateLimit → Logging → Controller
@With({AuthenticatedAction.class, RateLimitAction.class, LoggingAction.class})
public Result sensitiveOperation() {
    return ok("done");
}
```

---

## 6. Custom Action Annotation

Thay `@With(AuthenticatedAction.class)` bằng annotation đẹp hơn:

```java
// app/security/Authenticated.java - Custom annotation
package security;

import play.mvc.With;
import java.lang.annotation.*;

@With(AuthenticatedAction.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Authenticated {
}
```

```java
// Dùng annotation gọn hơn
@Authenticated  // thay vì @With(AuthenticatedAction.class)
public Result dashboard(Http.Request request) {
    return ok("dashboard");
}
```

---

## 7. Logging Action (Ví Dụ Thực Tế)

```java
// app/filters/LoggingAction.java
package actions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.*;
import java.util.concurrent.CompletionStage;

public class LoggingAction extends Action.Simple {
    private static final Logger log = LoggerFactory.getLogger(LoggingAction.class);

    @Override
    public CompletionStage<Result> call(Http.Request request) {
        long start = System.currentTimeMillis();
        log.info("→ {} {}", request.method(), request.uri());

        return delegate.call(request)
            .thenApply(result -> {
                long duration = System.currentTimeMillis() - start;
                log.info("← {} {} {}ms", request.method(), request.uri(), duration);
                return result;
            });
    }
}
```

---

## 8. So Sánh Với Spring

| Feature | Spring | Play |
|---------|--------|------|
| Middleware | `@Aspect` + AOP proxy | Action Composition (function) |
| Pre/Post processing | `HandlerInterceptor` | Action.call() với delegate |
| Global filter | `Filter` (Servlet) | EssentialFilter |
| Per-method | `@Around` pointcut | `@With(MyAction.class)` |
| Testing | Cần mock proxy | Gọi trực tiếp với fake request |

---

## 9. Lỗi Nghiêm Trọng: Action Phải Là Instance Mới Mỗi Request

> **"Every request must be served by a distinct instance of your `play.mvc.Action`."**

```java
// ❌ SAI - Singleton Action: tất cả request dùng chung instance
@Singleton
public class AuthenticatedAction extends Action.Simple {
    // State được share giữa các requests → race conditions!
    private String currentUserId;  // NGUY HIỂM

    @Override
    public CompletionStage<Result> call(Http.Request request) {
        currentUserId = extractUserId(request);  // Thread unsafe!
        return delegate.call(request);
    }
}

// ✅ ĐÚNG - Không có @Singleton: mỗi request = 1 instance mới
public class AuthenticatedAction extends Action.Simple {
    @Override
    public CompletionStage<Result> call(Http.Request request) {
        // Dùng biến local hoặc request.addAttr() để truyền data
        String userId = extractUserId(request);
        Http.Request enriched = request.addAttr(Security.USER_ID_KEY, userId);
        return delegate.call(enriched);
    }
}
```

**Quy tắc**: Action class **KHÔNG BAO GIỜ** có `@Singleton`. Nếu cần dependency, inject qua constructor - Guice tạo instance mới mỗi lần.

---

## 10. Deferred Body Parsing (Parse Sau Auth)

Mặc định, Play parse request body TRƯỚC khi chạy action composition. Điều này gây lãng phí nếu request bị từ chối ở auth layer.

```java
// Cấu hình body parse SAU action composition
// application.conf:
// play.http.actionComposition.executeActionCreatorActionFirst = false

// Trong Action: chỉ parse body sau khi đã auth thành công
public class AuthAction extends Action<Auth> {
    @Override
    public CompletionStage<Result> call(Http.Request request) {
        if (!isAuthenticated(request)) {
            return CompletableFuture.completedFuture(Results.unauthorized());
            // Body không được parse → tiết kiệm tài nguyên
        }
        return delegate.call(request);  // Parse body ở đây
    }
}
```

---

## 11. Controller-Level vs Method-Level Annotation Thứ Tự

```java
// Khi cả class và method đều có @With
// Mặc định: method annotation chạy TRƯỚC class annotation
@With(LoggingAction.class)  // Class level
public class MyController extends Controller {

    @With(AuthAction.class)  // Method level
    public Result secure() {
        // Thứ tự mặc định: AuthAction → LoggingAction
    }
}

// Đổi thứ tự trong application.conf:
// play.http.actionComposition.controllerAnnotationsFirst = true
// → LoggingAction → AuthAction
```

---

## 12. Bài Tập

Xem code thực hành trong `controllers-actions-demo/`:

1. Controller với đầy đủ CRUD (list, show, create, update, delete)
2. `AuthenticatedAction` - check Bearer token giả
3. `TimingAction` - log thời gian xử lý request
4. Chain hai action: timing + auth

```bash
cd controllers-actions-demo
sbt run

# Test không có auth
curl http://localhost:9000/secure/dashboard
# → 401 Unauthorized

# Test với fake token
curl -H "Authorization: Bearer validtoken123" http://localhost:9000/secure/dashboard
# → 200 Welcome, user: user-123

# Test timing header
curl -v http://localhost:9000/public/hello
# → Header X-Response-Time: 5ms
```
