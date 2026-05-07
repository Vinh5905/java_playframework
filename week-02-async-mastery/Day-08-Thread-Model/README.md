# Day 08 - Thread Model: Tại Sao Play Khác Biệt?

## Mục tiêu
- Hiểu sâu sự khác biệt giữa thread-per-request và event-loop model
- Biết khi nào nên và không nên dùng async
- Nắm khái niệm blocking vs non-blocking I/O

---

## 1. Thread-Per-Request Model (Spring Boot / Tomcat)

```
[Tomcat Thread Pool: 200 threads]

Request 1 →  Thread 1  [==DB_QUERY(2s)==]→ Response 1
Request 2 →  Thread 2  [==DB_QUERY(2s)==]→ Response 2
...
Request 200→ Thread 200 [==DB_QUERY(2s)==]→ Response 200
Request 201→ ⏳ QUEUE (đợi thread trống)
Request 202→ ⏳ QUEUE
...
Request 300→ ❌ REJECT (connection timeout)
```

**Vấn đề:**
- Mỗi thread tốn ~1MB stack memory
- 200 threads = 200MB chỉ cho stack
- Khi DB query mất 2 giây, **thread ngồi không làm gì**, chỉ chờ
- Muốn nhiều concurrent hơn → tăng thread pool → tốn RAM → OS context switching overhead

**Khi nào OK:**
- Request ít concurrent (internal service, admin dashboard)
- Code đơn giản, team không quen async
- Dùng WebFlux của Spring nếu cần async

---

## 2. Event Loop / Non-Blocking Model (Play với Pekko HTTP)

```
[Event Loop: chỉ 8 threads (= số CPU cores)]

Request 1 → Thread 1 đăng ký callback → Thread 1 TRẢ VỀ NGAY
Request 2 → Thread 1 đăng ký callback → Thread 1 TRẢ VỀ NGAY
...
Request 10000→ Thread 2 đăng ký callback → Thread 2 TRẢ VỀ NGAY

2 giây sau:
DB trả kết quả cho Request 1 → OS interrupt → Thread 3 handle callback → Response 1
DB trả kết quả cho Request 2 → Thread 4 handle callback → Response 2
...
```

**Ưu điểm:**
- 8 threads phục vụ 10,000+ concurrent requests
- RAM thấp (thread không block)
- Throughput cao hơn nhiều cho I/O-bound workloads

**Nhược điểm:**
- Code phức tạp hơn (phải viết async)
- CPU-bound task vẫn cần thread riêng
- Khó debug hơn (stack trace bị cắt)
- ThreadLocal không hoạt động (phải dùng Request attributes)

---

## 3. Non-Blocking I/O - Cơ Chế Thực Tế

```
Blocking I/O (truyền thống):
  Thread gửi read() syscall
  → OS: "chờ đây, tôi đi đọc disk/network"
  → Thread: ngủ (blocked)
  → OS: "đọc xong rồi, data đây"
  → Thread: thức dậy, xử lý data

Non-blocking I/O (epoll/kqueue trên Linux/macOS):
  Thread gửi read() với flag O_NONBLOCK
  → OS: "OK, tôi sẽ báo khi có data" → trả về NGAY
  → Thread: tiếp tục làm việc khác
  → OS: "data đã sẵn sàng" (event/interrupt)
  → Thread (bất kỳ): xử lý data
```

Play/Pekko dùng **Java NIO** và OS-level event notification (epoll trên Linux, kqueue trên macOS) để implement non-blocking I/O.

---

## 4. Khi Nào Blocking Vẫn Tốt Hơn?

Không phải lúc nào async cũng tốt hơn:

| Scenario | Nên dùng |
|----------|---------|
| I/O-bound (DB, HTTP calls, file) | Async ✅ |
| CPU-bound (tính toán nặng, ML inference) | Thread pool riêng (blocking OK) |
| Simple CRUD, ít concurrent | Sync OK, đơn giản hơn |
| Prototyping/MVP | Sync OK, optimize sau |
| Công thức tài chính phức tạp | Sync trên dedicated thread pool |

**Quy tắc thực tế**: Bắt đầu với sync nếu team không quen async. Chuyển sang async khi benchmark cho thấy bottleneck thực sự là thread count.

---

## 5. Play Thread Pools

Play có nhiều thread pool (gọi là "dispatcher" trong Pekko terminology):

```
Pekko default dispatcher
  └─ Xử lý HTTP request routing và non-blocking logic
  └─ Số thread ≈ CPU cores (mặc định)

play-dev-mode dispatcher  (chỉ trong dev)
  └─ Code compilation, asset serving

blocking-io-dispatcher  (bạn phải tạo)
  └─ JDBC blocking queries
  └─ Blocking file I/O
  └─ Legacy sync code

pekko.actor.default-dispatcher  (Pekko actors)
  └─ Background processing
```

> **Ngày mai (Day 09-11) sẽ học cách config và dùng đúng các thread pool này.**

---

## 6. Visualize Với Code Thực Tế

```java
// 1. Sync blocking - BAD trong Play event loop
public Result badSync() {
    // Thread bị block ở đây suốt 1 giây!
    // Không thể phục vụ request khác trong lúc đó
    try { Thread.sleep(1000); } catch (Exception e) {}
    return ok("Done");
}

// 2. Async non-blocking - GOOD
public CompletionStage<Result> goodAsync() {
    return CompletableFuture
        .supplyAsync(() -> {
            // Code này chạy trên thread pool riêng
            // Event loop thread được giải phóng NGAY
            try { Thread.sleep(1000); } catch (Exception e) {}
            return "Done";
        })
        .thenApply(result -> ok(result));
    // Trả về CompletionStage ngay lập tức
    // Event loop thread tự do nhận request tiếp theo!
}
```

---

## 7. Bài Tập Quan Sát

Trong thư mục `thread-model-demo/`, chạy và quan sát:

```bash
cd thread-model-demo
sbt run

# Terminal 1: Quan sát thread count
# (JVisualVM hoặc đơn giản hơn - Activity Monitor → Java process)

# Terminal 2: Test sync (tất cả thread bị block)
# Gửi 20 request đồng thời
for i in $(seq 1 20); do curl http://localhost:9000/sync & done
wait
# Quan sát: request queue lên, response chậm

# Test async
for i in $(seq 1 20); do curl http://localhost:9000/async & done
wait
# Quan sát: tất cả response về gần như cùng lúc
```

**Kết quả mong đợi:**
- Sync: 20 request mất ~20 giây (serialized)
- Async: 20 request mất ~1 giây (parallel)
