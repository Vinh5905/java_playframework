package repositories;

import models.Todo;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory repository - dữ liệu mất khi restart app.
 *
 * @Singleton: Guice tạo đúng 1 instance, inject vào mọi nơi cần.
 *
 * ConcurrentHashMap + AtomicLong: thread-safe cho concurrent HTTP requests.
 * Nếu dùng HashMap + long, có thể bị race condition khi nhiều request cùng lúc.
 */
@Singleton
public class TodoRepository {

    private final Map<Long, Todo> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public List<Todo> findAll() {
        return new ArrayList<>(store.values());
    }

    public Optional<Todo> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public Todo save(String title) {
        Long id = idGenerator.incrementAndGet();
        Todo todo = new Todo(id, title, false);
        store.put(id, todo);
        return todo;
    }

    public Optional<Todo> update(Long id, String title, boolean done) {
        Todo existing = store.get(id);
        if (existing == null) return Optional.empty();

        existing.title = title;
        existing.done = done;
        return Optional.of(existing);
    }

    public boolean delete(Long id) {
        return store.remove(id) != null;
    }

    public Map<String, Long> stats() {
        long total = store.size();
        long done = store.values().stream().filter(t -> t.done).count();
        long pending = total - done;

        Map<String, Long> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("done", done);
        result.put("pending", pending);
        return result;
    }
}
