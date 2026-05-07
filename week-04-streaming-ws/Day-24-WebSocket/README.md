# Day 24 - WebSocket trong Play

## Mục tiêu
- Implement WebSocket endpoint
- Hiểu WebSocket lifecycle
- Chat room example với Pekko actors

---

## 1. WebSocket Cơ Bản

```java
import play.mvc.*;
import org.apache.pekko.stream.javadsl.*;

public class WebSocketController extends Controller {

    // Echo WebSocket - gửi lại mọi message nhận được
    public WebSocket echo() {
        return WebSocket.Text.accept(request ->
            Flow.<String>create().map(msg -> "Echo: " + msg)
        );
    }
}
```

```
# conf/routes
GET     /ws/echo    controllers.WebSocketController.echo()
```

**Test bằng websocat:**
```bash
brew install websocat
websocat ws://localhost:9000/ws/echo
# Gõ message → nhận "Echo: message"
```

---

## 2. WebSocket Với Business Logic

```java
public WebSocket chat() {
    return WebSocket.Text.accept(request -> {
        // Lấy user từ session/query param
        String username = request.queryString("user").orElse("anonymous");

        // Source: messages gửi đến client
        // Sink: messages nhận từ client
        return Flow.<String>create()
            .map(message -> {
                // Process incoming message
                String processed = "[" + username + "]: " + message;
                broadcastToAll(processed);
                return "Sent: " + message;
            });
    });
}
```

---

## 3. WebSocket Với Pekko Actor (Pattern Đúng Cho Production)

```java
import org.apache.pekko.actor.*;
import org.apache.pekko.stream.javadsl.*;
import org.apache.pekko.stream.*;

public class ChatController extends Controller {

    private final ActorSystem actorSystem;
    private final Materializer materializer;

    @Inject
    public ChatController(ActorSystem actorSystem, Materializer materializer) {
        this.actorSystem = actorSystem;
        this.materializer = materializer;
    }

    public WebSocket chat() {
        return WebSocket.Text.acceptOrResult(request ->
            CompletableFuture.completedFuture(
                // Auth check
                request.session().get("userId")
                    .map(userId -> {
                        // Tạo flow cho connection này
                        Flow<String, String, ?> flow = createChatFlow(userId);
                        return Either.<Result, Flow<String, String, ?>>Right(flow);
                    })
                    .orElse(Either.Left(forbidden("Login required")))
            )
        );
    }

    private Flow<String, String, ?> createChatFlow(String userId) {
        // Tạo actor cho connection này
        ActorRef chatActor = actorSystem.actorOf(
            ChatActor.props(userId),
            "chat-" + userId + "-" + System.currentTimeMillis()
        );

        // ActorSource → nhận message từ actor → gửi về client
        Source<String, ActorRef> source = Source.actorRef(16, OverflowStrategy.dropHead());

        // Sink → nhận message từ client → gửi vào actor
        Sink<String, ?> sink = Sink.actorRef(chatActor, PoisonPill.getInstance());

        return Flow.fromSinkAndSource(sink, source);
    }
}

// Chat Actor
public class ChatActor extends AbstractActor {
    private final String userId;

    public static Props props(String userId) {
        return Props.create(ChatActor.class, () -> new ChatActor(userId));
    }

    public ChatActor(String userId) {
        this.userId = userId;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(String.class, msg -> {
                // Process message, send to other actors, etc.
                System.out.println(userId + " says: " + msg);
            })
            .build();
    }
}
```

---

## 4. WebSocket Lifecycle

```
Client                          Server
  |                               |
  |------- HTTP Upgrade ------→  |   ← HTTP request với Upgrade: websocket
  |  ←--- 101 Switching -------- |   ← Server agree
  |                               |
  |===== WebSocket Frame ======→  |   ← Bidirectional frames
  |  ←== WebSocket Frame ======= |
  |                               |
  |------- Close Frame -------→  |   ← Either side can close
  |  ←--- Close Frame ---------- |
  |                               |
```

---

## 5. Server-Sent Events (SSE) - Alternative Đơn Giản Hơn

Khi chỉ cần server → client (không cần bidirectional):

```java
import play.libs.EventSource;
import org.apache.pekko.stream.javadsl.*;
import java.time.Duration;

public Result sse() {
    // Stream sự kiện mỗi giây
    Source<EventSource.Event, ?> eventStream = Source.tick(
        Duration.ZERO,
        Duration.ofSeconds(1),
        "tick"
    ).map(t -> EventSource.Event.event(
        "{\"time\": " + System.currentTimeMillis() + "}"
    ));

    return ok().chunked(eventStream.via(EventSource.flow()))
        .as("text/event-stream");
}
```

**Client (JavaScript):**
```javascript
const es = new EventSource('/events');
es.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log('Time:', data.time);
};
```

---

## 6. Bài Tập

Xem `websocket-demo/` trong thư mục này.

```bash
cd websocket-demo
sbt run

# Test echo WebSocket
websocat ws://localhost:9000/ws/echo

# Test SSE (Server-Sent Events)
curl -N http://localhost:9000/events

# Mở browser http://localhost:9000 để xem chat demo
```
