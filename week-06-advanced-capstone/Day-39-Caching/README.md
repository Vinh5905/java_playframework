# Day 39 - Caching: Memory & Redis

## Mục tiêu
- Play Cache API (AsyncCacheApi)
- In-memory cache (dev)
- Redis cache (production)
- Cache patterns: cache-aside, write-through

---

## 1. Setup

```scala
// build.sbt
libraryDependencies += cacheApi  // Play Cache API

// Cho Redis (production):
// libraryDependencies += "com.github.karelcemus" %% "play-redis" % "3.0.0"
```

---

## 2. AsyncCacheApi Cơ Bản

```java
import play.cache.AsyncCacheApi;
import javax.inject.Inject;

@Singleton
public class UserService {

    private final AsyncCacheApi cache;
    private final UserRepository userRepo;

    @Inject
    public UserService(AsyncCacheApi cache, UserRepository userRepo) {
        this.cache = cache;
        this.userRepo = userRepo;
    }

    // Cache-aside pattern
    public CompletionStage<User> findById(Long id) {
        String cacheKey = "user:" + id;

        return cache.getOrElseUpdate(
            cacheKey,
            () -> userRepo.findById(id)  // Chỉ gọi DB khi cache miss
                .thenApply(opt -> opt.orElseThrow(() -> new NotFoundException("User", id))),
            300  // TTL: 300 seconds
        );
    }

    public CompletionStage<User> update(Long id, UserData data) {
        return userRepo.update(id, data)
            .thenCompose(updated -> {
                // Invalidate cache sau update
                String cacheKey = "user:" + id;
                return cache.remove(cacheKey)
                    .thenApply(v -> updated);
            });
    }

    public CompletionStage<Void> delete(Long id) {
        return userRepo.delete(id)
            .thenCompose(v -> cache.remove("user:" + id));
    }
}
```

---

## 3. Cache API

```java
// Get
CompletionStage<Optional<User>> user = cache.get("user:1");

// Set với TTL
cache.set("user:1", userObj, 300);  // 300 seconds

// GetOrElseUpdate (cache-aside in one call)
cache.getOrElseUpdate("user:1", () -> fetchUser(1L), 300);

// Remove
cache.remove("user:1");

// Remove tất cả (CÀN THẬN trong production!)
cache.removeAll();
```

---

## 4. Cache Patterns

### Cache-Aside (Lazy Loading)
```
Read: Check cache → miss → read DB → write cache → return
Write: Update DB → invalidate cache
```

### Write-Through
```
Write: Update DB + write cache (cùng lúc)
Read: Check cache → always hit (eventually)
```

### Cache Stampede (Thundering Herd)
```
Problem: Cache expires → 1000 requests → tất cả hit DB cùng lúc
Solution: Only 1 request fetch DB, rest wait
```

```java
// Tránh cache stampede với lock
private final Map<String, CompletionStage<?>> inFlight = new ConcurrentHashMap<>();

public CompletionStage<User> findByIdSafe(Long id) {
    String key = "user:" + id;
    return cache.get(key).thenCompose(opt -> {
        if (opt.isPresent()) return CompletableFuture.completedFuture(opt.get());

        // Chỉ 1 request fetch DB, rest piggyback
        return (CompletionStage<User>) inFlight.computeIfAbsent(key, k ->
            userRepo.findById(id)
                .thenCompose(user -> cache.set(key, user, 300).thenApply(v -> user))
                .whenComplete((u, t) -> inFlight.remove(key))
        );
    });
}
```

---

## 5. Redis (Production Config)

```hocon
# application.conf
play.cache.redis {
  host = "localhost"
  host = ${?REDIS_HOST}
  port = 6379
  port = ${?REDIS_PORT}
  database = 0
}

# Đặt default cache là Redis
play.cache.defaultCache = "play-redis"
```

```bash
# Start Redis với Docker
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

---

## 6. Named Caches - Nhiều Cache Instance

```java
import play.cache.NamedCache;

// application.conf
// play.cache.bindCaches = ["user-cache", "product-cache", "session-cache"]

public class UserService {

    private final AsyncCacheApi userCache;
    private final AsyncCacheApi productCache;

    @Inject
    public UserService(
        @NamedCache("user-cache") AsyncCacheApi userCache,
        @NamedCache("product-cache") AsyncCacheApi productCache
    ) {
        this.userCache = userCache;
        this.productCache = productCache;
    }

    public CompletionStage<User> getUser(Long id) {
        return userCache.getOrElseUpdate("user:" + id, () -> userRepo.findById(id), 300);
    }

    public CompletionStage<Product> getProduct(Long id) {
        return productCache.getOrElseUpdate("product:" + id, () -> productRepo.findById(id), 3600);
    }
}
```

**Cấu hình Caffeine per named cache:**
```hocon
play.cache.caffeine.caches {
  user-cache {
    initial-capacity = 200
    maximum-size = 10000
    expire-after-write = 10m
  }
  product-cache {
    maximum-size = 5000
    expire-after-write = 1h
  }
}
```

---

## 7. Caffeine - Default Cache Implementation

Từ Play 3.x, **Caffeine** là implementation mặc định (thay EhCache):

```scala
// build.sbt
libraryDependencies += cacheApi
// Caffeine được include tự động
```

```hocon
# application.conf - Caffeine config
play.cache.caffeine.defaults {
  initial-capacity = 100
  maximum-size = 500
  expire-after-write = 1h
}
```

**Tại sao Caffeine tốt hơn EhCache 2.x:**
- Throughput cao hơn (Window TinyLFU algorithm)
- Memory footprint nhỏ hơn
- Không cần file config XML

---

## 8. Custom Dispatcher Cho Cache

Nếu cache implementation có blocking I/O (Redis network calls), cần tách khỏi default dispatcher:

```hocon
# application.conf
play.cache.dispatcher = "contexts.cache-dispatcher"

contexts.cache-dispatcher {
  fork-join-executor {
    parallelism-max = 20
  }
}
```

---

## 9. Bài Tập

1. Cache kết quả query `GET /todos` với TTL 30 giây
2. Invalidate cache khi tạo/sửa/xóa todo
3. Test: Gọi list 10 lần → log chỉ show 1 DB query
4. Measure latency trước và sau cache

```bash
# Measure response time
time curl http://localhost:9000/todos  # First request (DB)
time curl http://localhost:9000/todos  # Subsequent (cache)
```
