package repositories;

import models.Todo;
import org.apache.pekko.actor.ActorSystem;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Day 14 - Async Repository
 *
 * So sánh với Day 07 TodoRepository:
 * - Tất cả method return CompletionStage
 * - Blocking code chạy trên blockingEc thay vì default EC
 * - Kết quả: Event loop thread tự do nhận request tiếp theo
 */
@Singleton
public class AsyncTodoRepository {

    private final Map<Long, Todo> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);
    private final ExecutionContext blockingEc;

    @Inject
    public AsyncTodoRepository(ActorSystem actorSystem) {
        // Lấy dispatcher đã cấu hình trong application.conf
        this.blockingEc = actorSystem.dispatchers().lookup("blocking-io-dispatcher");
    }

    public CompletionStage<List<Todo>> findAll() {
        return CompletableFuture.supplyAsync(
            () -> {
                // Giả lập latency của real DB query
                simulateDbLatency(10);
                return new ArrayList<>(store.values());
            },
            (ExecutionContextExecutor) blockingEc
        );
    }

    public CompletionStage<Optional<Todo>> findById(Long id) {
        return CompletableFuture.supplyAsync(
            () -> {
                simulateDbLatency(10);
                return Optional.ofNullable(store.get(id));
            },
            (ExecutionContextExecutor) blockingEc
        );
    }

    public CompletionStage<Todo> save(String title) {
        return CompletableFuture.supplyAsync(
            () -> {
                simulateDbLatency(15);
                Long id = idGenerator.incrementAndGet();
                Todo todo = new Todo(id, title, false);
                store.put(id, todo);
                return todo;
            },
            (ExecutionContextExecutor) blockingEc
        );
    }

    public CompletionStage<Optional<Todo>> update(Long id, String title, boolean done) {
        return CompletableFuture.supplyAsync(
            () -> {
                simulateDbLatency(15);
                Todo existing = store.get(id);
                if (existing == null) return Optional.<Todo>empty();
                existing.title = title;
                existing.done = done;
                return Optional.of(existing);
            },
            (ExecutionContextExecutor) blockingEc
        );
    }

    public CompletionStage<Boolean> delete(Long id) {
        return CompletableFuture.supplyAsync(
            () -> {
                simulateDbLatency(10);
                return store.remove(id) != null;
            },
            (ExecutionContextExecutor) blockingEc
        );
    }

    public CompletionStage<Map<String, Long>> stats() {
        return CompletableFuture.supplyAsync(
            () -> {
                simulateDbLatency(20);
                long total = store.size();
                long done = store.values().stream().filter(t -> t.done).count();
                Map<String, Long> result = new LinkedHashMap<>();
                result.put("total", total);
                result.put("done", done);
                result.put("pending", total - done);
                return result;
            },
            (ExecutionContextExecutor) blockingEc
        );
    }

    private void simulateDbLatency(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
