# Day 05 - Request & Response: Đọc Input, Viết Output

## Mục tiêu
- Đọc headers, cookies, body từ request
- Hiểu Session và Flash (và tại sao Play khác PHP/Java EE)
- Viết response với header, cookie, content-type custom

---

## 1. Đọc Request Đầy Đủ

```java
public Result inspect(Http.Request request) {
    // --- URL & Method ---
    String method = request.method();        // "GET", "POST"...
    String uri = request.uri();              // "/search?q=play&page=2"
    String path = request.path();            // "/search"
    boolean secure = request.secure();       // true nếu HTTPS
    String remoteAddr = request.remoteAddress();  // IP client

    // --- Query Parameters ---
    Optional<String> q = request.getQueryString("q");
    Map<String, String[]> allQP = request.queryString();

    // --- Headers ---
    Optional<String> ct = request.header("Content-Type");
    Optional<String> auth = request.header("Authorization");
    Http.Headers headers = request.getHeaders();

    // --- Body (chọn một) ---
    JsonNode json = request.body().asJson();                  // JSON body
    Map<String, String[]> form = request.body().asFormUrlEncoded();  // form
    String text = request.body().asText();                    // plain text
    byte[] bytes = request.body().asBytes().toArray();        // raw bytes

    // --- Cookies ---
    Optional<Http.Cookie> sessionCookie = request.cookie("myapp-session");
    Http.Cookies allCookies = request.cookies();

    // --- Session ---
    Optional<String> userId = request.session().get("userId");
    Map<String, String> sessionData = request.session().data();

    // --- Flash ---
    Optional<String> flashMsg = request.flash().get("success");

    return ok("OK");
}
```

---

## 2. Session: KHÔNG Phải Server-Side Session!

**Điểm hay nhầm nhất của người mới học Play.**

### PHP/Java EE/Spring (Server-side session):
```
Client → gửi session ID (cookie)
Server → tra cứu session store (RAM, Redis, DB)
       → lấy ra {"userId": 123, "role": "admin", "cart": [...]}
```

### Play (Client-side session):
```
Client → gửi TOÀN BỘ session data trong cookie (signed)
       → {"userId": "123", "role": "admin"}
Server → verify signature, đọc data
       → KHÔNG cần tra cứu database/Redis
```

**Hệ quả quan trọng:**
1. Session bị giới hạn **~4KB** (giới hạn cookie)
2. Session **không thể bị invalidate từ server** (không có server-side store)
3. Data được **signed** (không thể tamper) nhưng **KHÔNG encrypt** (client đọc được)
4. **KHÔNG lưu data nhạy cảm** vào session

```java
// Chỉ lưu token/ID, tra DB để lấy thông tin thực
public Result login(Http.Request request) {
    // ✅ ĐÚNG: Chỉ lưu session token
    return redirect("/dashboard")
        .addingToSession(request, "sessionToken", "abc-xyz-generated-token");
    // → Token này map sang DB để lấy userId, role, etc.
}

public Result loginWrong(Http.Request request) {
    // ❌ SAI: Lưu cả password vào session cookie
    return redirect("/dashboard")
        .addingToSession(request, "password", "123456");
    // Client có thể decode Base64 cookie và đọc được!
}
```

### Làm việc với Session

```java
// Thêm vào session
Result result = redirect("/")
    .addingToSession(request, "userId", "42")
    .addingToSession(request, "role", "admin");

// Xóa toàn bộ session (logout)
Result logout = redirect("/login").withNewSession();

// Xóa 1 key khỏi session
Result partial = ok("done")
    .removingFromSession(request, "tempKey");

// Đọc session
Optional<String> userId = request.session().get("userId");
String userIdOrDefault = request.session().get("userId").orElse("anonymous");
```

---

## 3. Flash: Message Chỉ Sống 1 Request

Flash scope dùng cho **redirect-after-POST** pattern:

```
POST /todos/create
  → Tạo todo thành công
  → redirect GET /todos (với flash message)
GET /todos
  → Hiện danh sách + flash "Todo created successfully!"
  → Flash bị xóa ngay sau request này
```

```java
// Set flash khi redirect
public Result createTodo(Http.Request request) {
    // Xử lý tạo todo...
    return redirect(routes.TodoController.list())
        .flashing("success", "Todo created successfully!");
}

// Đọc flash ở request tiếp theo
public Result listTodos(Http.Request request) {
    Optional<String> success = request.flash().get("success");
    Optional<String> error = request.flash().get("error");

    // Dùng trong response (ví dụ JSON API)
    ObjectNode json = Json.newObject();
    json.put("todos", Json.toJson(todoList));
    success.ifPresent(msg -> json.put("message", msg));
    return ok(json);
}
```

---

## 4. Cookies

```java
// Tạo cookie
Http.Cookie myCookie = Http.Cookie.builder("theme", "dark")
    .withMaxAge(Duration.ofDays(30))   // Tồn tại 30 ngày
    .withHttpOnly(true)                 // Không accessible từ JavaScript
    .withSecure(true)                   // Chỉ gửi qua HTTPS
    .withPath("/")                      // Áp dụng toàn site
    .withSameSite(Http.Cookie.SameSite.STRICT)  // CSRF protection
    .build();

// Set cookie trong response
Result response = ok("Saved").withCookies(myCookie);

// Xóa cookie
Result clearCookie = ok("Cleared").discardingCookie("theme");

// Đọc cookie từ request
Optional<Http.Cookie> theme = request.cookie("theme");
String themeValue = theme.map(Http.Cookie::value).orElse("light");
```

---

## 5. Custom Response Headers

```java
public Result customResponse() {
    return ok("data")
        .withHeader("X-Request-Id", UUID.randomUUID().toString())
        .withHeader("Cache-Control", "public, max-age=3600")
        .withHeader("X-RateLimit-Remaining", "99")
        .as("application/json; charset=utf-8");
}

// Response với multiple values cùng header
public Result withMultipleHeaders() {
    return ok("").withHeaders(
        new Tuple2<>("Set-Cookie", "a=1"),
        new Tuple2<>("Set-Cookie", "b=2")
    );
}
```

---

## 6. Content Negotiation

```java
public Result negotiate(Http.Request request) {
    // Client gửi Accept: application/json hoặc text/html
    if (request.accepts("application/json")) {
        return ok(Json.newObject().put("data", "value"));
    } else if (request.accepts("text/html")) {
        return ok("<html><body>value</body></html>").as("text/html");
    } else {
        return status(406, "Not Acceptable");
    }
}
```

---

## 7. Request Attributes (Truyền Data Giữa Actions)

Thay ThreadLocal, Play dùng **Request Attributes** để truyền data qua Action chain:

```java
// Định nghĩa TypedKey (type-safe, không có key collision)
public class RequestKeys {
    public static final TypedKey<String> USER_ID = TypedKey.create("userId");
    public static final TypedKey<User> USER = TypedKey.create("user");
    public static final TypedKey<Long> REQUEST_START = TypedKey.create("requestStart");
}

// Trong Action Composition - thêm attribute
public CompletionStage<Result> call(Http.Request request) {
    Http.Request enriched = request
        .addAttr(RequestKeys.USER_ID, "user-42")
        .addAttr(RequestKeys.REQUEST_START, System.currentTimeMillis());
    return delegate.call(enriched);
}

// Trong Controller - đọc attribute
public Result dashboard(Http.Request request) {
    String userId = request.attrs().get(RequestKeys.USER_ID);
    return ok("User: " + userId);
}
```

**Tại sao không dùng ThreadLocal?**
```
Action chạy trên thread A
  → ThreadLocal A có userId = "42"
  → gọi async operation
    → callback chạy trên thread B
      → ThreadLocal B KHÔNG có userId!

Request Attributes:
  → userId được lưu trong Request object
  → Request object được truyền tường minh vào mọi method
  → Không bị mất khi switch thread
```

---

## 8. Range Requests - Partial Content (RFC 7233)

Play hỗ trợ HTTP Range requests để serve file theo từng phần (video streaming, download resume):

```java
import play.mvc.RangeResults;
import java.io.File;
import java.nio.file.Path;

public class FileController extends Controller {

    // Serve file với range support (206 Partial Content)
    public Result serveVideo(Http.Request request) {
        File videoFile = new File("/var/media/video.mp4");

        // RangeResults tự xử lý Range header → 206 hoặc 200
        return RangeResults.ofFile(request, videoFile);
    }

    // Serve từ Path
    public Result serveLargeFile(Http.Request request) {
        Path filePath = Path.of("/var/files/large.zip");
        return RangeResults.ofPath(request, filePath, Optional.of("large.zip"));
    }
}
```

**Khi client gửi:**
```
GET /video HTTP/1.1
Range: bytes=0-1023
```

**Play trả:**
```
HTTP/1.1 206 Partial Content
Content-Range: bytes 0-1023/5242880
Content-Length: 1024
```

---

## 9. Bài Tập

Xem `request-response-demo/` trong thư mục này:

1. Endpoint `/mirror` - nhận mọi input và trả về JSON mô tả request đó:
   ```json
   {
     "method": "POST",
     "path": "/mirror",
     "headers": {"Content-Type": "application/json"},
     "body": "...",
     "queryParams": {}
   }
   ```

2. Endpoint `/session/set?key=x&value=y` - set session
3. Endpoint `/session/get` - đọc toàn bộ session
4. Endpoint `/session/clear` - xóa session

5. Implement redirect-after-POST với flash:
   - `POST /messages` → tạo message → redirect với flash "Created!"
   - `GET /messages` → hiện danh sách + flash message nếu có

```bash
# Test session
curl -c cookies.txt "http://localhost:9000/session/set?key=name&value=Alice"
curl -b cookies.txt "http://localhost:9000/session/get"
# → {"name": "Alice"}

# Test mirror
curl -X POST "http://localhost:9000/mirror?debug=true" \
  -H "X-My-Header: test" \
  -H "Content-Type: application/json" \
  -d '{"hello":"world"}'
```
