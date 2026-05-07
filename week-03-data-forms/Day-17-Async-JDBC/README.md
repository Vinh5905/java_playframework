# Day 17 - Async JDBC: Pattern Đúng Cho Database

## Mục tiêu
- Viết JDBC queries async với custom dispatcher
- Transaction management
- Batch operations

---

## 1. Tại Sao JDBC Luôn Blocking?

JDBC (Java Database Connectivity) là blocking API theo thiết kế:
```
conn.prepareStatement(sql)  → block đến khi SQL được prepare
ps.executeQuery()            → block đến khi DB trả kết quả
rs.next()                    → block đến khi row được đọc
```

**R2DBC** (Reactive Relational Database Connectivity) là reactive alternative, nhưng ecosystem nhỏ hơn.

**Approach thực tế**: Dùng JDBC blocking + wrap trong custom dispatcher.

---

## 2. Pattern Chuẩn

```java
@Singleton
public class TodoRepository {

    private final Database db;
    private final ExecutionContext dbEc;

    @Inject
    public TodoRepository(Database db, ActorSystem system) {
        this.db = db;
        this.dbEc = system.dispatchers().lookup("blocking-db-dispatcher");
    }

    // SELECT
    public CompletionStage<List<Todo>> findAll() {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                List<Todo> todos = new ArrayList<>();
                String sql = "SELECT id, title, done, created_at FROM todos ORDER BY id";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        todos.add(new Todo(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getBoolean("done"),
                            rs.getTimestamp("created_at").toInstant().toString()
                        ));
                    }
                }
                return todos;
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    // INSERT với RETURNING
    public CompletionStage<Todo> insert(String title) {
        return CompletableFuture.supplyAsync(
            () -> db.withTransaction(conn -> {
                String sql = "INSERT INTO todos (title) VALUES (?) " +
                             "RETURNING id, title, done, created_at";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, title);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    return new Todo(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getBoolean("done"),
                        rs.getTimestamp("created_at").toInstant().toString()
                    );
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    // UPDATE
    public CompletionStage<Boolean> update(Long id, String title, boolean done) {
        return CompletableFuture.supplyAsync(
            () -> db.withTransaction(conn -> {
                String sql = "UPDATE todos SET title = ?, done = ?, updated_at = NOW() WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, title);
                    ps.setBoolean(2, done);
                    ps.setLong(3, id);
                    return ps.executeUpdate() > 0;
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    // DELETE
    public CompletionStage<Boolean> delete(Long id) {
        return CompletableFuture.supplyAsync(
            () -> db.withTransaction(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM todos WHERE id = ?")) {
                    ps.setLong(1, id);
                    return ps.executeUpdate() > 0;
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    // BATCH INSERT
    public CompletionStage<Integer> batchInsert(List<String> titles) {
        return CompletableFuture.supplyAsync(
            () -> db.withTransaction(conn -> {
                String sql = "INSERT INTO todos (title) VALUES (?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (String title : titles) {
                        ps.setString(1, title);
                        ps.addBatch();
                    }
                    int[] counts = ps.executeBatch();
                    return counts.length;
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }
}
```

---

## 3. Connection Management

- `db.withConnection(...)` - mượn connection, tự return khi done
- `db.withTransaction(...)` - như withConnection + auto commit/rollback
- Không bao giờ giữ connection lâu hơn cần thiết
- Connection pool (HikariCP) quản lý pool size

---

## 4. Bài Tập

1. Implement `TodoRepository` đầy đủ với PostgreSQL
2. Test với `ab -n 200 -c 20 http://localhost:9000/todos` - quan sát connection pool
3. Thêm index và so sánh query time trước/sau
