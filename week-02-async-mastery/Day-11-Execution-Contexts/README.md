# Day 11 - Execution Contexts: Thread Pool Management ⭐⭐⭐

## Mục tiêu
- Hiểu tại sao cần tách Execution Context
- Cấu hình custom dispatcher trong application.conf
- Biết chọn đúng dispatcher cho từng loại task

---

## 1. Tại Sao Cần Tách Execution Context?

Play có 1 default Execution Context (EC) để xử lý HTTP requests. Đây là thread pool nhỏ (~ số CPU cores) vì chỉ cần xử lý non-blocking logic.

**Vấn đề**: Nếu bạn chạy blocking code (JDBC, file I/O sync) trên default EC:

```
Default EC: 8 threads
─────────────────────────────────────
Thread 1: [BLOCKED by DB query 2s] ❌
Thread 2: [BLOCKED by DB query 2s] ❌
Thread 3: [BLOCKED by DB query 2s] ❌
Thread 4: [BLOCKED by DB query 2s] ❌
Thread 5: [BLOCKED by DB query 2s] ❌
Thread 6: [BLOCKED by DB query 2s] ❌
Thread 7: [BLOCKED by DB query 2s] ❌
Thread 8: [BLOCKED by DB query 2s] ❌

Request #9 đến: KHÔNG CÒN THREAD!
→ Toàn bộ app bị frozen, kể cả những request không cần DB
```

**Giải pháp**: Tạo separate dispatcher cho blocking code:

```
Default EC: 8 threads   ← Chỉ xử lý non-blocking
─────────────────────────────────────
Thread 1: [route → handler] → giải phóng ngay
Thread 2: [route → handler] → giải phóng ngay
...

Blocking EC: 50 threads  ← Chỉ làm blocking I/O
─────────────────────────────────────
Thread 1: [BLOCKED by DB query] (OK, được phép block)
Thread 2: [BLOCKED by DB query] (OK)
...
Thread 50: [BLOCKED] (OK)

Request #51 đến: Default EC vẫn free → nhận request ngay!
```

---

## 2. Cấu Hình Dispatcher

```hocon
# conf/application.conf

# Dispatcher cho blocking I/O (JDBC, file I/O legacy, v.v.)
blocking-io-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    # Số thread cố định
    # Công thức: số kết nối DB pool (thường 10-50)
    fixed-pool-size = 20
  }
  throughput = 1  # Xử lý 1 message rồi yield thread (công bằng)
}

# Dispatcher cho CPU-bound tasks (tính toán nặng, xử lý ảnh, v.v.)
cpu-bound-dispatcher {
  type = Dispatcher
  executor = "fork-join-executor"
  fork-join-executor {
    # Số thread = CPU cores (ví dụ 8 core = 8 thread)
    parallelism-min = 2
    parallelism-factor = 1.0  # threads = cores * factor
    parallelism-max = 8
  }
  throughput = 100  # CPU task → xử lý nhiều hơn trước khi yield
}

# Dispatcher cho background jobs (email, notifications)
background-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 5  # Ít thread vì priority thấp
  }
  throughput = 5
}
```

---

## 3. Dùng Custom Dispatcher Trong Java Code

### Cách 1: Inject qua ActorSystem (Pekko native)

```java
import org.apache.pekko.actor.ActorSystem;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;

@Singleton
public class UserRepository {

    private final ExecutionContext blockingEc;
    private final Database db;

    @Inject
    public UserRepository(Database db, ActorSystem actorSystem) {
        this.db = db;
        // Lấy dispatcher đã config trong application.conf
        this.blockingEc = actorSystem.dispatchers().lookup("blocking-io-dispatcher");
    }

    public CompletionStage<List<User>> findAll() {
        return CompletableFuture.supplyAsync(
            () -> {
                // Blocking JDBC - chạy trên blockingEc, không phải default EC!
                return db.withConnection(conn -> {
                    ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM users");
                    List<User> users = new ArrayList<>();
                    while (rs.next()) {
                        users.add(new User(rs.getLong("id"), rs.getString("name")));
                    }
                    return users;
                });
            },
            (ExecutionContextExecutor) blockingEc  // ← Quan trọng!
        );
    }
}
```

### Cách 2: Play's HttpExecutionContext (đảm bảo HTTP context)

```java
import play.libs.concurrent.HttpExecutionContext;

@Singleton
public class SomeService {
    private final HttpExecutionContext httpEc;

    @Inject
    public SomeService(HttpExecutionContext httpEc) {
        this.httpEc = httpEc;
    }

    public CompletionStage<String> processWithHttpContext() {
        return CompletableFuture.supplyAsync(
            () -> "result",
            httpEc.current()  // Đảm bảo HTTP context được carry over
        );
    }
}
```

### Cách 3: Inject ExecutorService (Java style)

```java
@Singleton
public class LegacyIntegration {
    private final ExecutorService executor;

    @Inject
    public LegacyIntegration(ActorSystem system) {
        // Convert Pekko dispatcher sang Java ExecutorService
        scala.concurrent.ExecutionContext ec =
            system.dispatchers().lookup("blocking-io-dispatcher");
        this.executor = new ExecutorServiceDelegate(
            scala.concurrent.ExecutionContext.Implicits$.MODULE$.global()
        );
    }
}
```

---

## 4. Play Database API Với Custom EC

```java
import play.db.Database;
import org.apache.pekko.actor.ActorSystem;

@Singleton
public class ProductRepository {
    private final Database db;
    private final scala.concurrent.ExecutionContext dbEc;

    @Inject
    public ProductRepository(Database db, ActorSystem system) {
        this.db = db;
        this.dbEc = system.dispatchers().lookup("blocking-io-dispatcher");
    }

    public CompletionStage<List<Product>> findAll() {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                List<Product> products = new ArrayList<>();
                ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT id, name, price FROM products");
                while (rs.next()) {
                    products.add(new Product(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getBigDecimal("price")
                    ));
                }
                return products;
            }),
            (java.util.concurrent.Executor) dbEc
        );
    }
}
```

---

## 5. Chọn Loại Dispatcher Nào?

| Task Type | Dispatcher | Lý do |
|-----------|-----------|-------|
| Non-blocking I/O (async HTTP, reactive DB) | Default EC | Ít thread, hiệu quả |
| Blocking I/O (JDBC, blocking file I/O) | `thread-pool-executor` fixed | Dự đoán được số thread |
| CPU-bound (tính toán, image processing) | `fork-join-executor` | Work-stealing, tận dụng cores |
| Background jobs (email, notifications) | Separate `thread-pool-executor` | Không ảnh hưởng request |
| Pekko actors | Default actor dispatcher | Actors thiết kế cho non-blocking |

---

## 6. Connection Pool Sizing

Số thread của blocking-io-dispatcher phải **match** với database connection pool size:

```hocon
blocking-io-dispatcher {
  thread-pool-executor {
    fixed-pool-size = 20  # ← Phải bằng maximumPoolSize của HikariCP
  }
}

db.default.hikaricp {
  maximumPoolSize = 20  # ← Bằng với thread pool
  minimumIdle = 5
}
```

**Lý do**: Nếu dispatcher có 20 threads nhưng HikariCP chỉ có 10 connections → 10 threads phải đợi connection → thread pool 50% idle không hiệu quả.

---

## 7. Monitoring và Debugging

```java
// Xem thread nào đang xử lý request
public Result debug() {
    String threadName = Thread.currentThread().getName();
    // Nếu thread tên là "application-akka.actor.default-dispatcher-X" → default EC
    // Nếu tên là "blocking-io-dispatcher-X" → đúng dispatcher
    return ok("Running on thread: " + threadName);
}
```

**Log dispatcher trong development:**
```hocon
# application.conf
pekko {
  actor {
    debug {
      lifecycle = on
      unhandled = on
    }
  }
}
```

---

## 8. Bài Tập

Xem `execution-contexts-demo/` trong thư mục này.

Project có 3 endpoint để quan sát:

1. `/wrong` - chạy blocking code trên default EC → bad
2. `/right` - chạy blocking code trên custom EC → good
3. `/thread-name` - trả về tên thread đang xử lý

```bash
cd execution-contexts-demo
sbt run

# Quan sát thread names
curl http://localhost:9000/thread-name/default
curl http://localhost:9000/thread-name/blocking

# Load test để thấy sự khác biệt
ab -n 100 -c 50 http://localhost:9000/wrong
ab -n 100 -c 50 http://localhost:9000/right
```
