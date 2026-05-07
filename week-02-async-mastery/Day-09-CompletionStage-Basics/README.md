# Day 09 - CompletionStage & CompletableFuture

## Mục tiêu
- Hiểu sự khác biệt CompletionStage vs CompletableFuture
- Thành thạo: thenApply, thenCompose, thenCombine, exceptionally, handle
- Nắm bẫy thenApply vs thenCompose (sai lầm #1 của mọi người)

---

## 1. CompletionStage vs CompletableFuture

```java
// CompletionStage<T>: Interface - chỉ define cách compose
// → Dùng làm return type của API (ẩn implementation)
CompletionStage<User> findUser(Long id);

// CompletableFuture<T>: Class implements CompletionStage
// → Thêm khả năng complete(), completeExceptionally(), cancel()
// → Dùng trong internal code khi cần control completion
CompletableFuture<User> future = new CompletableFuture<>();
future.complete(new User(1, "Alice"));  // Trigger completion
future.completeExceptionally(new RuntimeException("DB error"));
```

**Quy tắc:**
- Return type của method public → `CompletionStage<T>`
- Khi cần tự complete → `CompletableFuture<T>`
- Khi cần convert → `.toCompletableFuture()`

---

## 2. Tạo CompletionStage

```java
// 1. Already completed (giá trị đã có sẵn)
CompletionStage<String> done = CompletableFuture.completedFuture("hello");

// 2. Run async trên ForkJoinPool (mặc định)
CompletionStage<String> async1 = CompletableFuture.supplyAsync(() -> {
    return fetchFromDB();  // chạy trên ForkJoinPool.commonPool()
});

// 3. Run async trên executor cụ thể (KHUYẾN NGHỊ cho blocking I/O)
ExecutorService myPool = Executors.newFixedThreadPool(10);
CompletionStage<String> async2 = CompletableFuture.supplyAsync(
    () -> fetchFromDB(),
    myPool  // chạy trên myPool thay vì ForkJoinPool
);

// 4. Run async không có return value
CompletionStage<Void> noReturn = CompletableFuture.runAsync(() -> {
    sendEmail("user@example.com");
});

// 5. Failed stage
CompletionStage<String> failed = CompletableFuture.failedFuture(
    new RuntimeException("Something went wrong")
);
```

---

## 3. Transform: thenApply

`thenApply` = `map` trong Stream: transform value T → U.

```java
CompletionStage<Integer> numberStage = CompletableFuture.completedFuture(5);

// Transform: Integer → String
CompletionStage<String> strStage = numberStage.thenApply(n -> "Value: " + n);

// Chain nhiều transform
CompletionStage<Integer> result = CompletableFuture.completedFuture("  hello  ")
    .thenApply(String::trim)           // "  hello  " → "hello"
    .thenApply(String::toUpperCase)    // "hello" → "HELLO"
    .thenApply(String::length);        // "HELLO" → 5

// Trong Play controller
public CompletionStage<Result> getUser(Long id) {
    return userService.findById(id)             // CompletionStage<User>
        .thenApply(user -> Json.toJson(user))   // CompletionStage<JsonNode>
        .thenApply(Results::ok);                // CompletionStage<Result>
}
```

---

## 4. Flat-Map: thenCompose ⭐ (Quan Trọng Nhất!)

`thenCompose` = `flatMap` trong Stream: khi function trả về `CompletionStage`.

```java
// Scenario: findUser → rồi findOrders của user đó

// ❌ SAI: dùng thenApply khi function trả CompletionStage
CompletionStage<CompletionStage<List<Order>>> WRONG =
    userService.findById(1L)
        .thenApply(user -> orderService.findByUser(user.id));
        // thenApply nhận T → U
        // Nhưng orderService.findByUser() trả CompletionStage<List<Order>>
        // Nên kết quả là CompletionStage<CompletionStage<...>> → nested!

// ✅ ĐÚNG: dùng thenCompose khi function trả CompletionStage
CompletionStage<List<Order>> CORRECT =
    userService.findById(1L)
        .thenCompose(user -> orderService.findByUser(user.id));
        // thenCompose "flatten" lớp ngoài → kết quả phẳng

// Quy tắc nhớ:
// function(value) → value          → thenApply
// function(value) → CompletionStage → thenCompose
```

**Ví dụ thực tế:**

```java
public CompletionStage<Result> getOrdersForUser(Long userId) {
    return userService.findById(userId)                     // CS<User>
        .thenCompose(user ->
            orderService.findByUserId(user.id)              // CS<List<Order>>
        )
        .thenCompose(orders ->
            enrichOrdersWithProducts(orders)                // CS<List<EnrichedOrder>>
        )
        .thenApply(enrichedOrders -> ok(Json.toJson(enrichedOrders)));
}
```

---

## 5. Parallel: thenCombine và allOf

```java
// thenCombine: Chờ 2 future song song, kết hợp kết quả
CompletionStage<User> userF = userService.findById(1L);
CompletionStage<List<Product>> productsF = productService.findAll();

// Cả 2 chạy ĐỒNG THỜI
CompletionStage<ObjectNode> result = userF.thenCombine(productsF,
    (user, products) -> {
        ObjectNode json = Json.newObject();
        json.set("user", Json.toJson(user));
        json.set("products", Json.toJson(products));
        return json;
    }
);
// Thời gian = max(user query, product query) - không phải tổng!

// allOf: Chờ nhiều future, không lấy giá trị riêng lẻ
CompletableFuture<Void> all = CompletableFuture.allOf(
    userF.toCompletableFuture(),
    productsF.toCompletableFuture(),
    orderService.findAll().toCompletableFuture()
);
all.thenRun(() -> System.out.println("All done!"));

// anyOf: Lấy kết quả của future hoàn thành đầu tiên
CompletableFuture<Object> first = CompletableFuture.anyOf(
    primaryDb.query().toCompletableFuture(),
    replicaDb.query().toCompletableFuture()
);
```

---

## 6. Xử Lý Lỗi

```java
// exceptionally: Handle lỗi, trả về fallback value
CompletionStage<User> withFallback = userService.findById(1L)
    .exceptionally(throwable -> {
        log.error("Failed to find user", throwable);
        return User.anonymous();  // fallback
    });

// handle: Xử lý cả success VÀ error trong cùng 1 function
CompletionStage<Result> handled = userService.findById(1L)
    .handle((user, throwable) -> {
        if (throwable != null) {
            log.error("Error", throwable);
            return internalServerError("Service unavailable");
        }
        return ok(Json.toJson(user));
    });

// whenComplete: Side effect, không thay đổi value
CompletionStage<User> withLogging = userService.findById(1L)
    .whenComplete((user, throwable) -> {
        if (throwable != null) {
            metrics.incrementErrorCount();
        } else {
            metrics.incrementSuccessCount();
        }
        // Value vẫn là User hoặc propagate exception
    });
```

---

## 7. Execution Context Trong Chain

```java
// Mỗi callback có thể chạy trên thread pool khác nhau
// Mặc định: chạy trên cùng thread hoàn thành stage trước

// Specify executor cho callback cụ thể
ExecutorService myExecutor = Executors.newFixedThreadPool(4);

CompletableFuture.supplyAsync(() -> fetchData(), ioPool)  // chạy trên ioPool
    .thenApplyAsync(data -> process(data), cpuPool)       // chạy trên cpuPool
    .thenAcceptAsync(result -> save(result), dbPool);     // chạy trên dbPool

// Không có Async suffix → chạy trên thread hoàn thành stage trước
.thenApply(...)   // thread nào complete stage trước thì xử lý callback này
.thenApplyAsync(..., executor)  // explicitly schedule trên executor
```

---

## 8. Anti-Pattern: .get() và .join()

```java
// ❌ NGUY HIỂM: Block thread hiện tại chờ kết quả
public CompletionStage<Result> bad() {
    User user = userService.findById(1L)
        .toCompletableFuture()
        .get();  // BLOCK! Đánh bại toàn bộ mục đích async
    return CompletableFuture.completedFuture(ok(Json.toJson(user)));
}

// ❌ Tương tự:
String result = future.toCompletableFuture().join();  // BLOCK!

// ✅ ĐÚNG: Chain không block
public CompletionStage<Result> good() {
    return userService.findById(1L)
        .thenApply(user -> ok(Json.toJson(user)));
}
```

**Khi nào được phép dùng .get():**
- Trong `main()` method khi shutdown
- Trong test (dùng `.get(timeout, unit)` với timeout)
- Trong background job không liên quan đến request handling

---

## 9. Bài Tập

Xem `completionstage-demo/` trong thư mục này:

1. Chain 3 service calls: findUser → findOrders → calculateTotal
2. Parallel calls: fetchUserProfile + fetchUserSettings (cùng lúc)
3. Error handling: retry khi fail lần đầu
4. Timeout: cancel nếu quá 500ms

```bash
cd completionstage-demo
sbt run

curl "http://localhost:9000/chain/1"      # chain 3 calls
curl "http://localhost:9000/parallel/1"   # parallel calls
curl "http://localhost:9000/error/1"      # error handling
curl "http://localhost:9000/timeout/1"    # timeout demo
```
