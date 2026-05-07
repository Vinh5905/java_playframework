# Day 10 - Async Actions Trong Play

## Mục tiêu
- Viết async action trả CompletionStage<Result>
- Hiểu khi nào dùng sync vs async action
- Pattern thực tế: service layer trả CompletionStage

---

## 1. Sync Action vs Async Action

```java
// SYNC: method trả Result trực tiếp
public Result syncAction() {
    String data = "some data";
    return ok(data);
}

// ASYNC: method trả CompletionStage<Result>
public CompletionStage<Result> asyncAction() {
    return CompletableFuture.supplyAsync(() -> "some data")
        .thenApply(Results::ok);
}
```

Play xử lý cả hai đều ổn. Khi nào dùng async:
- Gọi service không đồng bộ (database, HTTP calls)
- Xử lý cần nhiều I/O operations song song
- Khi service layer đã trả CompletionStage

---

## 2. Pattern Thực Tế: Layered Architecture

```
Controller (async action)
    └─ Service (trả CompletionStage)
        └─ Repository (trả CompletionStage, chạy trên blocking EC)
            └─ Database driver
```

```java
// Repository - chạy blocking JDBC trên dispatcher riêng
@Singleton
public class UserRepository {
    private final ExecutionContext dbEc;
    private final Database db;

    @Inject
    public UserRepository(Database db, ActorSystem system) {
        this.db = db;
        this.dbEc = system.dispatchers().lookup("blocking-db-dispatcher");
    }

    public CompletionStage<Optional<User>> findById(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            // Blocking JDBC - OK vì chạy trên dbEc, không phải default EC
            return db.withConnection(conn -> {
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
                ps.setLong(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.<User>empty();
            });
        }, dbEc);  // ← Chạy trên dbEc!
    }
}

// Service - orchestrate business logic
@Singleton
public class UserService {
    private final UserRepository userRepo;
    private final OrderRepository orderRepo;

    @Inject
    public UserService(UserRepository userRepo, OrderRepository orderRepo) {
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
    }

    public CompletionStage<UserWithOrders> getUserWithOrders(Long id) {
        return userRepo.findById(id)                    // CS<Optional<User>>
            .thenCompose(userOpt ->
                userOpt.map(user ->
                    orderRepo.findByUserId(user.id)     // CS<List<Order>>
                        .thenApply(orders ->
                            new UserWithOrders(user, orders)
                        )
                ).orElseGet(() ->
                    CompletableFuture.failedFuture(new UserNotFoundException(id))
                )
            );
    }
}

// Controller - thin layer, handle HTTP concerns
public class UserController extends Controller {
    private final UserService userService;

    @Inject
    public UserController(UserService userService) {
        this.userService = userService;
    }

    public CompletionStage<Result> show(Long id) {
        return userService.getUserWithOrders(id)
            .thenApply(data -> ok(Json.toJson(data)))
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof UserNotFoundException) {
                    return notFound(errorJson("User not found: " + id));
                }
                log.error("Unexpected error for user " + id, throwable);
                return internalServerError(errorJson("Internal server error"));
            });
    }
}
```

---

## 3. Parallel Calls Trong Controller

```java
public CompletionStage<Result> dashboard(Long userId) {
    // Chạy 3 queries song song thay vì tuần tự
    CompletionStage<User> userF = userService.findById(userId);
    CompletionStage<List<Order>> ordersF = orderService.findRecent(userId, 10);
    CompletionStage<Stats> statsF = statsService.getUserStats(userId);

    // Đợi tất cả xong
    return userF
        .thenCombine(ordersF, (user, orders) -> new Object[]{user, orders})
        .thenCombine(statsF, (arr, stats) -> {
            User user = (User) arr[0];
            List<Order> orders = (List<Order>) arr[1];

            ObjectNode json = Json.newObject();
            json.set("user", Json.toJson(user));
            json.set("recentOrders", Json.toJson(orders));
            json.set("stats", Json.toJson(stats));
            return ok(json);
        });
    // Thời gian = max(user, orders, stats) thay vì tổng
}
```

---

## 4. Async Action Với Timeout

```java
public CompletionStage<Result> withTimeout(Long id) {
    CompletionStage<User> userF = userService.findById(id);

    // Tạo timeout future
    CompletableFuture<User> timeoutF = new CompletableFuture<>();
    scheduler.schedule(
        () -> timeoutF.completeExceptionally(new TimeoutException()),
        500, TimeUnit.MILLISECONDS
    );

    // Race: future nào xong trước thắng
    return CompletableFuture.anyOf(
        userF.toCompletableFuture(),
        timeoutF
    )
    .thenApply(result -> ok(Json.toJson(result)))
    .exceptionally(t -> {
        if (t.getCause() instanceof TimeoutException) {
            return status(503, "Service temporarily unavailable");
        }
        return internalServerError("Error");
    });
}
```

---

## 5. Chú Ý: Http.Context Deprecated

Play cũ (2.x) có `Http.Context.current()` - một ThreadLocal static.

```java
// ❌ DEPRECATED và KHÔNG AN TOÀN trong async
Http.Context ctx = Http.Context.current();  // ThreadLocal!
String lang = ctx.lang().code();  // Nguy hiểm trong async callback

// ✅ ĐÚNG: Truyền request tường minh
public CompletionStage<Result> action(Http.Request request) {
    String lang = request.acceptLanguages().get(0).code();
    return service.process()
        .thenApply(result -> ok(result).withLang(lang, messagesApi));
}
```

---

## 6. Testing Async Actions

```java
@Test
public void testAsyncAction() throws Exception {
    Application app = new GuiceApplicationBuilder().build();

    try {
        Http.RequestBuilder request = Helpers.fakeRequest("GET", "/users/1");
        CompletionStage<Result> resultStage = Helpers.routeAsync(app, request);

        // Lấy kết quả với timeout
        Result result = resultStage
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

        assertEquals(200, result.status());
    } finally {
        Helpers.stop(app);
    }
}
```

---

## 7. Bài Tập

Xem `async-actions-demo/` trong thư mục này.

Project demo layered architecture:
- `UserRepository` - async query
- `UserService` - orchestration
- `UserController` - thin HTTP layer

```bash
cd async-actions-demo
sbt run

# Test single user
curl http://localhost:9000/users/1

# Test dashboard (parallel calls)
curl http://localhost:9000/users/1/dashboard

# Test với delay simulation
curl "http://localhost:9000/users/1/slow"
```
