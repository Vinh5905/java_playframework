# 📅 Tuần 8: Settings + Production Build + Docker

---

## 1. 🎯 Mục Tiêu Cuối Tuần

**Thấy gì trên màn hình:**
- Panel Settings với các toggle (bật/tắt tính năng)
- Tắt typing indicator → bên kia không còn thấy "... is typing"
- App chạy được trong Docker container

**Demo flow:**
```
1. Mở Settings panel
2. Toggle "Typing Indicators" → OFF
3. Gõ tin → bên kia KHÔNG thấy "typing..."
4. Toggle lại → ON → hoạt động lại
5. sbt docker:publishLocal → chạy docker-compose up → app live
```

---

## 2. 📚 Kiến Thức Lý Thuyết

### 2.1 Settings Architecture

Settings được lưu per-user trong PostgreSQL. Mỗi khi WebSocket event xảy ra, backend kiểm tra settings trước khi broadcast.

```
Alice bật typing indicator
    ↓
Alice gõ tin → frontend gửi {type: "typing", isTyping: true}
    ↓
ChatRoomActor nhận
    ↓ check settings: typingIndicators = true?
    ↓ YES → broadcast typing event đến Florencio
    ↓ NO  → skip (không gửi gì)
```

### 2.2 Play Cache API

Settings thay đổi ít → cache để tránh DB hit mỗi message:

```java
@Inject AsyncCacheApi cache;

// Get với cache (TTL 5 phút)
cache.getOrElseUpdate("settings:" + userId,
    () -> settingsRepo.findByUserId(userId),
    300  // seconds
);

// Invalidate khi update
cache.remove("settings:" + userId);
```

### 2.3 Production Build: `sbt dist` vs `sbt docker:publishLocal`

```bash
# sbt dist → tạo .zip chứa start script + jars
sbt dist
# → target/universal/chat-backend-1.0.zip

# sbt docker:publishLocal → tạo Docker image trực tiếp
sbt docker:publishLocal
# → Local image: chat-backend:1.0-SNAPSHOT
```

### 2.4 Production Config vs Dev Config

```
Dev:   play.evolutions.autoApply = true (nguy hiểm ở production!)
Prod:  play.evolutions.autoApply = false → manual migration
Dev:   Secret key = hardcoded "changeme"
Prod:  Secret key = env var ${APP_SECRET}
Dev:   CORS = localhost:3000
Prod:  CORS = your-domain.com
```

---

## 3. 🛠️ Setup Production Tools

```bash
# Docker Desktop đã có từ Tuần 2

# Verify docker-compose
docker compose version
# Docker Compose version v2.x.x
```

---

## 4. 📂 Cấu Trúc File Tuần 8

```
your-project/be/
├── app/
│   ├── controllers/
│   │   └── SettingsController.java   ← TẠO MỚI
│   ├── models/
│   │   └── Settings.java             ← TẠO MỚI
│   └── repositories/
│       └── SettingsRepository.java   ← TẠO MỚI
├── conf/
│   ├── application.conf              ← SỬA: thêm cache config
│   ├── prod.conf                     ← TẠO MỚI: production config
│   ├── routes                        ← SỬA: thêm settings routes
│   └── evolutions/default/
│       └── 3.sql                     ← TẠO MỚI: settings table
├── Dockerfile                        ← TẠO MỚI
└── docker-compose.yml                ← TẠO MỚI (ở root your-project/)
```

---

## 5. 👨‍💻 Hướng Dẫn Code

### Bước 5.1: Settings Model

```java
// app/models/Settings.java
package models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Settings {
    @JsonProperty("user_id")
    public Long userId;

    @JsonProperty("typing_indicators")
    public boolean typingIndicators = true;

    @JsonProperty("show_online_status")
    public boolean showOnlineStatus = true;

    @JsonProperty("notifications")
    public boolean notifications = true;

    @JsonProperty("sound_enabled")
    public boolean soundEnabled = true;

    public Settings() {}
    public Settings(Long userId) {
        this.userId = userId;
        // Default values: all enabled
    }
}
```

### Bước 5.2: Evolution Settings Table

```sql
-- conf/evolutions/default/3.sql

-- !Ups
CREATE TABLE user_settings (
    user_id            BIGINT PRIMARY KEY REFERENCES accounts(id),
    typing_indicators  BOOLEAN NOT NULL DEFAULT TRUE,
    show_online_status BOOLEAN NOT NULL DEFAULT TRUE,
    notifications      BOOLEAN NOT NULL DEFAULT TRUE,
    sound_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Default settings cho tất cả accounts hiện có
INSERT INTO user_settings (user_id)
SELECT id FROM accounts;

-- !Downs
DROP TABLE IF EXISTS user_settings;
```

### Bước 5.3: SettingsRepository

```java
// app/repositories/SettingsRepository.java
package repositories;

import models.Settings;
import org.apache.pekko.actor.ActorSystem;
import play.db.Database;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class SettingsRepository {

    private final Database db;
    private final ExecutionContext dbEc;

    @Inject
    public SettingsRepository(Database db, ActorSystem system) {
        this.db = db;
        this.dbEc = system.dispatchers().lookup("blocking-db-dispatcher");
    }

    public CompletionStage<Settings> findByUserId(Long userId) {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                String sql = """
                    SELECT user_id, typing_indicators, show_online_status,
                           notifications, sound_enabled
                    FROM user_settings WHERE user_id = ?
                    """;
                try (var ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, userId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        Settings s = new Settings();
                        s.userId = rs.getLong("user_id");
                        s.typingIndicators = rs.getBoolean("typing_indicators");
                        s.showOnlineStatus = rs.getBoolean("show_online_status");
                        s.notifications = rs.getBoolean("notifications");
                        s.soundEnabled = rs.getBoolean("sound_enabled");
                        return s;
                    }
                    return new Settings(userId);  // Default nếu chưa có row
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    public CompletionStage<Void> updateSetting(Long userId, String key, boolean value) {
        // Whitelist để tránh SQL injection
        String column = switch (key) {
            case "typing_indicators"  -> "typing_indicators";
            case "show_online_status" -> "show_online_status";
            case "notifications"      -> "notifications";
            case "sound_enabled"      -> "sound_enabled";
            default -> throw new IllegalArgumentException("Unknown setting: " + key);
        };

        return CompletableFuture.runAsync(
            () -> db.withTransaction(conn -> {
                String sql = "UPDATE user_settings SET " + column + " = ?, updated_at = NOW() WHERE user_id = ?";
                try (var ps = conn.prepareStatement(sql)) {
                    ps.setBoolean(1, value);
                    ps.setLong(2, userId);
                    if (ps.executeUpdate() == 0) {
                        // Insert nếu chưa có row (upsert)
                        String insert = "INSERT INTO user_settings (user_id, " + column + ") VALUES (?, ?) ON CONFLICT (user_id) DO NOTHING";
                        try (var ps2 = conn.prepareStatement(insert)) {
                            ps2.setLong(1, userId);
                            ps2.setBoolean(2, value);
                            ps2.executeUpdate();
                        }
                    }
                    return null;
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }
}
```

### Bước 5.4: SettingsController

```java
// app/controllers/SettingsController.java
package controllers;

import models.Settings;
import play.cache.AsyncCacheApi;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import repositories.SettingsRepository;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

public class SettingsController extends Controller {

    private final SettingsRepository settingsRepo;
    private final AsyncCacheApi cache;

    @Inject
    public SettingsController(SettingsRepository settingsRepo, AsyncCacheApi cache) {
        this.settingsRepo = settingsRepo;
        this.cache = cache;
    }

    // GET /api/settings?userId=1
    public CompletionStage<Result> getSettings(Long userId) {
        return cache.getOrElseUpdate(
            "settings:" + userId,
            () -> settingsRepo.findByUserId(userId),
            300  // Cache 5 phút
        ).thenApply(s -> ok(Json.toJson(s)));
    }

    // PATCH /api/settings?userId=1
    // Body: {"key": "typing_indicators", "value": false}
    public CompletionStage<Result> updateSetting(Long userId, Http.Request request) {
        var body = request.body().asJson();
        if (body == null) {
            return error400("JSON body required");
        }

        String key = body.path("key").asText();
        boolean value = body.path("value").asBoolean();

        return settingsRepo.updateSetting(userId, key, value)
            .thenCompose(v -> {
                // Invalidate cache sau khi update
                return cache.remove("settings:" + userId);
            })
            .thenApply(v -> ok(Json.newObject()
                .put("success", true)
                .put("key", key)
                .put("value", value)
            ))
            .exceptionally(t -> {
                if (t.getCause() instanceof IllegalArgumentException) {
                    return badRequest(Json.newObject().put("error", t.getCause().getMessage()));
                }
                return internalServerError(Json.newObject().put("error", t.getMessage()));
            });
    }

    private CompletionStage<Result> error400(String msg) {
        return java.util.concurrent.CompletableFuture.completedFuture(
            badRequest(Json.newObject().put("error", msg))
        );
    }
}
```

### Bước 5.5: Tích Hợp Settings Vào ChatRoomActor

```java
// ChatRoomActor.java - inject SettingsRepository:
private final SettingsRepository settingsRepo;
// ... inject qua constructor ...

// Trong handleTyping():
private void handleTyping(TypingEvent event) {
    // Kiểm tra settings của RECIPIENT trước khi gửi
    // (không check sender - sender muốn gửi, nhưng recipient có thể tắt)
    // Tìm recipient của conversation...
    // Đơn giản: broadcast cho tất cả và để client filter

    // Thực ra: check settings của sender
    settingsRepo.findByUserId(event.userId)
        .thenAccept(settings -> {
            if (!settings.typingIndicators) return;  // Sender đã tắt → không gửi

            var payload = Json.newObject()
                .put("type", "typing")
                .put("convId", event.convId)
                .put("userId", event.userId)
                .put("isTyping", event.isTyping);

            connectedUsers.forEach((uid, actor) -> {
                if (!uid.equals(event.userId)) {
                    actor.tell(payload, self());
                }
            });
        });
}
```

### Bước 5.6: Routes mới

```
# conf/routes
GET     /api/settings                   controllers.SettingsController.getSettings(userId: Long)
PATCH   /api/settings                   controllers.SettingsController.updateSetting(userId: Long, request: Request)
```

### Bước 5.7: Cache Config

```hocon
# application.conf
play.modules.enabled += "play.api.cache.ehcache.EhCacheModule"
```

```scala
// build.sbt
libraryDependencies += ehcache
```

---

## 6. 📦 Production Build

### Bước 6.1: Production Config File

```hocon
# conf/prod.conf
include "application.conf"

# Override cho production
play.http.secret.key = ${APP_SECRET}
play.evolutions.db.default.autoApply = false

play.filters.hosts.allowed = ["your-domain.com", "www.your-domain.com"]
play.filters.cors.allowedOrigins = ["https://your-domain.com"]
```

### Bước 6.2: Dockerfile

```dockerfile
# Dockerfile (để ở your-project/be/)

# Stage 1: Build
FROM sbtscala/scala-sbt:eclipse-temurin-17.0.10_7_1.10.0_2.13.16 AS builder
WORKDIR /build
COPY . .
RUN sbt clean stage

# Stage 2: Runtime (nhỏ hơn nhiều - không cần sbt)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /build/target/universal/stage .

# Non-root user vì security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 9000
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

CMD ["bin/chat-backend", "-Dconfig.file=conf/prod.conf"]
```

### Bước 6.3: docker-compose.yml

```yaml
# your-project/docker-compose.yml
version: '3.8'

services:
  backend:
    build: ./be
    ports:
      - "9000:9000"
    environment:
      - APP_SECRET=your-very-long-random-secret-key-here-min-32-chars
      - DATABASE_URL=jdbc:postgresql://postgres:5432/chatapp
      - DATABASE_USER=chatuser
      - DATABASE_PASSWORD=chatpass
      - MONGODB_URI=mongodb://chatuser:chatpass@mongo:27017/chatapp?authSource=admin
      - OPENAI_API_KEY=${OPENAI_API_KEY}  # Đọc từ shell env
    depends_on:
      postgres:
        condition: service_healthy
      mongo:
        condition: service_healthy
    restart: unless-stopped

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: chatuser
      POSTGRES_PASSWORD: chatpass
      POSTGRES_DB: chatapp
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U chatuser -d chatapp"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  mongo:
    image: mongo:7
    environment:
      MONGO_INITDB_ROOT_USERNAME: chatuser
      MONGO_INITDB_ROOT_PASSWORD: chatpass
      MONGO_INITDB_DATABASE: chatapp
    volumes:
      - mongo_data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--quiet", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  frontend:
    image: nginx:alpine
    ports:
      - "3000:80"
    volumes:
      - ./fe:/usr/share/nginx/html:ro
    restart: unless-stopped

volumes:
  postgres_data:
  mongo_data:
```

### Bước 6.4: Build và Chạy

```bash
# Build Docker image
cd your-project/be
sbt docker:publishLocal
# → image: chat-backend:1.0-SNAPSHOT

# Hoặc dùng Dockerfile:
docker build -t chat-backend .

# Chạy toàn bộ stack
cd your-project
export OPENAI_API_KEY="sk-proj-your-key"
docker compose up -d

# Xem logs
docker compose logs -f backend

# Test
curl http://localhost:9000/health

# Stop
docker compose down

# Stop + xóa data (cẩn thận!)
docker compose down -v
```

### Bước 6.5: Nginx Config Cho Frontend (Production)

```nginx
# Tạo your-project/fe/nginx.conf:
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # Serve static files
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy API requests đến backend
    # Thay vì cần CORS, Nginx forward request
    location /api/ {
        proxy_pass http://backend:9000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Proxy WebSocket
    location /ws/ {
        proxy_pass http://backend:9000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## 7. 🎭 Final Settings: Các Toggle Hợp Lý

| Setting | Mô tả | Default |
|---------|-------|---------|
| `typing_indicators` | Hiện "... is typing" | ON |
| `show_online_status` | Hiện dot xanh/xám | ON |
| `notifications` | Browser notification | ON |
| `sound_enabled` | Âm thanh khi nhận tin | ON |
| `read_receipts` | Hiện "Seen" | ON |
| `message_preview` | Hiện nội dung trong notification | ON |

---

## 8. ⚠️ Pitfalls Tuần 8

**Docker build chậm lần đầu:** sbt trong Docker cần download dependencies. Tạo `.dockerignore`:
```
target/
.git/
*.log
```
Và thêm layer caching vào Dockerfile (copy build.sbt trước, chỉ copy source sau).

**Evolution trong Docker:**
```hocon
# prod.conf
play.evolutions.db.default.autoApply = false  # Phải apply thủ công!
```
Apply migration khi deploy:
```bash
# Chạy container với flag đặc biệt để apply evolution
docker run --rm chat-backend bin/chat-backend -Dplay.evolutions.db.default.autoApply=true
```

**Secret key trong docker-compose**: Không dùng `.env` file nếu commit lên git. Dùng Docker Secrets hoặc vault.

---

## 9. ✅ Checklist Tuần 8 (Final)

**Settings:**
- [ ] Toggle typing indicator → tắt → không thấy "... is typing"
- [ ] Toggle online status → tắt → users khác thấy mình offline
- [ ] Settings persist qua refresh (từ DB)

**Production:**
- [ ] `docker compose up -d` → tất cả services chạy
- [ ] `curl http://localhost:9000/health` từ docker network
- [ ] Frontend truy cập qua Nginx port 3000
- [ ] Messages persist qua `docker compose restart`

**Full Integration Test:**
- [ ] 2 browser tab → switch 2 accounts → chat real-time
- [ ] Bot streaming hoạt động (nếu có API key)
- [ ] Restart toàn bộ stack → data vẫn còn

---

## 🎓 Bạn Đã Hoàn Thành!

Sau 8 tuần, bạn đã build được:

| Tính năng | Công nghệ |
|-----------|----------|
| REST API | Play Controllers + Routes |
| PostgreSQL | Async JDBC + HikariCP + Evolutions |
| MongoDB | Reactive Streams driver + Pekko |
| WebSocket | Pekko Actors + Streams |
| Real-time messaging | Actor model broadcast |
| Typing indicators | Debounce + WS events |
| Online presence | Heartbeat pattern |
| Global chat | Singleton broadcast actor |
| AI Bot streaming | OpenAI API + SSE |
| Settings | Cache API + PostgreSQL |
| Docker deploy | sbt-native-packager + Compose |

**Bước tiếp theo gợi ý:**
1. Thêm Authentication (JWT) thay vì account switcher đơn giản
2. Thêm File/Image sharing (Pekko Streams file upload)
3. Scale với Redis Pub/Sub cho multi-instance
4. Add message reactions, reply-to, delete
5. Push notifications (FCM/APNs)
