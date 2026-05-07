# Day 28 - Mini Project: Realtime Notifications

## Mục tiêu
- Tổng hợp Tuần 4 (WebSocket, SSE, Streams, WS Client)
- Realtime notification system
- Kết hợp WebSocket + SSE tùy theo client

---

## Spec

```
POST /events         Publish event (webhook nhận từ service khác)
GET  /events/stream  SSE stream cho browser
GET  /ws/events      WebSocket stream (bidirectional)
GET  /events/recent  Last 100 events (REST)
```

---

## Architecture

```
External Service
  │
  ▼ POST /events
EventController
  │
  ▼
EventBus (Pekko Actor - stateful)
  ├─→ SSE clients (broadcast to all)
  └─→ WebSocket clients (broadcast to all)

Browser A ←── SSE stream ───── EventBus
Browser B ←── WebSocket ────── EventBus
```

---

## Key Components

```java
// Event Bus Actor - broadcast events to all connected clients
public class EventBusActor extends AbstractActor {

    private final List<ActorRef> sseClients = new ArrayList<>();
    private final List<ActorRef> wsClients = new ArrayList<>();
    private final LinkedList<Event> recentEvents = new LinkedList<>();

    // Messages
    public static class Subscribe { public final ActorRef client; public Subscribe(ActorRef c) { client = c; } }
    public static class Unsubscribe { public final ActorRef client; /* ... */ }
    public static class Publish { public final Event event; /* ... */ }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Publish.class, msg -> {
                // Lưu event gần đây (max 100)
                recentEvents.addFirst(msg.event);
                if (recentEvents.size() > 100) recentEvents.removeLast();

                // Broadcast đến tất cả clients
                String json = Json.toJson(msg.event).toString();
                sseClients.forEach(c -> c.tell(json, self()));
                wsClients.forEach(c -> c.tell(json, self()));
            })
            // ... handle Subscribe, Unsubscribe
            .build();
    }
}

// SSE Endpoint
public Result sseStream() {
    ActorRef sseActor = actorSystem.actorOf(SseClientActor.props(eventBus));

    Source<EventSource.Event, ?> source = Source
        .actorRef(64, OverflowStrategy.dropHead())
        .mapMaterializedValue(clientActor -> {
            eventBus.tell(new EventBusActor.Subscribe(clientActor), ActorRef.noSender());
            return clientActor;
        })
        .map(msg -> EventSource.Event.event(msg.toString()));

    return ok().chunked(source.via(EventSource.flow()))
        .as("text/event-stream");
}
```

---

## Chạy Project

```bash
cd realtime-notifications
sbt run

# Subscribe SSE
curl -N http://localhost:9000/events/stream

# In another terminal - publish event
curl -X POST http://localhost:9000/events \
  -H "Content-Type: application/json" \
  -d '{"type": "order", "message": "New order #1234", "priority": "high"}'

# → SSE client nhận ngay trong vài milliseconds!

# WebSocket test
websocat ws://localhost:9000/ws/events
```

---

## Điểm Học Được Từ Tuần 4

1. **Day 22**: WS Client - gọi external API async
2. **Day 23**: Pekko Streams - xử lý data streams
3. **Day 24**: WebSocket - bidirectional realtime
4. **Day 25**: SSE - server push unidirectional
5. **Day 26**: File upload/download streaming
6. **Day 27**: Circuit breaker, Actor pattern
7. **Day 28**: Kết hợp tất cả
