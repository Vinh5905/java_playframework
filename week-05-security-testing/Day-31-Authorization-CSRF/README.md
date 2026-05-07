# Day 31 - Authorization & CSRF Protection

## Mục tiêu
- Role-based access control (RBAC)
- CSRF protection trong Play
- Khi nào cần/không cần CSRF

---

## 1. Role-Based Authorization

```java
// app/security/Authorized.java - Custom annotation
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@play.mvc.With(AuthorizationAction.class)
public @interface Authorized {
    String[] roles() default {};
}

// app/security/AuthorizationAction.java
public class AuthorizationAction extends Action<Authorized> {
    @Override
    public CompletionStage<Result> call(Http.Request request) {
        String userRole = request.attrs()
            .getOptional(JwtService.USER_ROLE_KEY)
            .orElse("");

        String[] required = configuration.roles();
        boolean hasRole = required.length == 0 ||
            Arrays.stream(required).anyMatch(r -> r.equals(userRole));

        if (!hasRole) {
            return CompletableFuture.completedFuture(
                Results.forbidden(
                    Json.newObject().put("error", "Insufficient permissions. Required: " + Arrays.toString(required))
                )
            );
        }

        return delegate.call(request);
    }
}

// Dùng trong controller
@With(JwtAction.class)         // Authenticate first
@Authorized(roles = {"ADMIN"}) // Then authorize
public Result adminEndpoint(Http.Request request) {
    return ok("Admin only");
}

@With(JwtAction.class)
@Authorized  // Any authenticated user
public Result userEndpoint(Http.Request request) {
    return ok("Any logged in user");
}
```

---

## 2. CSRF: Khi Nào Cần?

**CSRF (Cross-Site Request Forgery)**: Attacker dụ user click link → browser tự gửi request với cookie của user.

**Cần CSRF khi:**
- App dùng session cookie (browser tự gửi cookie)
- State-changing operations (POST, PUT, DELETE)
- Server-rendered HTML forms

**KHÔNG cần CSRF khi:**
- API dùng JWT/Bearer token trong Authorization header
  - Browser không tự gửi Authorization header
  - Chỉ JavaScript mới set header này
  - CORS đã ngăn cross-origin requests từ unauthorized domains

```hocon
# Cho REST API dùng JWT → tắt CSRF
play.filters.disabled += "play.filters.csrf.CSRFFilter"

# Cho server-rendered app với session cookies → bật CSRF (mặc định)
```

---

## 3. CSRF Trong Play (Server-Rendered Apps)

```html
@* Template: Form cần CSRF token *@
@helper.form(routes.TodoController.create()) {
    @CSRF.formField  ← Tự động inject hidden input với CSRF token
    <input type="text" name="title">
    <button type="submit">Create</button>
}
```

```java
// Test với curl (bypass CSRF trong dev)
curl -X POST http://localhost:9000/todos \
  -H "Csrf-Token: nocheck" \
  -H "Content-Type: application/json" \
  -d '{"title": "test"}'
```

---

## 4. CORS: Chặn Unauthorized Cross-Origin

```hocon
play.filters.cors {
  # Chỉ cho phép frontend của bạn
  allowedOrigins = ["https://myapp.com", "http://localhost:3000"]

  # Không có allowedOrigins → chặn tất cả cross-origin
  # allowedOrigins = null  → cho phép tất cả (KHÔNG dùng production)
}
```

**CORS + JWT logic:**
1. CORS filter chặn unauthorized origins ở browser
2. Non-browser (curl, Postman) bypass CORS nhưng cần JWT token
3. JWT token chỉ được tạo sau khi auth → không thể forge
