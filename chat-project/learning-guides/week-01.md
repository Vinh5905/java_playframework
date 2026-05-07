# 📅 Tuần 1: Nền Tảng + Account Switcher

---

## 1. 🎯 Mục Tiêu Cuối Tuần

**Bạn sẽ thấy trên màn hình:**
- Giao diện chat đẹp (như screenshot) chạy ở `http://localhost:3000`
- Khi vào lần đầu: modal hiện **danh sách tài khoản** để chọn
- Click chọn account "Alice Johnson" → thấy danh sách conversations
- Backend Play chạy ở `http://localhost:9000`, phục vụ API `/api/accounts`

**Demo flow:**
```
1. Mở http://localhost:3000
   → Hiện modal "Chọn tài khoản" với 9 accounts
2. Click "Alice Johnson"
   → Modal đóng, sidebar hiện conversations
   → Header sidebar hiện "Alice Johnson | Tap to switch"
3. Click "Florencio Dorrance" trong danh sách
   → Chat window mở, thấy tin nhắn (mock)
4. Gõ tin + Enter → Tin gửi đi (chưa lưu DB, Week 3)
5. Click header "Alice Johnson" → Modal mở lại để switch account
6. Chọn "Bob Smith" → App reload với góc nhìn Bob
```

---

## 2. 📚 Kiến Thức Lý Thuyết

### 2.1 Play Framework là gì và tại sao khác Spring Boot?

Play Framework là web framework theo mô hình **MVC**, xây trên **Pekko** (reactive toolkit). Điểm khác biệt quan trọng nhất:

| Đặc điểm | Play | Spring Boot |
|----------|------|-------------|
| Threading | Event-loop (ít thread, non-blocking) | Thread-per-request (nhiều thread, blocking) |
| Routing | File `conf/routes` tách biệt | Annotation `@RequestMapping` rải rác |
| Hot reload | Tự động khi có request | Cần DevTools |
| Build tool | sbt | Maven/Gradle |

> **Vì sao dùng Play cho chat app?** Chat là I/O-bound: server chờ user gõ tin. Play xử lý 10,000 kết nối WebSocket với vài thread, còn Spring Boot dễ bị hết thread.

### 2.2 MVC Request Flow trong Play

```
Browser gửi GET /api/accounts
         ↓
[Pekko HTTP Server port 9000]
         ↓
[Filters] (CORS, logging...)
         ↓
[Router] đọc conf/routes → match → AccountController.list()
         ↓
[AccountController.list()] - code của bạn
         ↓
return ok(Json.toJson(accounts))  ← Result object
         ↓
HTTP Response 200 + JSON body
```

### 2.3 Routing trong Play: File-based thay vì Annotation

```
# conf/routes
GET   /api/accounts            controllers.AccountController.list()
POST  /api/accounts/switch/:id controllers.AccountController.switchTo(id: Long)
GET   /api/accounts/current    controllers.AccountController.current()
```

**Tại sao hay hơn annotation?**
- Nhìn 1 file biết toàn bộ API
- Type-safe: Play compile routes, sai sẽ lỗi compile (không phải runtime)
- Reverse routing: generate URL từ controller method, không hardcode string

### 2.4 Dependency Injection với Guice

Play dùng Google Guice để inject dependencies:

```java
// Không cần new AccountRepository(), Guice tự tạo và inject
public class AccountController extends Controller {
    private final AccountRepository repo;

    @Inject  // Guice sẽ inject AccountRepository vào đây
    public AccountController(AccountRepository repo) {
        this.repo = repo;
    }
}
```

**@Singleton** = Guice chỉ tạo 1 instance, inject vào mọi nơi cần → phù hợp cho repository (shared state).

### 2.5 JSON trong Play với Jackson

```java
// Object → JSON (serialize)
List<Account> accounts = repo.findAll();
return ok(Json.toJson(accounts));   // Tự động set Content-Type: application/json

// JSON → Object (deserialize)
JsonNode body = request.body().asJson();
String name = body.get("name").asText();
```

### 2.6 CORS là gì và tại sao cần cho dự án này?

Frontend chạy ở `localhost:3000`, gọi API ở `localhost:9000` → **khác origin** → browser block vì CORS policy.

Play cần return header `Access-Control-Allow-Origin: http://localhost:3000` để browser cho phép.

```hocon
# application.conf
play.filters.cors {
  allowedOrigins = ["http://localhost:3000"]
}
```

---

## 3. 🛠️ Setup Môi Trường

### Bước 3.1: Kiểm tra đã có gì

```bash
# Java 17+
java -version
# Phải thấy: openjdk version "17.x.x" hoặc cao hơn

# sbt
sbt --version
# Phải thấy: sbt script version 1.x.x
```

**Nếu chưa có:**
```bash
# Java 17
brew install openjdk@17
echo 'export JAVA_HOME=/opt/homebrew/opt/openjdk@17' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# sbt
brew install sbt
```

### Bước 3.2: Verify Python cho Frontend

```bash
python3 --version
# Phải thấy: Python 3.x.x (macOS thường đã có)
```

### Bước 3.3: Mở Frontend (ngay bây giờ!)

```bash
cd /path/to/learn_playframework/chat-project/your-project/fe
python3 -m http.server 3000
```

Mở browser: **http://localhost:3000** → Bạn thấy UI chat ngay, với mock data.

---

## 4. 📂 Cấu Trúc File Tuần 1

```
your-project/be/
├── app/
│   ├── controllers/
│   │   └── AccountController.java    ← TẠO MỚI
│   └── models/
│       └── Account.java              ← TẠO MỚI
├── conf/
│   ├── application.conf              ← SỬA (thêm CORS, secret key)
│   └── routes                        ← TẠO MỚI
├── project/
│   ├── build.properties              ← ĐÃ CÓ SẴN
│   └── plugins.sbt                   ← ĐÃ CÓ SẴN
└── build.sbt                         ← ĐÃ CÓ SẴN
```

> **Lưu ý:** `your-project/be/` đã có `build.sbt`, `project/build.properties`, `project/plugins.sbt` sẵn. Bạn chỉ cần tạo các file Java và config.

---

## 5. 👨‍💻 Hướng Dẫn Code Từng Bước

### Bước 5.1: Tạo Model Account

**File:** `your-project/be/app/models/Account.java`

**Mục đích:** Đây là data class đại diện cho 1 tài khoản. Play dùng Jackson để tự động convert Java object ↔ JSON. Không cần getter/setter nếu dùng public fields (đơn giản hơn cho học).

```java
package models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Account = 1 tài khoản trong hệ thống.
 *
 * Tuần 1: Lưu in-memory (Map)
 * Tuần 2: Sẽ refactor → lưu PostgreSQL, thêm passwordHash, createdAt, v.v.
 *
 * Jackson tự convert:
 *   Account(1L, "Alice Johnson", "alice", false)
 *   → {"id":1,"name":"Alice Johnson","username":"alice","isBot":false}
 */
public class Account {

    public Long id;
    public String name;
    public String username;

    // Đổi tên field trong JSON thành camelCase
    @JsonProperty("isBot")
    public boolean isBot;

    // Constructor cho tạo mới
    public Account(Long id, String name, String username, boolean isBot) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.isBot = isBot;
    }

    // Default constructor - Jackson cần để deserialize JSON → Account
    public Account() {}
}
```

**Verify bằng cách nhìn:** File này không có logic gì → không cần test riêng. Sẽ được test khi AccountController trả về JSON.

---

### Bước 5.2: Tạo AccountController

**File:** `your-project/be/app/controllers/AccountController.java`

**Mục đích:** Controller nhận HTTP request và trả response. Tuần 1 dùng in-memory Map thay vì DB. Tuần 2 sẽ inject AccountRepository để query DB.

```java
package controllers;

import models.Account;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Singleton;
import java.util.*;

/**
 * AccountController - Xử lý tất cả HTTP request liên quan đến accounts.
 *
 * Tuần 1: Dùng in-memory Map, không có DB
 * Tuần 2: Inject AccountRepository, đọc từ PostgreSQL
 *
 * @Singleton: Guice tạo 1 instance duy nhất cho toàn app.
 *             Quan trọng vì ACCOUNTS_DB là shared state (Map) - nếu không singleton
 *             thì mỗi request tạo controller mới → Map mới → mất data!
 */
@Singleton
public class AccountController extends Controller {

    // MOCK: In-memory store - sẽ thay bằng AccountRepository ở Tuần 2
    private static final Map<Long, Account> ACCOUNTS_DB = new LinkedHashMap<>();
    private static Long currentAccountId = 1L;

    // Khởi tạo mock data khi class load lần đầu
    static {
        long id = 1;
        ACCOUNTS_DB.put(id, new Account(id++, "Alice Johnson",      "alice",     false));
        ACCOUNTS_DB.put(id, new Account(id++, "Bob Smith",          "bob",       false));
        ACCOUNTS_DB.put(id, new Account(id++, "Elmer Laverty",      "elmer",     false));
        ACCOUNTS_DB.put(id, new Account(id++, "Florencio Dorrance", "florencio", false));
        ACCOUNTS_DB.put(id, new Account(id++, "Lavern Laboy",       "lavern",    false));
        ACCOUNTS_DB.put(id, new Account(id++, "Titus Kitamura",     "titus",     false));
        ACCOUNTS_DB.put(id, new Account(id++, "Geoffrey Mott",      "geoffrey",  false));
        ACCOUNTS_DB.put(id, new Account(id++, "Alfonzo Schuessler", "alfonzo",   false));
        ACCOUNTS_DB.put(id, new Account(id,   "ChatGPT Bot",        "gpt_bot",   true));
    }

    /**
     * GET /api/accounts
     * Trả về danh sách TẤT CẢ accounts.
     * Frontend dùng để hiển thị account switcher.
     */
    public Result list() {
        List<Account> accounts = new ArrayList<>(ACCOUNTS_DB.values());
        return ok(Json.toJson(accounts));
    }

    /**
     * GET /api/accounts/current
     * Trả về account đang "active" (đang switch vào).
     * Frontend dùng để hiện tên user ở sidebar.
     */
    public Result current() {
        Account acc = ACCOUNTS_DB.get(currentAccountId);
        if (acc == null) {
            return notFound(Json.newObject().put("error", "No active account"));
        }
        return ok(Json.toJson(acc));
    }

    /**
     * POST /api/accounts/switch/:id
     * Switch sang account khác.
     *
     * Tại sao không dùng GET? Vì đây là state-changing operation.
     * REST convention: GET = read-only, POST/PUT = mutate state.
     *
     * Tuần 1: Lưu vào biến static (mất khi restart)
     * Tuần 2: Lưu vào DB hoặc session
     */
    public Result switchTo(Long id) {
        if (!ACCOUNTS_DB.containsKey(id)) {
            // 404 Not Found với JSON body (không phải HTML)
            return notFound(Json.newObject().put("error", "Account not found: " + id));
        }

        currentAccountId = id;

        // Trả về account đã switch để frontend không cần gọi thêm /current
        return ok(Json.toJson(ACCOUNTS_DB.get(id)));
    }

    /**
     * GET /health
     * Health check - dùng để verify backend đang chạy.
     * Frontend gọi endpoint này để biết backend online hay chưa.
     */
    public Result health() {
        return ok(Json.newObject()
            .put("status", "UP")
            .put("service", "chat-backend")
            .put("timestamp", System.currentTimeMillis())
        );
    }
}
```

---

### Bước 5.3: Cấu Hình Routes

**File:** `your-project/be/conf/routes`

**Mục đích:** File này là single source of truth cho toàn bộ API. Play compile file này thành Scala class → type-safe.

**Quan trọng - thứ tự route:**
```
# /api/accounts/current phải đứng TRƯỚC /api/accounts/:id
# Vì nếu ngược lại, "current" sẽ match vào :id → lỗi parse Long!
```

```
# conf/routes
# ─────────────────────────────────────────────────────────
# Format: HTTP_METHOD   URL_PATTERN   CONTROLLER.METHOD
# ─────────────────────────────────────────────────────────

# Health check (dùng để test backend đang chạy)
GET     /health                         controllers.AccountController.health()

# ── Accounts API ──────────────────────────────────────────
# QUAN TRỌNG: /current phải ở TRÊN /:id để tránh conflict!
GET     /api/accounts/current           controllers.AccountController.current()
GET     /api/accounts                   controllers.AccountController.list()
POST    /api/accounts/switch/:id        controllers.AccountController.switchTo(id: Long)

# ── Static assets (nếu có)  ───────────────────────────────
# GET     /assets/*file               controllers.Assets.versioned(path="/public", file: Asset)
```

---

### Bước 5.4: Cấu Hình application.conf

**File:** `your-project/be/conf/application.conf`

```hocon
# application.conf - Cấu hình Play Framework
# Format: HOCON (superset của JSON, hỗ trợ comment và environment variables)

# ── Security ──────────────────────────────────────────────
# Secret key dùng để sign session cookie
# Production: đổi thành string ngẫu nhiên >= 32 ký tự + lưu vào env var
play.http.secret.key = "chat-app-dev-secret-key-change-in-production"

# ── Allowed Hosts ──────────────────────────────────────────
play.filters.hosts {
  allowed = ["localhost", "localhost:9000", "127.0.0.1", "127.0.0.1:9000"]
}

# ── CORS - Cho phép Frontend gọi API ──────────────────────
# Vì frontend chạy ở localhost:3000, backend ở localhost:9000 → khác origin
# Browser sẽ block nếu không có CORS header
play.filters.cors {
  allowedOrigins = ["http://localhost:3000", "http://127.0.0.1:3000"]
  allowedHttpMethods = ["GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"]
  allowedHttpHeaders = ["Accept", "Content-Type", "Authorization"]
  preflightMaxAge = 1 hour
}

# ── Tắt CSRF cho REST API ─────────────────────────────────
# REST API dùng JWT (Tuần 8) thay vì session cookie → không cần CSRF
# CSRF chỉ cần khi dùng browser form với session cookie
play.filters.disabled += "play.filters.csrf.CSRFFilter"
```

---

### Bước 5.5: Kiểm Tra Sản Phẩm

**Bước 1:** Chạy backend

```bash
cd your-project/be
sbt run
# Lần đầu: ~5-10 phút download dependencies. Chờ đến khi thấy:
# --- (Running the application, auto-reloading is enabled) ---
```

**Bước 2:** Test bằng curl

```bash
# Health check
curl http://localhost:9000/health
# Kết quả mong đợi:
# {"status":"UP","service":"chat-backend","timestamp":1234567890}

# Lấy danh sách accounts
curl http://localhost:9000/api/accounts
# Kết quả mong đợi:
# [{"id":1,"name":"Alice Johnson","username":"alice","isBot":false},...]

# Lấy current account
curl http://localhost:9000/api/accounts/current
# Kết quả mong đợi: {"id":1,"name":"Alice Johnson",...}

# Switch sang account 4 (Florencio)
curl -X POST http://localhost:9000/api/accounts/switch/4
# Kết quả mong đợi: {"id":4,"name":"Florencio Dorrance",...}

# Verify đã switch
curl http://localhost:9000/api/accounts/current
# Kết quả mong đợi: {"id":4,"name":"Florencio Dorrance",...}

# Test 404
curl http://localhost:9000/api/accounts/switch/999
# Kết quả mong đợi: {"error":"Account not found: 999"}
```

**Bước 3:** Kết nối Frontend với Backend

Mở `your-project/fe/js/config.js`, đổi:
```javascript
USE_MOCK: false,   // ← Đổi từ true sang false
```

Reload browser `http://localhost:3000` → Frontend giờ gọi backend thật!

```bash
# Xem network tab trong DevTools (F12) để confirm:
# GET http://localhost:9000/api/accounts → 200 OK
```

---

## 6. 🔄 Code "Tiến Hóa" Sẽ Xảy Ra Ở Tuần Sau

**Tuần 2:** `AccountController` sẽ inject `AccountRepository`:

```java
// Tuần 1: Static Map (mất data khi restart)
private static final Map<Long, Account> ACCOUNTS_DB = new LinkedHashMap<>();

// Tuần 2: Inject repository (PostgreSQL)
private final AccountRepository accountRepo;

@Inject
public AccountController(AccountRepository accountRepo) {
    this.accountRepo = accountRepo;
}

// Tuần 1: return ok(Json.toJson(new ArrayList<>(ACCOUNTS_DB.values())));
// Tuần 2: return accountRepo.findAll().thenApply(accs -> ok(Json.toJson(accs)));
```

**Sự khác biệt:**
- Tuần 1: `Result` (sync)
- Tuần 2: `CompletionStage<Result>` (async) vì JDBC query là blocking, phải wrap vào thread pool

---

## 7. 🎭 Mock Code Đang Dùng

```java
// Đây là MOCK - sẽ thay bằng DB ở Tuần 2
private static final Map<Long, Account> ACCOUNTS_DB = new LinkedHashMap<>();
private static Long currentAccountId = 1L;

// MOCK: currentAccountId sẽ bị reset mỗi khi restart
// Tuần 2: Lưu vào PostgreSQL, persist qua restart
```

Frontend `config.js`:
```javascript
USE_MOCK: true,  // MOCK: Đổi sang false khi backend chạy
```

---

## 8. ⚠️ Pitfalls & Common Mistakes

**Lỗi 1: `Port 9000 already in use`**
```bash
# Tìm process đang dùng port 9000
lsof -i :9000
# Kill process đó
kill -9 <PID>
# Hoặc chạy Play trên port khác:
sbt "run 9001"
```

**Lỗi 2: Frontend gọi API bị CORS error**
```
Access to fetch at 'http://localhost:9000/api/accounts' from origin
'http://localhost:3000' has been blocked by CORS policy
```
→ Kiểm tra `application.conf` có dòng CORS config chưa.
→ Kiểm tra Play đã restart sau khi sửa conf chưa (Play hot reload với Java, nhưng conf đôi khi cần restart).

**Lỗi 3: `sbt run` lần đầu chậm**
→ Bình thường! sbt download ~500MB dependencies. Lần sau nhanh vì cache.

**Lỗi 4: JSON không đúng format**
```bash
curl http://localhost:9000/api/accounts | python3 -m json.tool
# Nếu lỗi parse → body không phải JSON → kiểm tra controller trả gì
```

**Lỗi 5: Hot reload không hoạt động**
→ Play hot reload khi có **request mới đến**, không phải khi save file.
→ Sửa code → save → refresh browser → Play detect thay đổi → recompile → trả response mới.

**Lỗi 6: `@Singleton` bị quên**
→ Nếu quên `@Singleton` trên `AccountController`, mỗi request tạo 1 instance mới → Map bị reset → gọi `/switch/4` xong gọi `/current` lại thấy account 1!

---

## 9. ✅ Checklist Hoàn Thành Tuần 1

- [ ] `java -version` → 17+
- [ ] `sbt --version` → 1.x
- [ ] Frontend chạy ở `http://localhost:3000` với mock data
- [ ] Account switcher modal hiện khi lần đầu vào
- [ ] Click account → sidebar cập nhật tên user
- [ ] Click conversation → chat window mở, thấy tin nhắn mock
- [ ] Gõ tin + Enter → tin gửi đi, hiện ngay (mock local)
- [ ] Backend chạy: `curl http://localhost:9000/health` → `{"status":"UP",...}`
- [ ] `curl http://localhost:9000/api/accounts` → 9 accounts dạng JSON
- [ ] `curl -X POST http://localhost:9000/api/accounts/switch/4` → Florencio
- [ ] Frontend kết nối backend: đổi `USE_MOCK: false`, accounts load từ API
- [ ] Không có lỗi CORS trong DevTools

---

## 10. 🔗 Kết Nối Với Tuần 2

**Tuần 2 sẽ làm:**
- Cài PostgreSQL (Docker)
- Tạo `accounts` table bằng Play Evolutions
- Viết `AccountRepository` với async JDBC
- Refactor `AccountController` để inject Repository thay vì Map
- Data persist qua restart → switch account Florencio, restart server → vẫn thấy Florencio là current

**Code Tuần 1 cần đổi:**
```java
// Xóa: static Map và static currentAccountId
// Thêm: inject AccountRepository
// Đổi: Result → CompletionStage<Result> (async)
```

**Gợi ý chuẩn bị:** Đọc qua `Day-11-Execution-Contexts/README.md` trong `learn_playframework` để hiểu tại sao cần tách execution context khi dùng JDBC.
