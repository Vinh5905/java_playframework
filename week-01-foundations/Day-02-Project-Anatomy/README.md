# Day 02 - Giải Phẫu Project Play

## Mục tiêu hôm nay
- Hiểu bản chất từng file trong project (không học vẹt)
- Nắm MVC flow từ HTTP request → response
- Đọc được `build.sbt`, `application.conf`, `routes`

---

## 1. File `build.sbt` - Trái Tim của Project

```scala
// Tên project (dùng cho JAR artifact name)
name := """todo-api"""
organization := "com.example"
version := "1.0-SNAPSHOT"

// enablePlugins(PlayJava) = "đây là Play project dùng Java"
// PlayMinimalJava = không có Twirl templates (dùng khi làm pure REST API)
lazy val root = (project in file(".")).enablePlugins(PlayJava)

// Play 3.x dùng Scala 2.13 (kể cả khi bạn viết Java)
// Scala là implementation language của Play, không phải của bạn
scalaVersion := "2.13.16"

libraryDependencies ++= Seq(
  guice,           // Google Guice - Dependency Injection framework
  jdbc,            // Thêm khi cần database
  evolutions,      // Thêm khi cần database migration
  ws,              // Thêm khi cần gọi HTTP API ngoài

  // Test
  "org.junit.jupiter" % "junit-jupiter-api" % "5.10.2" % Test,
  "org.junit.jupiter" % "junit-jupiter-engine" % "5.10.2" % Test,
  "org.mockito" % "mockito-core" % "5.11.0" % Test
)
```

**Cách đọc dependency notation:**
```
"group-id" % "artifact-id" % "version" % scope
    ↑              ↑              ↑         ↑
  org/author    tên thư viện   phiên bản  compile/test/runtime
```

`%%` thay vì `%` = tự động append Scala version vào artifact-id (dùng cho Scala library).

---

## 2. File `conf/application.conf` - Cấu Hình HOCON

Play dùng **HOCON** (Human-Optimized Config Object Notation) - superset của JSON.

```hocon
# Dấu # = comment (JSON không có comment)

# Secret key - bắt buộc phải đổi trong production!
# Dùng để sign session cookie, CSRF token
play.http.secret.key = "changeme"

# Có thể đọc từ environment variable
# Nếu MY_SECRET không tồn tại → dùng fallback "changeme"
play.http.secret.key = ${?MY_SECRET}

# Database config (khi có JDBC)
db {
  default {
    driver = "org.postgresql.Driver"
    url = "jdbc:postgresql://localhost:5432/mydb"
    username = "postgres"
    password = ${?DB_PASSWORD}  # từ env var
  }
}

# HOCON cho phép object notation ngắn gọn
# Dòng này tương đương:
# play { filters { hosts { allowed = [...] } } }
play.filters.hosts.allowed = ["localhost:9000", "127.0.0.1:9000"]

# Thêm/bỏ filter
play.filters.enabled += "filters.MyLoggingFilter"
play.filters.disabled += "play.filters.csrf.CSRFFilter"

# Custom config của bạn
myapp {
  maxRetries = 3
  apiUrl = "https://api.example.com"
  apiUrl = ${?EXTERNAL_API_URL}  # override bằng env var nếu có
}
```

**Đọc config trong code Java:**
```java
@Singleton
public class MyService {
    private final int maxRetries;
    private final String apiUrl;

    @Inject
    public MyService(Config config) {
        this.maxRetries = config.getInt("myapp.maxRetries");
        this.apiUrl = config.getString("myapp.apiUrl");
    }
}
```

---

## 3. File `conf/routes` - URL Routing

```
# Format: METHOD   URL_PATTERN   FULLY_QUALIFIED_CONTROLLER.METHOD

# GET đơn giản
GET     /               controllers.HomeController.index()

# Path parameter - :name bắt một segment (không có /)
GET     /users/:id      controllers.UserController.show(id: Long)

# Wildcard - *path bắt nhiều segment (có /)
GET     /files/*path    controllers.FileController.serve(path: String)

# Query parameter - không có trong URL, nhưng khai báo trong method
# Ví dụ: GET /search?q=play&limit=10
GET     /search         controllers.SearchController.find(q: String, limit: Int ?= 10)

# HTTP methods khác
POST    /users          controllers.UserController.create(request: Request)
PUT     /users/:id      controllers.UserController.update(id: Long, request: Request)
DELETE  /users/:id      controllers.UserController.delete(id: Long)
PATCH   /users/:id      controllers.UserController.patch(id: Long, request: Request)
```

**Play compile file routes thành Scala class** - nếu viết sai (controller không tồn tại, sai type) sẽ lỗi **compile time**, không phải runtime. Đây là "type-safe routing".

**Thứ tự routes quan trọng:**
```
# ❌ SAI: route generic trước route cụ thể
GET     /users/:id          controllers.UserController.show(id: Long)
GET     /users/profile      controllers.UserController.profile()
# → /users/profile sẽ match route đầu với id="profile" → lỗi parse Long!

# ✅ ĐÚNG: route cụ thể trước
GET     /users/profile      controllers.UserController.profile()
GET     /users/:id          controllers.UserController.show(id: Long)
```

---

## 4. MVC Flow Chi Tiết

```
HTTP Request: GET /users/42
    │
    ▼
┌─────────────────────────────────┐
│   Pekko HTTP Server             │  ← Nhận TCP connection
│   (port 9000)                   │
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│   Filters (Pipeline)            │  ← Chạy TRƯỚC khi vào controller
│   - AllowedHostsFilter          │    • Kiểm tra Host header
│   - SecurityHeadersFilter       │    • Thêm X-Frame-Options, etc.
│   - CSRFFilter                  │    • Kiểm tra CSRF token
│   - CORSFilter                  │    • CORS headers
│   - GzipFilter (nếu bật)        │    • Nén response
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│   Router                        │  ← Đọc conf/routes
│   "GET /users/:id"              │    Match → UserController.show(id=42)
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│   Action Composition            │  ← Action wrappers (auth, logging...)
│   (nếu có @With(Auth.class))    │
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│   Controller.show(42)           │  ← Code của bạn
│   → gọi service                 │
│   → return ok(json)             │
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│   Filters (reverse pipeline)    │  ← Chạy SAU khi controller trả về
│   - GzipFilter                  │    • Nén body
└─────────────────────────────────┘
    │
    ▼
HTTP Response: 200 OK, Content-Type: application/json
```

---

## 5. Vì Sao Play Không Dùng Annotation Routing?

Spring Boot:
```java
@RestController
public class UserController {
    @GetMapping("/users/{id}")
    public User show(@PathVariable Long id) { ... }

    @PostMapping("/users")
    public User create(@RequestBody UserDto dto) { ... }
}
```

Để xem **tất cả API** của Spring app, bạn phải tìm kiếm `@GetMapping`, `@PostMapping`... rải rác trong nhiều file.

Play:
```
# Một file conf/routes - nhìn 1 cái là biết toàn bộ API
GET     /users/:id      controllers.UserController.show(id: Long)
POST    /users          controllers.UserController.create(request: Request)
```

**Lợi ích**:
1. API documentation ngay trong code
2. Tìm được ngay controller nào xử lý URL nào
3. Compiler kiểm tra tính hợp lệ của routes
4. Reverse routing: generate URL type-safe

---

## 6. Reverse Routing

```java
// Thay vì hardcode URL
return redirect("/users/" + id);  // ❌ Dễ lỗi nếu đổi URL

// Dùng reverse routing - Play tự generate từ routes file
return redirect(controllers.routes.UserController.show(id));  // ✅

// Generate URL string
String url = controllers.routes.UserController.show(42L).url();
// → "/users/42"

// Khi bạn đổi route:
// GET     /user/:id   → GET   /members/:id
// Code reverse routing tự cập nhật, compile error nếu quên
```

---

## 7. Bài Tập

1. Mở project `hello-play` từ Day 01
2. Thêm route `GET /info` → controller trả về JSON với thông tin: `{"framework": "Play", "version": "3.0", "language": "Java"}`
3. Dùng reverse routing trong `HomeController.index()` để log URL của route `/info`
4. Thêm route `GET /echo/:message` → trả về message đó

**Kiểm tra:**
```bash
curl http://localhost:9000/info
# → {"framework":"Play","version":"3.0","language":"Java"}

curl http://localhost:9000/echo/hello-world
# → hello-world
```
