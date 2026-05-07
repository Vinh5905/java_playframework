# 📅 Tuần 2: PostgreSQL + User Persistence

---

## 1. 🎯 Mục Tiêu Cuối Tuần

**Thấy gì trên màn hình:**
- Accounts load từ PostgreSQL thật (không phải Map)
- Restart backend → switch account vẫn persist
- Thêm account mới qua curl → xuất hiện ngay trong switcher

**Demo flow:**
```
1. Switch sang "Florencio" → curl /api/accounts/current → {"id":4,"name":"Florencio"}
2. Ctrl+C dừng backend → sbt run lại
3. curl /api/accounts/current → VẪN thấy Florencio (đã lưu DB)
```

---

## 2. 📚 Kiến Thức Lý Thuyết

### 2.1 Tại Sao Cần Tách Execution Context Cho JDBC?

Play có 1 default thread pool (event loop, ~8 threads). Nếu bạn chạy JDBC blocking trên đó:

```
Default EC (8 threads)
Thread 1: [BLOCKED đợi PostgreSQL 2s] ❌
Thread 2: [BLOCKED đợi PostgreSQL 2s] ❌
...
Thread 8: [BLOCKED đợi PostgreSQL 2s] ❌
Request thứ 9 đến: KHÔNG CÒN THREAD → app freeze!
```

**Giải pháp:** Tạo thread pool riêng cho DB queries:

```
Default EC (8 threads)   ← Chỉ xử lý routing, business logic
Blocking DB EC (10 threads) ← Chỉ làm JDBC queries
```

### 2.2 Play Evolutions - Migration Tool Tích Hợp

Play Evolutions = database migration có sẵn trong Play. Tạo file SQL đánh số thứ tự, Play tự apply khi startup.

```
conf/evolutions/default/
├── 1.sql   ← accounts table
├── 2.sql   ← conversations table (Tuần 3)
└── 3.sql   ← settings table (Tuần 8)
```

### 2.3 HikariCP Connection Pool

Play dùng HikariCP (nhanh nhất) để quản lý DB connections.

```hocon
db.default.hikaricp {
  maximumPoolSize = 10   # Phải bằng fixed-pool-size của blocking dispatcher
  minimumIdle = 2
}
```

**Sai lầm hay gặp:** Pool size quá lớn → PostgreSQL overwhelmed. Công thức: `(cores * 2) + 1`.

---

## 3. 🛠️ Setup Môi Trường

```bash
# Cài Docker Desktop nếu chưa có
brew install --cask docker

# Start PostgreSQL container
docker run -d \
  --name chat-postgres \
  -e POSTGRES_USER=chatuser \
  -e POSTGRES_PASSWORD=chatpass \
  -e POSTGRES_DB=chatapp \
  -p 5432:5432 \
  postgres:16-alpine

# Verify đang chạy
docker ps
# Phải thấy: chat-postgres ... Up

# Kết nối thử
docker exec -it chat-postgres psql -U chatuser -d chatapp
# Gõ \q để thoát
```

---

## 4. 📂 Cấu Trúc File Tuần 2

```
your-project/be/
├── app/
│   ├── controllers/
│   │   └── AccountController.java    ← SỬA: inject repo, CompletionStage
│   ├── models/
│   │   └── Account.java              ← SỬA: thêm createdAt
│   └── repositories/
│       └── AccountRepository.java    ← TẠO MỚI ← QUAN TRỌNG NHẤT
├── conf/
│   ├── application.conf              ← SỬA: thêm DB config
│   ├── routes                        ← SỬA: thêm route tạo account
│   └── evolutions/default/
│       └── 1.sql                     ← TẠO MỚI: schema
└── build.sbt                         ← SỬA: thêm jdbc, evolutions, postgresql
```

---

## 5. 👨‍💻 Hướng Dẫn Code Từng Bước

### Bước 5.1: Cập nhật build.sbt

**File:** `your-project/be/build.sbt`

```scala
name := """chat-backend"""
organization := "com.example"
version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.16"

libraryDependencies ++= Seq(
  guice,
  jdbc,        // ← MỚI: Play JDBC support
  evolutions,  // ← MỚI: Play Evolutions (migrations)

  // PostgreSQL JDBC driver
  "org.postgresql" % "postgresql" % "42.7.4",  // ← MỚI

  // Test
  "org.junit.jupiter" % "junit-jupiter-api" % "5.10.2" % Test
)
```

### Bước 5.2: Tạo Migration SQL

**File:** `your-project/be/conf/evolutions/default/1.sql`

**Mục đích:** Play Evolutions đọc file này và tạo table khi app start.

```sql
-- !Ups    ← Section này chạy khi APPLY migration
CREATE TABLE accounts (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    username     VARCHAR(100) NOT NULL UNIQUE,
    is_bot       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed data - 9 accounts ban đầu
INSERT INTO accounts (name, username, is_bot) VALUES
    ('Alice Johnson',      'alice',     false),
    ('Bob Smith',          'bob',       false),
    ('Elmer Laverty',      'elmer',     false),
    ('Florencio Dorrance', 'florencio', false),
    ('Lavern Laboy',       'lavern',    false),
    ('Titus Kitamura',     'titus',     false),
    ('Geoffrey Mott',      'geoffrey',  false),
    ('Alfonzo Schuessler', 'alfonzo',   false),
    ('ChatGPT Bot',        'gpt_bot',   true);

-- Table lưu current account (đơn giản hơn là session)
CREATE TABLE app_state (
    key   VARCHAR(100) PRIMARY KEY,
    value VARCHAR(1000)
);
INSERT INTO app_state (key, value) VALUES ('current_account_id', '1');

-- !Downs  ← Section này chạy khi ROLLBACK migration
DROP TABLE IF EXISTS app_state;
DROP TABLE IF EXISTS accounts;
```

### Bước 5.3: Cập nhật application.conf

```hocon
play.http.secret.key = "chat-app-dev-secret-key"

play.filters.hosts.allowed = ["localhost", "localhost:9000", "127.0.0.1"]
play.filters.cors {
  allowedOrigins = ["http://localhost:3000"]
  allowedHttpMethods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
  allowedHttpHeaders = ["Accept", "Content-Type", "Authorization"]
}
play.filters.disabled += "play.filters.csrf.CSRFFilter"

# ── Database ──────────────────────────────────────────────────
db {
  default {
    driver   = "org.postgresql.Driver"
    url      = "jdbc:postgresql://localhost:5432/chatapp"
    username = "chatuser"
    password = "chatpass"

    hikaricp {
      maximumPoolSize = 10  # Phải bằng fixed-pool-size của blocking-db-dispatcher
      minimumIdle     = 2
      connectionTimeout = 30000
    }
  }
}

# ── Evolutions ──────────────────────────────────────────────────
play.evolutions {
  db.default.enabled  = true
  db.default.autoApply = true  # CHỈ dev! Production: tắt, apply thủ công
}

# ── Custom Thread Pool cho JDBC blocking ─────────────────────
# Không để JDBC chạy trên default EC (event loop)!
blocking-db-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 10  # Bằng với maximumPoolSize ở trên
  }
  throughput = 1
}
```

### Bước 5.4: Tạo AccountRepository

**File:** `your-project/be/app/repositories/AccountRepository.java`

**Mục đích:** Tách DB logic ra khỏi Controller. Controller chỉ lo HTTP, Repository lo SQL.

```java
package repositories;

import models.Account;
import org.apache.pekko.actor.ActorSystem;
import play.db.Database;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class AccountRepository {

    private final Database db;
    private final ExecutionContext dbEc;  // Thread pool riêng cho JDBC

    @Inject
    public AccountRepository(Database db, ActorSystem actorSystem) {
        this.db = db;
        // Lấy custom dispatcher đã cấu hình trong application.conf
        this.dbEc = actorSystem.dispatchers().lookup("blocking-db-dispatcher");
    }

    /**
     * Lấy tất cả accounts.
     * Trả về CompletionStage vì JDBC là blocking → wrap vào thread pool riêng.
     */
    public CompletionStage<List<Account>> findAll() {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                // db.withConnection tự đóng connection sau khi lambda xong
                List<Account> accounts = new ArrayList<>();
                String sql = "SELECT id, name, username, is_bot FROM accounts ORDER BY id";

                try (var ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        accounts.add(mapRow(rs));
                    }
                }
                return accounts;
            }),
            (ExecutionContextExecutor) dbEc  // Chạy trên blocking EC, không phải default EC
        );
    }

    public CompletionStage<Optional<Account>> findById(Long id) {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                String sql = "SELECT id, name, username, is_bot FROM accounts WHERE id = ?";
                try (var ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, id);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) return Optional.of(mapRow(rs));
                    return Optional.<Account>empty();
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    /** Lấy current_account_id từ app_state table */
    public CompletionStage<Long> getCurrentAccountId() {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                String sql = "SELECT value FROM app_state WHERE key = 'current_account_id'";
                try (var ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Long.parseLong(rs.getString("value"));
                    return 1L;
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    /** Lưu current_account_id vào app_state table */
    public CompletionStage<Void> setCurrentAccountId(Long accountId) {
        return CompletableFuture.runAsync(
            () -> db.withTransaction(conn -> {
                String sql = "UPDATE app_state SET value = ? WHERE key = 'current_account_id'";
                try (var ps = conn.prepareStatement(sql)) {
                    ps.setString(1, accountId.toString());
                    ps.executeUpdate();
                    return null;
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    private Account mapRow(ResultSet rs) throws Exception {
        return new Account(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("username"),
            rs.getBoolean("is_bot")
        );
    }
}
```

### Bước 5.5: Refactor AccountController

**File:** `your-project/be/app/controllers/AccountController.java`

```java
package controllers;

import models.Account;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import repositories.AccountRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletionStage;

@Singleton
public class AccountController extends Controller {

    private final AccountRepository accountRepo;

    @Inject
    public AccountController(AccountRepository accountRepo) {
        this.accountRepo = accountRepo;
        // Không còn static Map! Tất cả qua DB
    }

    // Return type đổi sang CompletionStage<Result> (async)
    public CompletionStage<Result> list() {
        return accountRepo.findAll()
            .thenApply(accounts -> ok(Json.toJson(accounts)))
            .exceptionally(t -> internalServerError(
                Json.newObject().put("error", "DB error: " + t.getMessage())
            ));
    }

    public CompletionStage<Result> current() {
        return accountRepo.getCurrentAccountId()
            .thenCompose(accountRepo::findById)
            .thenApply(opt -> opt
                .map(acc -> ok(Json.toJson(acc)))
                .orElse(notFound(Json.newObject().put("error", "Account not found")))
            );
    }

    public CompletionStage<Result> switchTo(Long id) {
        return accountRepo.findById(id)
            .thenCompose(opt -> {
                if (opt.isEmpty()) {
                    return java.util.concurrent.CompletableFuture.completedFuture(
                        notFound(Json.newObject().put("error", "Account not found: " + id))
                    );
                }
                Account acc = opt.get();
                return accountRepo.setCurrentAccountId(id)
                    .thenApply(v -> ok(Json.toJson(acc)));
            })
            .exceptionally(t -> internalServerError(
                Json.newObject().put("error", t.getMessage())
            ));
    }

    public Result health() {
        return ok(Json.newObject().put("status", "UP").put("db", "connected"));
    }
}
```

### Bước 5.6: Cập nhật Routes

```
# conf/routes - Thêm route tạo account
GET     /health                         controllers.AccountController.health()
GET     /api/accounts/current           controllers.AccountController.current()
GET     /api/accounts                   controllers.AccountController.list()
POST    /api/accounts/switch/:id        controllers.AccountController.switchTo(id: Long)
```

---

## 6. 🔄 Sự Tiến Hóa Code Tuần 1 → Tuần 2

| | Tuần 1 | Tuần 2 |
|--|--------|--------|
| Data storage | `static Map<Long, Account>` | PostgreSQL via JDBC |
| Return type | `Result` | `CompletionStage<Result>` |
| Threading | Default EC (sai!) | Custom blocking EC |
| Persistence | Mất khi restart | Persist qua restart |
| Error handling | Không có | `exceptionally()` |

---

## 7. 🎭 Mock Code Còn Lại

```java
// Conversations vẫn là MOCK - sẽ thay bằng MongoDB ở Tuần 3
// AccountController.health() trả "db: connected" nhưng chưa ping thật
```

---

## 8. ⚠️ Pitfalls Tuần 2

**Lỗi 1: Evolution checksum mismatch**
```
Oops, there were evolution problems. Database '[default]' needs evolution!
```
→ Bạn đã chạy evolution rồi sửa file SQL → Play detect checksum mismatch
→ Fix: Xóa table `play_evolutions` trong PostgreSQL và chạy lại, hoặc tạo file `2.sql` mới

**Lỗi 2: HikariCP pool exhausted**
```
Connection is not available, request timed out after 30000ms
```
→ `maximumPoolSize` quá nhỏ hoặc `fixed-pool-size` của dispatcher không match

**Lỗi 3: Quên cast ExecutionContext**
```
(ExecutionContextExecutor) dbEc  // ← Phải có cast này
```
Java CompletableFuture cần `java.util.concurrent.Executor`, không phải Scala `ExecutionContext`

---

## 9. ✅ Checklist Tuần 2

- [ ] Docker chạy: `docker ps` thấy `chat-postgres`
- [ ] `sbt run` không lỗi evolution
- [ ] `curl http://localhost:9000/api/accounts` → data từ PostgreSQL
- [ ] Switch Florencio → restart backend → `/current` vẫn trả Florencio
- [ ] Log không thấy DB queries chạy trên `application-akka.actor.default-dispatcher`
  _(Phải thấy `blocking-db-dispatcher`)_

---

## 10. 🔗 Kết Nối Tuần 3

Tuần 3 thêm MongoDB cho messages. Tại sao MongoDB thay vì PostgreSQL?
- Messages là **append-only log** → NoSQL document phù hợp hơn relational
- Schema messages linh hoạt (text, image, file, reaction...)
- MongoDB có aggregation pipeline mạnh cho analytics
