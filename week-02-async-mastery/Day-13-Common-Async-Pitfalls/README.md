# Day 13 - Common Async Pitfalls: 6 Sai Lầm Chết Người

## Mục tiêu
- Nhận ra và tránh 6 sai lầm phổ biến nhất khi viết async code
- Hiểu hậu quả của từng sai lầm
- Code patterns để phòng tránh

---

## Sai Lầm #1: Gọi `.get()` hoặc `.join()`

```java
// ❌ SAI: Block thread đang xử lý request
public CompletionStage<Result> bad(Long id) {
    // .get() BLOCK thread cho đến khi có kết quả
    // Đánh bại toàn bộ mục đích của async!
    try {
        User user = userService.findById(id)
            .toCompletableFuture()
            .get();  // BLOCK!
        return CompletableFuture.completedFuture(ok(Json.toJson(user)));
    } catch (Exception e) {
        return CompletableFuture.completedFuture(internalServerError());
    }
}

// ❌ join() cũng vậy
User user = userService.findById(id).toCompletableFuture().join();  // BLOCK!

// ✅ ĐÚNG: Dùng thenApply/thenCompose - không block
public CompletionStage<Result> good(Long id) {
    return userService.findById(id)
        .thenApply(user -> ok(Json.toJson(user)));
}
```

**Hậu quả**: App bị freeze dưới load, throughput giảm xuống mức sync.

---

## Sai Lầm #2: `thenApply` Thay Vì `thenCompose` (Nested Future)

```java
// ❌ SAI: Kết quả là CompletionStage<CompletionStage<User>> - nested!
CompletionStage<CompletionStage<User>> WRONG =
    userService.findById(1L)                   // CS<User>
        .thenApply(user ->
            profileService.refresh(user)        // CS<User> ← function trả CS
        );
// thenApply mong đợi function trả VALUE, nhưng profileService.refresh() trả CS
// → Kết quả bị bọc thêm 1 lớp CS → không thể dùng trực tiếp

// ✅ ĐÚNG: thenCompose flatten lớp ngoài
CompletionStage<User> CORRECT =
    userService.findById(1L)
        .thenCompose(user ->
            profileService.refresh(user)        // CS<User>
        );
// thenCompose "unwrap" → kết quả là CS<User> phẳng

// Quy tắc nhớ nhanh:
// function returns T         → thenApply(T → U)
// function returns CS<T>     → thenCompose(T → CS<U>)
```

**Cách phát hiện**: Code compile được nhưng type là `CompletionStage<CompletionStage<...>>` → sai.

---

## Sai Lầm #3: Không Xử Lý Exception

```java
// ❌ SAI: Exception bị "nuốt" - request bị treo mãi mãi hoặc crash không rõ nguyên nhân
public CompletionStage<Result> noErrorHandling(Long id) {
    return userService.findById(id)
        .thenApply(user -> ok(Json.toJson(user)));
    // Nếu userService throw exception → CompletionStage failed
    // Nhưng không có handler → Play không biết trả gì về cho client
}

// ✅ ĐÚNG: Luôn có exceptionally hoặc handle
public CompletionStage<Result> withErrorHandling(Long id) {
    return userService.findById(id)
        .thenApply(user -> ok(Json.toJson(user)))
        .exceptionally(throwable -> {
            log.error("Failed to get user " + id, throwable);
            if (throwable.getCause() instanceof NotFoundException) {
                return notFound(Json.newObject().put("error", "Not found"));
            }
            return internalServerError(Json.newObject().put("error", "Server error"));
        });
}

// Hoặc dùng handle (xử lý cả success và error):
public CompletionStage<Result> withHandle(Long id) {
    return userService.findById(id)
        .handle((user, throwable) -> {
            if (throwable != null) {
                log.error("Error", throwable);
                return internalServerError("Error: " + throwable.getMessage());
            }
            return ok(Json.toJson(user));
        });
}
```

---

## Sai Lầm #4: ThreadLocal Không Hoạt Động

```java
// ❌ SAI: ThreadLocal không persist qua async callback
ThreadLocal<String> requestId = new ThreadLocal<>();

public CompletionStage<Result> bad(Http.Request request) {
    requestId.set(request.id());  // Set trên Thread A

    return CompletableFuture.supplyAsync(() -> {
        // Chạy trên Thread B - KHÁC với Thread A!
        String id = requestId.get();  // null! Thread B không có value
        log.info("Request ID: " + id);  // Luôn log null
        return "done";
    }).thenApply(Results::ok);
}

// ✅ ĐÚNG: Capture trong closure (effectively final)
public CompletionStage<Result> good(Http.Request request) {
    String requestId = request.id();  // Capture vào local variable

    return CompletableFuture.supplyAsync(() -> {
        // requestId được capture trong closure → có thể truy cập
        log.info("Request ID: " + requestId);  // ✅
        return "done";
    }).thenApply(Results::ok);
}

// ✅ ĐÚNG: Dùng Request attributes
public CompletionStage<Result> withAttributes(Http.Request request) {
    return someService.process()
        .thenApply(data -> {
            // request object được capture trong closure
            String userId = request.attrs().get(Security.USER_ID_KEY);
            return ok(data);
        });
}
```

---

## Sai Lầm #5: Sequential Khi Có Thể Parallel

```java
// ❌ SAI: 3 independent queries chạy tuần tự (3 giây nếu mỗi cái 1 giây)
public CompletionStage<Result> sequential(Long userId) {
    return userService.findById(userId)           // Đợi 1s
        .thenCompose(user ->
            orderService.findByUser(userId)       // Đợi thêm 1s
        )
        .thenCompose(orders ->
            productService.findAll()              // Đợi thêm 1s
        )
        .thenApply(products -> ok("done"));
    // Tổng: 3 giây!
}

// ✅ ĐÚNG: Chạy song song khi không phụ thuộc nhau
public CompletionStage<Result> parallel(Long userId) {
    // 3 queries chạy CÙNG LÚC
    CompletionStage<User> userF = userService.findById(userId);
    CompletionStage<List<Order>> ordersF = orderService.findByUser(userId);
    CompletionStage<List<Product>> productsF = productService.findAll();

    return userF
        .thenCombine(ordersF, (user, orders) -> new Object[]{user, orders})
        .thenCombine(productsF, (arr, products) -> {
            // Kết hợp kết quả
            ObjectNode json = Json.newObject();
            json.set("user", Json.toJson(arr[0]));
            json.set("orders", Json.toJson(arr[1]));
            json.set("products", Json.toJson(products));
            return ok(json);
        });
    // Tổng: max(1s, 1s, 1s) = 1 giây!
}
```

---

## Sai Lầm #6: Quên Complete Future → Request Bị Treo

```java
// ❌ SAI: Future không bao giờ complete
public CompletionStage<Result> leak(Http.Request request) {
    CompletableFuture<Result> future = new CompletableFuture<>();

    callbackService.doSomething(result -> {
        if (result.isSuccess()) {
            future.complete(ok("done"));  // ✅ Complete on success
        }
        // Nhưng nếu result.isError() → không complete!
        // → future treo mãi → request timeout sau 60-120s
    });

    return future;  // Trả future về ngay
}

// ✅ ĐÚNG: Luôn complete trong mọi nhánh
public CompletionStage<Result> noLeak(Http.Request request) {
    CompletableFuture<Result> future = new CompletableFuture<>();

    callbackService.doSomething(result -> {
        if (result.isSuccess()) {
            future.complete(ok("done"));
        } else {
            // Phải complete trong nhánh error!
            future.complete(internalServerError("Error: " + result.getError()));
            // Hoặc:
            // future.completeExceptionally(new RuntimeException(result.getError()));
        }
    });

    return future;
}
```

---

## Bonus: Sai Lầm #7 - Blocking Trong thenApply

```java
// ❌ SAI: thenApply chạy trên thread đang handle stage trước
// Nếu đó là default EC thread → block nó!
public CompletionStage<Result> bad() {
    return CompletableFuture.completedFuture("data")
        .thenApply(data -> {
            Thread.sleep(1000);  // Block thread của default EC!
            return ok(data);
        });
}

// ✅ ĐÚNG: Blocking trong thenApplyAsync với executor riêng
public CompletionStage<Result> good() {
    return CompletableFuture.completedFuture("data")
        .thenApplyAsync(data -> {
            Thread.sleep(1000);  // Chạy trên blockingEc
            return ok(data);
        }, blockingEc);
}
```

---

## Checklist Trước Khi Ship Async Code

- [ ] Không dùng `.get()` hoặc `.join()` trong request handler
- [ ] `thenCompose` cho function trả `CompletionStage`
- [ ] Tất cả chain có `exceptionally` hoặc `handle`
- [ ] Không dùng ThreadLocal → dùng closure capture hoặc Request attributes
- [ ] Independent I/O chạy song song với `thenCombine` / `allOf`
- [ ] `CompletableFuture` thủ công luôn được complete trong mọi nhánh
- [ ] Blocking code có explicit executor (không để supplyAsync tự chọn)
