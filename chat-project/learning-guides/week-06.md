# 📅 Tuần 6: Global Chat Room

---

## 1. 🎯 Mục Tiêu Cuối Tuần

**Thấy gì:**
- Tab "Global" trong sidebar
- Tất cả accounts online đều thấy cùng 1 conversation
- Gửi tin → tất cả tab browser hiện tại thấy ngay

---

## 2. 📚 Kiến Thức Lý Thuyết

### 2.1 Global Chat vs Direct Message

| | Direct Message | Global Room |
|--|----------------|-------------|
| Recipients | 2 người | Tất cả online |
| Routing | convId → tìm participants | Broadcast tất cả |
| History | Per-conversation | Chung |

### 2.2 Broadcast Actor Pattern

```
User A gửi "Hello"
    ↓
GlobalRoomActor.broadcast("Hello", fromA)
    ↓
Gửi đến Actor(B), Actor(C), Actor(D), ... (tất cả trừ A)
    ↓
Browser B, C, D hiển thị "Hello"
```

### 2.3 Persistent Global Room Messages

Global room messages cũng lưu MongoDB nhưng với `conversationId = "global"`.

---

## 4. 📂 Cấu Trúc File Tuần 6

```
your-project/be/
├── app/
│   ├── actors/
│   │   └── GlobalRoomActor.java      ← TẠO MỚI
│   ├── controllers/
│   │   └── GlobalChatController.java ← TẠO MỚI: WS endpoint cho global
└── conf/
    └── routes                         ← SỬA: thêm /ws/global
```

---

## 5. 👨‍💻 Hướng Dẫn Code

### GlobalRoomActor

```java
// app/actors/GlobalRoomActor.java
package actors;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import play.libs.Json;
import repositories.MessageRepository;

import java.util.*;

public class GlobalRoomActor extends AbstractActor {

    public static class Join    { public final Long userId; public final ActorRef actor;
                                  public Join(Long u, ActorRef a) { userId=u; actor=a; } }
    public static class Leave   { public final Long userId; public Leave(Long u) { userId=u; } }
    public static class Broadcast { public final Long senderId; public final String text;
                                    public Broadcast(Long s, String t) { senderId=s; text=t; } }

    private final Map<Long, ActorRef> members = new HashMap<>();
    private final MessageRepository messageRepo;

    public static Props props(MessageRepository r) {
        return Props.create(GlobalRoomActor.class, () -> new GlobalRoomActor(r));
    }

    public GlobalRoomActor(MessageRepository r) { this.messageRepo = r; }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Join.class, j -> members.put(j.userId, j.actor))
            .match(Leave.class, l -> members.remove(l.userId))
            .match(Broadcast.class, b -> {
                var msg = new models.Message("global", b.senderId, b.text);
                messageRepo.saveMessage(msg).thenAccept(saved -> {
                    var payload = Json.newObject()
                        .put("type", "global_message")
                        .set("message", Json.toJson(saved));

                    members.forEach((uid, actor) -> {
                        if (!uid.equals(b.senderId)) {
                            actor.tell(payload, self());
                        }
                    });
                });
            })
            .build();
    }
}
```

### GlobalChatController

```java
// app/controllers/GlobalChatController.java
package controllers;

import actors.GlobalRoomActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.stream.Materializer;
import play.libs.streams.ActorFlow;
import play.mvc.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class GlobalChatController extends Controller {

    private final ActorSystem actorSystem;
    private final Materializer materializer;
    private final ActorRef globalRoom;

    @Inject
    public GlobalChatController(ActorSystem system, Materializer mat,
                                 @Named("global-room") ActorRef globalRoom) {
        this.actorSystem = system;
        this.materializer = mat;
        this.globalRoom = globalRoom;
    }

    // GET /ws/global?accountId=1
    public WebSocket join(Http.Request req) {
        Long accountId = Long.parseLong(req.getQueryString("accountId").orElse("1"));

        return WebSocket.Json.accept(request ->
            ActorFlow.actorRef(
                wsOut -> Props.create(GlobalMemberActor.class,
                    () -> new GlobalMemberActor(accountId, wsOut, globalRoom)),
                actorSystem, materializer
            )
        );
    }
}
```

### Đăng ký trong AppModule

```java
// app/modules/AppModule.java - thêm:
bindActor(GlobalRoomActor.class, "global-room");
```

### Routes

```
GET     /ws/global                      controllers.GlobalChatController.join(request: Request)
GET     /api/global/messages            controllers.GlobalChatController.history()
```

---

## 5. Frontend - Thêm Tab Global

Trong `app.js`, thêm tab "Global" vào sidebar:

```javascript
// Trong renderConversations() - thêm global conversation đầu tiên
const globalConv = {
    id: 'global',
    name: 'Global Chat',
    isGlobal: true,
    lastMessage: 'Everyone can chat here',
};

// WS kết nối global room
WS.connectGlobal(accountId);
```

---

## 9. ✅ Checklist Tuần 6

- [ ] Tab "Global Chat" hiện trong sidebar
- [ ] 3 tabs/accounts cùng nhìn global → 1 gửi → 2 còn lại thấy ngay
- [ ] Lịch sử global persist qua restart
- [ ] GET /api/global/messages → 20 tin mới nhất

---

## 10. 🔗 Kết Nối Tuần 7

Tuần 7: ChatGPT Bot với streaming. Đây là tính năng phức tạp nhất - call OpenAI API và stream response từng ký tự.
