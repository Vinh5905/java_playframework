# Day 29 - Filters: Global Middleware

## Mục tiêu
- Implement EssentialFilter (chạy cho mọi request)
- Logging filter, CORS filter, Rate limiting
- Thứ tự filter và cách debug

---

## 1. Filter vs Action Composition

| | Filter (EssentialFilter) | Action Composition |
|--|--------------------------|-------------------|
| Phạm vi | TOÀN BỘ request | Per-controller hoặc per-action |
| Config | `application.conf` | `@With` annotation |
| Use case | Logging, CORS, Security headers | Auth, Rate limit per endpoint |

---

## 2. Logging Filter

```java
// app/filters/RequestLoggingFilter.java
package filters;

import org.apache.pekko.stream.Materializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.EssentialAction;
import play.mvc.EssentialFilter;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RequestLoggingFilter extends EssentialFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final Materializer mat;

    @Inject
    public RequestLoggingFilter(Materializer mat) {
        this.mat = mat;
    }

    @Override
    public EssentialAction apply(EssentialAction next) {
        return EssentialAction.of(request -> {
            long startTime = System.currentTimeMillis();
            String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);

            log.info("→ [{}] {} {}", requestId, request.method(), request.uri());

            return next.apply(request).map(result -> {
                long duration = System.currentTimeMillis() - startTime;
                log.info("← [{}] {} {} {} {}ms",
                    requestId,
                    request.method(),
                    request.uri(),
                    result.status(),
                    duration
                );

                // Thêm response header để track
                return result
                    .withHeader("X-Request-Id", requestId)
                    .withHeader("X-Response-Time", duration + "ms");
            }, mat.executionContext());
        });
    }
}
```

---

## 3. Rate Limiting Filter

```java
// app/filters/RateLimitFilter.java
package filters;

import org.apache.pekko.stream.Materializer;
import play.libs.Json;
import play.mvc.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class RateLimitFilter extends EssentialFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final Materializer mat;

    @Inject
    public RateLimitFilter(Materializer mat) {
        this.mat = mat;
        // Reset counts mỗi phút
        java.util.concurrent.Executors.newScheduledThreadPool(1)
            .scheduleAtFixedRate(requestCounts::clear, 1, 1, java.util.concurrent.TimeUnit.MINUTES);
    }

    @Override
    public EssentialAction apply(EssentialAction next) {
        return EssentialAction.of(request -> {
            String clientIp = request.remoteAddress();
            int count = requestCounts
                .computeIfAbsent(clientIp, k -> new AtomicInteger(0))
                .incrementAndGet();

            if (count > MAX_REQUESTS_PER_MINUTE) {
                // Return 429 Too Many Requests
                return org.apache.pekko.stream.javadsl.Source.single(
                    play.mvc.Results.status(429,
                        Json.newObject().put("error", "Rate limit exceeded. Max 100 req/min")
                    ).withHeader("Retry-After", "60")
                );
            }

            return next.apply(request).map(result ->
                result.withHeader("X-RateLimit-Remaining",
                    String.valueOf(MAX_REQUESTS_PER_MINUTE - count)),
                mat.executionContext()
            );
        });
    }
}
```

---

## 4. Đăng Ký Filters

```hocon
# application.conf
play.filters.enabled = [
  "play.filters.hosts.AllowedHostsFilter",
  "play.filters.headers.SecurityHeadersFilter",
  "play.filters.cors.CORSFilter",
  "filters.RequestLoggingFilter",
  "filters.RateLimitFilter"
]

# CORS config
play.filters.cors {
  allowedOrigins = ["http://localhost:3000", "https://myapp.com"]
  allowedHttpMethods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
  allowedHttpHeaders = ["Accept", "Content-Type", "Authorization"]
  exposedHeaders = ["X-Request-Id", "X-Response-Time"]
  preflightMaxAge = 3 days
}

# Security headers
play.filters.headers {
  frameOptions = "DENY"
  xssProtection = "1; mode=block"
  contentTypeOptions = "nosniff"
  strictTransportSecurity = "max-age=31536000; includeSubDomains"
  contentSecurityPolicy = "default-src 'self'"
}
```

---

## 5. Filters Chỉ Cho Đường Path Cụ Thể

```java
@Override
public EssentialAction apply(EssentialAction next) {
    return EssentialAction.of(request -> {
        // Chỉ áp rate limit cho /api/* paths
        if (!request.path().startsWith("/api/")) {
            return next.apply(request);
        }
        // Apply rate limiting logic...
        return applyRateLimit(request, next);
    });
}
```

---

## 6. Bài Tập

Xem `filters-demo/` trong thư mục này.

```bash
cd filters-demo
sbt run

# Test logging filter (xem log output)
curl http://localhost:9000/api/test
# Log: → [abc123] GET /api/test
# Log: ← [abc123] GET /api/test 200 5ms

# Test rate limiting (gửi > 100 requests/phút)
for i in $(seq 1 110); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9000/api/test
done
# 100 đầu: 200, từ 101 trở đi: 429

# Test CORS headers
curl -H "Origin: http://localhost:3000" \
     -H "Access-Control-Request-Method: GET" \
     -X OPTIONS http://localhost:9000/api/test -v
```
