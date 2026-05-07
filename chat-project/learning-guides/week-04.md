# 📅 Tuần 4: WebSocket + Real-time Messaging

---

## 1. 🎯 Mục Tiêu Cuối Tuần

**Thấy gì trên màn hình:**
- Mở 2 tab browser, switch 2 account khác nhau
- Tab 1 (Alice) gửi "Hello!" → Tab 2 (Florencio) thấy ngay **không cần reload**
- Thực sự real-time qua WebSocket

---

## 2. 📚 Kiến Thức Lý Thuyết

### 2.1 WebSocket vs HTTP Polling

**HTTP Polling (cũ, kém):**
```
Client → GET /new-messages?since=123  (mỗi 2 giây)
Server → []  (không có gì)
Client → GET /new-messages?since=123  (2 giây sau)
Server → [{"text": "Hello!"}]
→ Delay tối đa 2 giây, lãng phí bandwidth
```

**WebSocket (đúng):**
```
Client ──── HTTP Upgrade ────→ Server
Client ←══ WS Connection ════→ Server  (bidirectional, persistent)
Server ──── push "Hello!" ───→ Client  (ngay lập tức, không cần poll)
```

### 2.2 WebSocket Trong Play: Pekko Streams + Actors

Play WebSocket được implement qua Pekko Streams:

```
WebSocket Connection
  ↓
Flow[Message, Message, _]
  ├── Source (server → client: push messages)
  └── Sink   (client → server: receive messages)
```

Pattern tốt nhất: **1 Actor per connection**

```
User A connects → ChatActor(A) tạo
User B connects → ChatActor(B) tạo

A gửi "Hello B":
  ChatActor(A) → MessageRepository.save()
               → ChatActor(B).tell("Hello B")
  ChatActor(B) → push qua WebSocket → Browser B hiện "Hello"
```

### 2.3 WebSocket Message Format

Frontend và Backend đều phải agree trên format:

```json
// Client → Server: gửi tin
{"type":"message","convId":"1","text":"Hello!","senderId":1}

// Server → Client: nhận tin
{"type":"message","convId":"1","message":{"id":"...","text":"Hello!","senderId":1,"time":"..."}}

// Server → Client: typing (Tuần 5)
{"type":"typing","convId":"1","userId":2,"isTyping":true}

// Server → Client: presence (Tuần 5)
{"type":"presence","userId":2,"status":"online"}
```

---

## 3. 🛠️ Setup

Không cần cài thêm gì - Pekko đã có trong Play.

---

## 4. 📂 Cấu Trúc File Tuần 4

```
your-project/be/
├── app/
│   ├── actors/
│   │   ├── ChatRoomActor.java          ← TẠO MỚI: quản lý 1 conversation
│   │   └── UserConnectionActor.java    ← TẠO MỚI: 1 actor per WS connection
│   ├── controllers/
│   │   └── WebSocketController.java    ← TẠO MỚI: WS endpoint
│   └── ... (các file cũ không đổi)
└── conf/
    └── routes                          ← SỬA: thêm WS route
```

---

## 5. 👨‍💻 Hướng Dẫn Code

### Bước 5.1: UserConnectionActor - 1 Instance Per WS Connection

**File:** `app/actors/UserConnectionActor.java`

```java
package actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import play.libs.Json;

/**
 * 1 actor = 1 WebSocket connection = 1 user's browser tab.
 *
 * Actor này nhận:
 * - Messages từ browser (qua WS) → forward đến ChatRoomActor
 * - Messages từ ChatRoomActor → push về browser qua WS
 *
 * Khi WS disconnect → Actor tự destroy (lifecycle management tự động).
 */
public class UserConnectionActor extends AbstractActor {

    private final Long accountId;
    private final ActorRef wsOut;          // Kênh gửi về browser
    private final ActorRef chatRegistry;   // Registry của tất cả rooms

    public static Props props(Long accountId, ActorRef wsOut, ActorRef chatRegistry) {
        return Props.create(UserConnectionActor.class,
            () -> new UserConnectionActor(accountId, wsOut, chatRegistry));
    }

    public UserConnectionActor(Long accountId, ActorRef wsOut, ActorRef chatRegistry) {
        this.accountId = accountId;
        this.wsOut = wsOut;
        this.chatRegistry = chatRegistry;
    }

    @Override
    public void preStart() {
        // Khi connect → báo presence online
        chatRegistry.tell(new ChatRoomActor.UserConnected(accountId, self()), self());
    }

    @Override
    public void postStop() {
        // Khi disconnect → báo presence offline
        chatRegistry.tell(new ChatRoomActor.UserDisconnected(accountId), self());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            // Từ browser → parse JSON → xử lý
            .match(JsonNode.class, this::handleClientMessage)
            // Từ ChatRoomActor → push về browser
            .match(ObjectNode.class, msg -> wsOut.tell(msg.toString(), self()))
            .build();
    }

    private void handleClientMessage(JsonNode msg) {
        String type = msg.path("type").asText();

        switch (type) {
            case "message":
                chatRegistry.tell(new ChatRoomActor.SendMessage(
                    msg.path("convId").asText(),
                    accountId,
                    msg.path("text").asText()
                ), self());
                break;

            case "typing":
                chatRegistry.tell(new ChatRoomActor.TypingEvent(
                    msg.path("convId").asText(),
                    accountId,
                    msg.path("isTyping").asBoolean()
                ), self());
                break;

            default:
                // Ignore unknown message types
        }
    }
}
```

### Bước 5.2: ChatRoomActor - Registry + Broadcast

**File:** `app/actors/ChatRoomActor.java`

```java
package actors;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import play.libs.Json;
import repositories.MessageRepository;

import javax.inject.Inject;
import java.util.*;

/**
 * Singleton actor (1 instance cho toàn app).
 * Quản lý tất cả connections và broadcast messages.
 *
 * State:
 * - Map<accountId, ActorRef> connectedUsers → biết ai đang online
 * - Khi tin nhắn đến: tìm recipient → gửi qua actor của họ
 */
public class ChatRoomActor extends AbstractActor {

    // ── Messages (inner classes) ────────────────────────
    public static class UserConnected {
        public final Long accountId; public final ActorRef actor;
        public UserConnected(Long id, ActorRef a) { accountId = id; actor = a; }
    }
    public static class UserDisconnected {
        public final Long accountId;
        public UserDisconnected(Long id) { accountId = id; }
    }
    public static class SendMessage {
        public final String convId; public final Long senderId; public final String text;
        public SendMessage(String c, Long s, String t) { convId=c; senderId=s; text=t; }
    }
    public static class TypingEvent {
        public final String convId; public final Long userId; public final boolean isTyping;
        public TypingEvent(String c, Long u, boolean t) { convId=c; userId=u; isTyping=t; }
    }

    // ── State ───────────────────────────────────────────
    private final Map<Long, ActorRef> connectedUsers = new HashMap<>();
    private final MessageRepository messageRepo;

    public static Props props(MessageRepository messageRepo) {
        return Props.create(ChatRoomActor.class, () -> new ChatRoomActor(messageRepo));
    }

    public ChatRoomActor(MessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(UserConnected.class, msg -> {
                connectedUsers.put(msg.accountId, msg.actor);
                broadcastPresence(msg.accountId, "online");
            })
            .match(UserDisconnected.class, msg -> {
                connectedUsers.remove(msg.accountId);
                broadcastPresence(msg.accountId, "offline");
            })
            .match(SendMessage.class, this::handleSendMessage)
            .match(TypingEvent.class, this::handleTyping)
            .build();
    }

    private void handleSendMessage(SendMessage cmd) {
        // 1. Lưu vào MongoDB async
        var msg = new models.Message(cmd.convId, cmd.senderId, cmd.text);
        messageRepo.saveMessage(msg).thenAccept(saved -> {
            // 2. Broadcast đến TẤT CẢ users online (đơn giản)
            // Tuần sau sẽ chỉ gửi đến đúng participants
            var payload = Json.newObject()
                .put("type", "message")
                .put("convId", saved.conversationId)
                .set("message", Json.toJson(saved));

            connectedUsers.forEach((accountId, actor) -> {
                actor.tell(payload, self());
            });
        });
    }

    private void handleTyping(TypingEvent event) {
        var payload = Json.newObject()
            .put("type", "typing")
            .put("convId", event.convId)
            .put("userId", event.userId)
            .put("isTyping", event.isTyping);

        // Gửi đến tất cả trừ sender
        connectedUsers.forEach((accountId, actor) -> {
            if (!accountId.equals(event.userId)) {
                actor.tell(payload, self());
            }
        });
    }

    private void broadcastPresence(Long userId, String status) {
        var payload = Json.newObject()
            .put("type", "presence")
            .put("userId", userId)
            .put("status", status);

        connectedUsers.forEach((id, actor) -> {
            if (!id.equals(userId)) {
                actor.tell(payload, self());
            }
        });
    }
}
```

### Bước 5.3: WebSocketController

**File:** `app/controllers/WebSocketController.java`

```java
package controllers;

import actors.ChatRoomActor;
import actors.UserConnectionActor;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import play.libs.streams.ActorFlow;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.WebSocket;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class WebSocketController extends Controller {

    private final ActorSystem actorSystem;
    private final Materializer materializer;
    private final ActorRef chatRoomActor;

    @Inject
    public WebSocketController(ActorSystem actorSystem,
                                Materializer materializer,
                                @Named("chat-room") ActorRef chatRoomActor) {
        this.actorSystem = actorSystem;
        this.materializer = materializer;
        this.chatRoomActor = chatRoomActor;
    }

    /**
     * GET /ws/chat?accountId=1
     * Browser upgrade sang WebSocket protocol tại đây.
     *
     * ActorFlow.actorRef tạo 1 UserConnectionActor cho mỗi connection,
     * kết nối nó với WS stream (wsOut → actor, actor → wsOut).
     */
    public WebSocket chat(Http.Request request) {
        Long accountId = Long.parseLong(
            request.getQueryString("accountId").orElse("1")
        );

        return WebSocket.Json.accept(req ->
            ActorFlow.actorRef(
                wsOut -> UserConnectionActor.props(accountId, wsOut, chatRoomActor),
                actorSystem,
                materializer
            )
        );
    }
}
```

### Bước 5.4: Đăng ký ChatRoomActor qua Module

**File:** `app/modules/AppModule.java`

```java
package modules;

import actors.ChatRoomActor;
import com.google.inject.AbstractModule;
import play.libs.pekko.PekkoGuiceSupport;
import repositories.MessageRepository;

public class AppModule extends AbstractModule implements PekkoGuiceSupport {
    @Override
    protected void configure() {
        // Bind ChatRoomActor như 1 singleton actor
        bindActor(ChatRoomActor.class, "chat-room");
    }
}
```

```hocon
# application.conf
play.modules.enabled += "modules.AppModule"
```

### Bước 5.5: Thêm WS Route

```
# conf/routes
GET     /ws/chat                        controllers.WebSocketController.chat(request: Request)
```

### Bước 5.6: Bật WebSocket ở Frontend

**File:** `your-project/fe/js/websocket.js`

Uncomment toàn bộ code WebSocket (đã có sẵn, chỉ cần bỏ comment).

---

## 6. 🔄 Sự Tiến Hóa

| | Tuần 3 | Tuần 4 |
|--|--------|--------|
| Nhận tin | Phải reload | Real-time |
| Giao tiếp | HTTP request/response | WebSocket bidirectional |
| State | Stateless | Stateful actors |
| Concurrency | CompletionStage | Pekko Actor model |

---

## 7. ⚠️ Pitfalls Tuần 4

**Actor lifecycle**: Actor tự destroy khi WS disconnect. Đừng giữ reference ngoài actor system.

**Thread safety**: Pekko actor xử lý 1 message tại 1 thời điểm → không cần `synchronized`. Nhưng đừng share mutable state giữa actors.

**WebSocket browser**: Chỉ 1 WS connection per tab. Mở tab mới = connection mới = actor mới.

---

## 8. ✅ Checklist Tuần 4

- [ ] 2 tabs browser với 2 accounts khác nhau
- [ ] Tab 1 gửi tin → Tab 2 thấy ngay (< 100ms)
- [ ] Disconnect 1 tab → tab kia thấy "offline"
- [ ] Không có memory leak sau nhiều connect/disconnect

---

## 10. 🔗 Kết Nối Tuần 5

Tuần 5 thêm **Typing Indicator** (đã có WS nên dễ) và **Online Status** real-time. Code ChatRoomActor đã có sẵn typing/presence handling.
