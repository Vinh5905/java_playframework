# Day 03 - Routing: URL → Controller

## Mục tiêu
- Thành thạo tất cả kiểu route (path param, query param, wildcard, regex)
- Hiểu và dùng được reverse routing
- Nắm thứ tự route matching

---

## 1. Các Kiểu Route Parameter

### Path Parameter (`:name`)
Bắt một URL segment (không có `/`):

```
GET     /users/:id          controllers.UserController.show(id: Long)
GET     /posts/:slug/edit   controllers.PostController.edit(slug: String)

# Test:
# GET /users/42        → id=42
# GET /users/abc       → lỗi nếu type là Long (compile check)
# GET /users/42/extra  → không match (có thêm segment)
```

### Wildcard (`*name`)
Bắt nhiều segments (có `/`):

```
GET     /files/*path        controllers.FileController.serve(path: String)
GET     /docs/*page         controllers.DocsController.show(page: String)

# Test:
# GET /files/images/logo.png  → path="images/logo.png"
# GET /files/css/app.css      → path="css/app.css"
```

### Regex Route (`$name<regex>`)
Bắt segment với regex constraint:

```
# Chỉ chấp nhận số nguyên
GET     /posts/$id<[0-9]+>  controllers.PostController.show(id: Long)

# Chỉ chấp nhận slug format (chữ thường, số, gạch ngang)
GET     /articles/$slug<[a-z0-9-]+> controllers.ArticleController.show(slug: String)

# Test:
# GET /posts/42       → match
# GET /posts/abc      → 404 (không match regex)
# GET /posts/42.5     → 404
```

### Query Parameter
Không có trong URL path, lấy từ `?key=value`:

```
# Required query param
GET     /search     controllers.SearchController.search(q: String)

# Optional với default value
GET     /products   controllers.ProductController.list(page: Int ?= 1, size: Int ?= 20)

# Optional không có default (Java Optional<String>)
GET     /filter     controllers.FilterController.apply(category: java.util.Optional[String])

# Test:
# GET /search?q=play              → q="play"
# GET /products                   → page=1, size=20 (defaults)
# GET /products?page=2&size=50    → page=2, size=50
# GET /filter?category=java       → category=Optional["java"]
# GET /filter                     → category=Optional.empty()
```

---

## 2. Fixed Value (Giá Trị Cố Định)

```
# Nhiều route mapping đến cùng method, với value khác nhau
GET     /api/v1/users   controllers.UserController.list(version: String = "v1")
GET     /api/v2/users   controllers.UserController.list(version: String = "v2")

# Hoặc đơn giản: mapping hai URL → cùng method
GET     /home           controllers.HomeController.index()
GET     /               controllers.HomeController.index()
```

---

## 3. Thứ Tự Route Matching

Play match từ **trên xuống dưới**, dừng khi tìm được match đầu tiên.

```
# ❌ SAI - "profile" sẽ match ":id" với id="profile"
GET     /users/:id          controllers.UserController.show(id: Long)
GET     /users/profile      controllers.UserController.profile()

# ✅ ĐÚNG - route cụ thể luôn đặt TRƯỚC route generic
GET     /users/profile      controllers.UserController.profile()
GET     /users/settings     controllers.UserController.settings()
GET     /users/:id          controllers.UserController.show(id: Long)
```

**Lưu ý với regex**: regex route ít "greedy" hơn wildcard nên an toàn hơn.

---

## 4. HTTP Methods

```
GET     /todos              controllers.TodoController.list()
POST    /todos              controllers.TodoController.create(request: Request)
GET     /todos/:id          controllers.TodoController.get(id: Long)
PUT     /todos/:id          controllers.TodoController.update(id: Long, request: Request)
PATCH   /todos/:id          controllers.TodoController.patch(id: Long, request: Request)
DELETE  /todos/:id          controllers.TodoController.delete(id: Long)
HEAD    /todos              controllers.TodoController.list()   # như GET nhưng không có body
OPTIONS /todos              controllers.TodoController.options()
```

---

## 5. Route Groups với Sub-router (Play 3.x)

Dùng `->` để include router từ file khác:

```
# conf/routes - main router
GET     /                       controllers.HomeController.index()

# Include API router
->      /api                    api.Routes

# conf/api.routes
GET     /users                  controllers.api.UserController.list()
GET     /users/:id              controllers.api.UserController.show(id: Long)
```

---

## 6. Reverse Routing - Type-Safe URL Generation

Reverse routing là một trong những feature hay nhất của Play.

```java
// Trong Controller:
public Result doSomething() {
    // Generate URL từ controller method
    play.mvc.Call redirect = controllers.routes.UserController.show(42L);

    System.out.println(redirect.url());    // "/users/42"
    System.out.println(redirect.method()); // "GET"

    return redirect(redirect);  // HTTP 303 redirect
}
```

**Trong Twirl template:**
```html
@* views/users/list.scala.html *@
<a href="@routes.UserController.show(user.id)">View User</a>
<form action="@routes.UserController.create()" method="POST">
```

**Tại sao reverse routing quan trọng:**
```
Scenario: Đổi route từ /users/:id → /members/:id

String hardcode:           Cần tìm và sửa tất cả chỗ "/users/"
Spring @RequestMapping:    Cần tìm và sửa các @PathVariable
Play reverse routing:      Compile error ngay! Sửa 1 chỗ trong routes là xong.
```

---

## 7. Request trong Controller

```java
// Cách lấy request trong action
public Result show(Http.Request request, Long id) {
    // Headers
    Optional<String> contentType = request.header("Content-Type");
    Map<String, String[]> headers = request.getHeaders().toMap();

    // Query params
    Optional<String> q = request.getQueryString("q");
    Map<String, String[]> allParams = request.queryString();

    // Remote address
    String ip = request.remoteAddress();

    // Method, URI, path
    String method = request.method();  // "GET"
    String uri = request.uri();        // "/search?q=play"
    String path = request.path();      // "/search"

    // Secure?
    boolean https = request.secure();

    return ok("ID: " + id + ", Query: " + q.orElse("none"));
}
```

> **Lưu ý**: Trong Play, `Http.Request` được truyền **tường minh** vào method (không inject qua ThreadLocal như Spring). Đây là thiết kế có chủ đích - tránh vấn đề ThreadLocal trong môi trường async.

---

## 8. List Parameters (Nhiều Giá Trị Cùng Key)

```
# Nhận nhiều giá trị cho cùng một query param
# GET /filter?tags=java&tags=play&tags=async
GET     /filter     controllers.FilterController.apply(tags: java.util.List[String])
```

```java
public Result apply(List<String> tags) {
    // tags = ["java", "play", "async"]
    return ok("Tags: " + tags);
}
```

---

## 9. Default Controller - Placeholder Routes

Play có `Default` controller built-in để xử lý các trường hợp đặc biệt:

```
# Redirect
GET     /old-api    controllers.Default.redirect(to = "/api/v2")

# 404 tường minh
GET     /deprecated controllers.Default.notFound()

# 500 tường minh
GET     /crash      controllers.Default.error()

# 200 trống
GET     /ping       controllers.Default.ok()
```

---

## 10. Relative Routes (CDN & Proxy)

```java
// Tạo URL relative so với request hiện tại
// Hữu ích khi app chạy sau CDN hoặc reverse proxy

Call absoluteCall = controllers.routes.UserController.show(42L);
Call relativeCall = absoluteCall.relativeTo(request);

// Nếu request đến từ https://cdn.example.com/api/users
// → relativeCall.url() = "../users/42" (relative path)
```

---

## 11. nocsrf Modifier - Tắt CSRF Cho Route Cụ Thể

```
# REST API endpoints dùng JWT không cần CSRF
+ nocsrf
POST    /api/webhooks    controllers.WebhookController.receive()

# Bình thường (CSRF check)
POST    /forms/submit   controllers.FormController.submit()
```

> Xem thêm CSRF trong Day 31.

---

## 12. Bài Tập

Tạo project mới hoặc dùng `hello-play` từ Day 01, thêm:

1. Route `/users/:id` → trả JSON `{"id": 42, "type": "user"}`
2. Route `/search?q=xxx&limit=10` → trả JSON `{"query": "xxx", "limit": 10}`
3. Route `/files/*path` → trả `"Serving file: path/to/file.txt"`
4. Route `/posts/$id<[0-9]+>` → chỉ chấp nhận số, 404 với text
5. Tạo redirect: `GET /old-url` → redirect đến `/new-url` dùng reverse routing

**Test:**
```bash
curl "http://localhost:9000/users/42"
curl "http://localhost:9000/search?q=play&limit=5"
curl "http://localhost:9000/files/images/logo.png"
curl "http://localhost:9000/posts/123"
curl "http://localhost:9000/posts/abc"  # → 404
curl -v "http://localhost:9000/old-url"  # → 303 redirect
```

## 13. File Code Thực Hành

Xem `routing-demo/` trong thư mục này - project Play đầy đủ với tất cả ví dụ trên.
