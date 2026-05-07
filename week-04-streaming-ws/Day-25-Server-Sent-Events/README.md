# Day 25 - Server-Sent Events (SSE)

## Mục tiêu
- Implement SSE trong Play
- So sánh với WebSocket
- Use cases thực tế

---

## SSE vs WebSocket

| | SSE | WebSocket |
|--|-----|-----------|
| Direction | Server → Client only | Bidirectional |
| Protocol | HTTP | WebSocket |
| Reconnect | Tự động | Phải implement |
| Complexity | Đơn giản | Phức tạp hơn |
| Use case | Notifications, live feed | Chat, gaming |

**Chọn SSE khi**: Server push data, client chỉ nhận (notification, stock price, live log)  
**Chọn WebSocket khi**: Cần bidirectional (chat, collaborative editing)

---

## 1. SSE Endpoint

```java
import play.libs.EventSource;
import org.apache.pekko.stream.javadsl.*;
import java.time.Duration;

public class NotificationController extends Controller {

    private final Materializer materializer;

    @Inject
    public NotificationController(Materializer materializer) {
        this.materializer = materializer;
    }

    // GET /events - SSE stream
    public Result events() {
        // Source tạo event mỗi giây
        Source<EventSource.Event, ?> eventStream = Source.tick(
            Duration.ZERO,
            Duration.ofSeconds(1),
            "tick"
        )
        .map(t -> {
            ObjectNode data = Json.newObject();
            data.put("time", System.currentTimeMillis());
            data.put("message", "Server time: " + java.time.LocalTime.now());
            return EventSource.Event.event(data.toString());
        });

        return ok().chunked(eventStream.via(EventSource.flow()))
            .as("text/event-stream");
    }

    // SSE với named events
    public Result notificationStream(Long userId) {
        Source<EventSource.Event, ?> stream = notificationService
            .getStreamForUser(userId)
            .map(notification -> EventSource.Event
                .event(Json.toJson(notification).toString())
                .withId(notification.id.toString())
                .withName("notification")  // event type
            );

        return ok().chunked(stream.via(EventSource.flow()))
            .as("text/event-stream");
    }
}
```

---

## 2. Client (JavaScript)

```javascript
// Kết nối SSE
const eventSource = new EventSource('/events');

eventSource.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log('Received:', data);
    document.getElementById('time').textContent = data.message;
};

eventSource.addEventListener('notification', (event) => {
    const notif = JSON.parse(event.data);
    showNotification(notif);
});

eventSource.onerror = (error) => {
    console.error('SSE error:', error);
    // Browser tự động reconnect sau lỗi
};

// Đóng kết nối
eventSource.close();
```

---

## 3. SSE Format

```
data: {"message": "Hello"}

id: 42
event: notification
data: {"type": "order", "id": 123}

retry: 3000
data: Reconnect after 3s

```

(Mỗi event ngăn cách bằng blank line)

---

## 4. Bài Tập

1. Implement live notification stream
2. Stream số lượng todos được tạo mỗi phút (realtime stats)
3. Test với: `curl -N http://localhost:9000/events`
