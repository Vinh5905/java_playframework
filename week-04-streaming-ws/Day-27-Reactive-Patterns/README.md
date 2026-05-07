# Day 27 - Reactive Patterns với Pekko

## Mục tiêu
- Pekko Actors cho stateful processing
- Circuit breaker pattern
- Backpressure handling

---

## 1. Pekko Actor Basics

```java
import org.apache.pekko.actor.*;

// Actor: stateful, single-threaded, message-driven
public class CounterActor extends AbstractActor {

    private int count = 0;

    // Messages (immutable!)
    public static class Increment { public final int by; public Increment(int by) { this.by = by; } }
    public static class GetCount { }
    public static class CountResponse { public final int count; public CountResponse(int c) { count = c; } }

    public static Props props() {
        return Props.create(CounterActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Increment.class, msg -> {
                count += msg.by;
                log.info("Incremented by {}. Total: {}", msg.by, count);
            })
            .match(GetCount.class, msg -> {
                sender().tell(new CountResponse(count), self());
            })
            .build();
    }
}

// Dùng trong controller
public class ActorController extends Controller {
    private final ActorRef counterActor;

    @Inject
    public ActorController(ActorSystem system) {
        this.counterActor = system.actorOf(CounterActor.props(), "counter");
    }

    public Result increment() {
        counterActor.tell(new CounterActor.Increment(1), ActorRef.noSender());
        return ok("Incremented");
    }

    public CompletionStage<Result> getCount() {
        return ask(counterActor, new CounterActor.GetCount(), Duration.ofSeconds(1))
            .thenApply(response -> ok(Json.newObject()
                .put("count", ((CounterActor.CountResponse) response).count)
            ));
    }
}
```

---

## 2. Circuit Breaker Pattern

Ngăn cascading failures khi external service lỗi:

```java
import org.apache.pekko.pattern.CircuitBreaker;

@Singleton
public class ExternalApiService {

    private final CircuitBreaker breaker;
    private final WSClient ws;

    @Inject
    public ExternalApiService(ActorSystem system, WSClient ws) {
        this.ws = ws;

        // Circuit breaker config
        this.breaker = new CircuitBreaker(
            system.scheduler(),
            system.dispatcher(),
            5,                        // maxFailures: mở breaker sau 5 lỗi
            Duration.ofSeconds(10),   // callTimeout: timeout mỗi call
            Duration.ofMinutes(1)     // resetTimeout: thử lại sau 1 phút
        )
        .addOnOpenListener(() -> log.warn("Circuit breaker OPEN"))
        .addOnCloseListener(() -> log.info("Circuit breaker CLOSED"))
        .addOnHalfOpenListener(() -> log.info("Circuit breaker HALF-OPEN"));
    }

    public CompletionStage<JsonNode> callExternalApi() {
        return breaker.callWithCircuitBreakerCS(() ->
            ws.url("https://external-api.example.com/data")
                .setRequestTimeout(Duration.ofSeconds(5))
                .get()
                .thenApply(WSResponse::asJson)
                .toCompletableFuture()
        );
        // Nếu breaker OPEN → throw CircuitBreakerOpenException ngay (fail fast)
        // Không đợi timeout!
    }
}
```

---

## 3. Backpressure với Pekko Streams

```java
// Xử lý data faster than consumer
Source.range(1, 1_000_000)
    .buffer(100, OverflowStrategy.backpressure())  // Buffer 100, rồi backpressure
    .throttle(100, Duration.ofSeconds(1))           // Max 100 elements/second
    .map(n -> processElement(n))
    .runWith(Sink.foreach(result -> save(result)), materializer);
```

---

## 4. Bài Tập

1. Implement circuit breaker cho WS Client từ Day 22
2. Test: Kill external service → circuit breaker tự mở
3. Implement rate limiter với Actor (stateful)
4. So sánh với filter-based rate limiter từ Day 29
