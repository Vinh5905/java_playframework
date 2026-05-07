# 📅 Tuần 5: Typing Indicator + Online/Offline Status

---

## 1. 🎯 Mục Tiêu Cuối Tuần

**Thấy gì:**
- Alice gõ tin → Florencio thấy "Alice is typing..." với 3 chấm nhảy
- Alice đóng tab → Florencio thấy dot chuyển từ xanh sang xám (offline)
- Florencio mở tab → Alice thấy dot xanh ngay

---

## 2. 📚 Kiến Thức Lý Thuyết

### 2.1 Typing Indicator: Debounce Pattern

Nếu frontend gửi WebSocket event mỗi keypress → quá nhiều events.

**Giải pháp:** Debounce - chỉ gửi "stopped typing" sau khi user dừng 1.5 giây.

```javascript
// Frontend đã có trong app.js
let typingTimer;
function handleTyping() {
    WS.sendTyping(convId, true);   // Gửi ngay: "đang gõ"
    clearTimeout(typingTimer);
    typingTimer = setTimeout(() => {
        WS.sendTyping(convId, false);  // Gửi sau 1.5s không gõ: "dừng"
    }, 1500);
}
```

### 2.2 Presence System: Heartbeat

Chỉ tracking connect/disconnect không đủ. Browser có thể crash mà không gửi WS close frame → server không biết user offline.

**Giải pháp:** Heartbeat (ping/pong)
```
Client → server: {"type": "ping"} mỗi 30s
Server → client: {"type": "pong"}

Server: Nếu không nhận ping trong 60s → coi user offline
```

### 2.3 Tại Sao Cần PresenceService Riêng?

Khi scale lên nhiều server (horizontal scaling), mỗi server chỉ biết users kết nối tới nó. User A ở Server 1 không biết User B ở Server 2 online.

**Giải pháp production:** Redis Pub/Sub hoặc Kafka để sync presence giữa các server.

**Tuần 5:** Chỉ implement single-server (actor-based), đủ cho demo.

---

## 3. 📂 Cấu Trúc File Tuần 5

```
your-project/be/
├── app/
│   ├── actors/
│   │   ├── ChatRoomActor.java        ← SỬA: thêm heartbeat handling
│   │   └── UserConnectionActor.java  ← SỬA: thêm ping/pong
│   └── services/
│       └── PresenceService.java      ← TẠO MỚI: centralized online tracking
```

---

## 5. 👨‍💻 Hướng Dẫn Code

### Bước 5.1: PresenceService

```java
// app/services/PresenceService.java
package services;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Track which users are online.
 * Singleton → shared across all requests.
 *
 * Tuần 5: In-memory (lost on restart)
 * Production: Integrate with Redis
 */
@Singleton
public class PresenceService {

    // userId → last seen timestamp
    private final Map<Long, Instant> lastSeen = new ConcurrentHashMap<>();
    private final Set<Long> onlineUsers = ConcurrentHashMap.newKeySet();

    public void markOnline(Long userId) {
        onlineUsers.add(userId);
        lastSeen.put(userId, Instant.now());
    }

    public void markOffline(Long userId) {
        onlineUsers.remove(userId);
        lastSeen.put(userId, Instant.now());
    }

    public boolean isOnline(Long userId) {
        return onlineUsers.contains(userId);
    }

    public Set<Long> getOnlineUsers() {
        return Collections.unmodifiableSet(onlineUsers);
    }

    public Instant getLastSeen(Long userId) {
        return lastSeen.getOrDefault(userId, Instant.EPOCH);
    }
}
```

### Bước 5.2: Thêm Heartbeat vào UserConnectionActor

```java
// Trong UserConnectionActor.java - thêm:

private static final long HEARTBEAT_TIMEOUT_MS = 60_000;  // 60s
private long lastPingTime = System.currentTimeMillis();

// Trong handleClientMessage():
case "ping":
    lastPingTime = System.currentTimeMillis();
    // Gửi pong về browser
    ObjectNode pong = Json.newObject().put("type", "pong");
    wsOut.tell(pong.toString(), self());
    break;

// Trong preStart():
// Schedule heartbeat check mỗi 30s
context().system().scheduler().scheduleWithFixedDelay(
    java.time.Duration.ofSeconds(30),
    java.time.Duration.ofSeconds(30),
    self(),
    "check_heartbeat",
    context().dispatcher(),
    self()
);

// Trong createReceive():
.match(String.class, cmd -> {
    if ("check_heartbeat".equals(cmd)) {
        long elapsed = System.currentTimeMillis() - lastPingTime;
        if (elapsed > HEARTBEAT_TIMEOUT_MS) {
            // Timeout → close connection
            context().stop(self());
        }
    }
})
```

### Bước 5.3: Frontend Heartbeat

```javascript
// websocket.js - thêm vào connect():
setInterval(() => {
    WS.send({ type: 'ping' });
}, 30000);
```

### Bước 5.4: API endpoint online users

```java
// app/controllers/AccountController.java - thêm:

private final PresenceService presenceService;

@Inject
public AccountController(AccountRepository repo, PresenceService presenceService) {
    this.repo = repo;
    this.presenceService = presenceService;
}

// GET /api/presence
public Result presence() {
    Set<Long> online = presenceService.getOnlineUsers();
    return ok(Json.toJson(online));
}
```

```
# routes
GET     /api/presence    controllers.AccountController.presence()
```

---

## 6. 🔄 Sự Tiến Hóa

| | Tuần 4 | Tuần 5 |
|--|--------|--------|
| Online status | Chỉ track connect/disconnect | Heartbeat + timeout |
| Typing | Frontend mock | Real WebSocket event |
| Presence API | Không có | GET /api/presence |

---

## 8. ⚠️ Pitfalls Tuần 5

**Debounce vs Throttle**: Typing dùng **debounce** (gửi sau khi dừng). Online status dùng **heartbeat** (gửi định kỳ).

**Race condition**: Nếu 2 tab cùng 1 account → markOffline khi 1 tab đóng nhưng tab kia vẫn online. Cần reference counting: `onlineCount[userId]++` khi connect, `--` khi disconnect, offline chỉ khi = 0.

---

## 9. ✅ Checklist Tuần 5

- [ ] Gõ tin → bên kia thấy "... is typing" trong < 200ms
- [ ] Dừng gõ 1.5s → "typing" biến mất
- [ ] Đóng tab → bên kia thấy dot xám trong < 65s (heartbeat timeout)
- [ ] GET /api/presence → danh sách userId đang online

---

## 10. 🔗 Kết Nối Tuần 6

Tuần 6: Global Chat Room - 1 room cho tất cả users online. Sẽ tái dùng ChatRoomActor với broadcast pattern.
