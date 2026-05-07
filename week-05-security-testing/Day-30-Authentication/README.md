# Day 30 - Authentication với JWT

## Mục tiêu
- Implement JWT authentication
- Login/logout flow
- Protect routes với Action Composition

---

## 1. Setup JWT

```scala
// build.sbt
libraryDependencies ++= Seq(
  "com.auth0" % "java-jwt" % "4.4.0"
)
```

---

## 2. JWT Service

```java
// app/security/JwtService.java
package security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import play.Configuration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Singleton
public class JwtService {

    private final Algorithm algorithm;
    private final long expirationHours;

    @Inject
    public JwtService(play.libs.ws.WSClient ws,
                      com.typesafe.config.Config config) {
        String secret = config.getString("jwt.secret");
        this.algorithm = Algorithm.HMAC256(secret);
        this.expirationHours = config.getLong("jwt.expirationHours");
    }

    public String generateToken(Long userId, String email, String role) {
        return JWT.create()
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withClaim("role", role)
            .withIssuedAt(Date.from(Instant.now()))
            .withExpiresAt(Date.from(Instant.now().plus(expirationHours, ChronoUnit.HOURS)))
            .sign(algorithm);
    }

    public DecodedJWT verifyToken(String token) {
        // Throws JWTVerificationException nếu invalid/expired
        return JWT.require(algorithm)
            .build()
            .verify(token);
    }

    public Long getUserId(String token) {
        return Long.parseLong(verifyToken(token).getSubject());
    }

    public String getRole(String token) {
        return verifyToken(token).getClaim("role").asString();
    }
}
```

---

## 3. JWT Action Composition

```java
// app/security/JwtAction.java
package security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import play.libs.Json;
import play.libs.typedmap.TypedKey;
import play.mvc.*;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class JwtAction extends Action.Simple {

    public static final TypedKey<Long> USER_ID = TypedKey.create("userId");
    public static final TypedKey<String> USER_ROLE = TypedKey.create("userRole");

    @Inject
    private JwtService jwtService;

    @Override
    public CompletionStage<Result> call(Http.Request request) {
        return request.header("Authorization")
            .filter(h -> h.startsWith("Bearer "))
            .map(h -> h.substring(7))
            .map(token -> {
                try {
                    DecodedJWT decoded = jwtService.verifyToken(token);
                    Long userId = Long.parseLong(decoded.getSubject());
                    String role = decoded.getClaim("role").asString();

                    Http.Request enriched = request
                        .addAttr(USER_ID, userId)
                        .addAttr(USER_ROLE, role);

                    return delegate.call(enriched);
                } catch (JWTVerificationException e) {
                    return CompletableFuture.completedFuture(
                        Results.unauthorized(Json.newObject().put("error", "Invalid or expired token"))
                    );
                }
            })
            .orElseGet(() ->
                CompletableFuture.completedFuture(
                    Results.unauthorized(Json.newObject().put("error", "Authorization header required"))
                )
            );
    }
}
```

---

## 4. Auth Controller

```java
// app/controllers/AuthController.java
package controllers;

import play.libs.Json;
import play.mvc.*;
import security.JwtService;

import javax.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AuthController extends Controller {

    private final JwtService jwtService;
    // In real app, inject UserRepository

    @Inject
    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    // POST /auth/login
    public Result login(Http.Request request) {
        JsonNode body = request.body().asJson();
        if (body == null) return badRequest("JSON required");

        String email = body.path("email").asText();
        String password = body.path("password").asText();

        // In real app: userRepository.findByEmail(email)
        // then verify password hash
        // For demo: hardcoded
        if ("admin@example.com".equals(email) && "password123".equals(password)) {
            String token = jwtService.generateToken(1L, email, "ADMIN");

            ObjectNode response = Json.newObject();
            response.put("token", token);
            response.put("userId", 1);
            response.put("role", "ADMIN");
            return ok(response);
        }

        return unauthorized(Json.newObject().put("error", "Invalid credentials"));
    }

    // GET /auth/me - protected endpoint
    @play.mvc.With(JwtAction.class)
    public Result me(Http.Request request) {
        Long userId = request.attrs().get(JwtAction.USER_ID);
        String role = request.attrs().get(JwtAction.USER_ROLE);

        ObjectNode response = Json.newObject();
        response.put("userId", userId);
        response.put("role", role);
        return ok(response);
    }
}
```

---

## 5. Cấu Hình

```hocon
# application.conf
jwt {
  secret = "your-super-secret-key-min-256-bits"
  secret = ${?JWT_SECRET}  # Override bằng env var
  expirationHours = 24
}
```

---

## 6. Test Flow

```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"password123"}' \
  | jq -r '.token')

echo "Token: $TOKEN"

# Gọi protected endpoint
curl http://localhost:9000/auth/me \
  -H "Authorization: Bearer $TOKEN"

# Test với token sai
curl http://localhost:9000/auth/me \
  -H "Authorization: Bearer invalid-token"
# → 401 Unauthorized
```

---

## 7. Role-Based Authorization

```java
// Annotation cho roles
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@With(RoleAction.class)
public @interface RequireRole {
    String[] value();
}

// Action check role
public class RoleAction extends Action<RequireRole> {
    @Override
    public CompletionStage<Result> call(Http.Request request) {
        String userRole = request.attrs().getOptional(JwtAction.USER_ROLE).orElse("");
        String[] requiredRoles = configuration.value();

        for (String role : requiredRoles) {
            if (role.equals(userRole)) {
                return delegate.call(request);
            }
        }

        return CompletableFuture.completedFuture(
            Results.forbidden(Json.newObject().put("error", "Insufficient permissions"))
        );
    }
}

// Dùng trong controller
@With(JwtAction.class)       // Trước: verify JWT
@RequireRole({"ADMIN"})     // Sau: check role
public Result adminOnly(Http.Request request) {
    return ok("Admin panel");
}
```
