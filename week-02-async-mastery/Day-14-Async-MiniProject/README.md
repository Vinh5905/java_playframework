# Day 14 - Mini Project: Todo API (Async Version)

## Mục tiêu
- Refactor Todo API từ Day 07 sang async
- So sánh code trước/sau để thấy sự khác biệt
- Benchmark để đo throughput tăng bao nhiêu

---

## So Sánh Sync vs Async

### Repository (Day 07 vs Day 14)

```java
// Day 07: SYNC
public List<Todo> findAll() {
    return new ArrayList<>(store.values());
}

// Day 14: ASYNC
public CompletionStage<List<Todo>> findAll() {
    return CompletableFuture.supplyAsync(
        () -> new ArrayList<>(store.values()),
        (ExecutionContextExecutor) blockingEc
    );
}
```

### Controller (Day 07 vs Day 14)

```java
// Day 07: SYNC
public Result list() {
    return ok(Json.toJson(repository.findAll()));
}

// Day 14: ASYNC
public CompletionStage<Result> list() {
    return repository.findAll()
        .thenApply(todos -> ok(Json.toJson(todos)))
        .exceptionally(t -> internalServerError(errorJson(t.getMessage())));
}
```

---

## Chạy Và Benchmark

```bash
cd todo-api-async
sbt run

# Test API
curl http://localhost:9000/todos
curl -X POST http://localhost:9000/todos \
  -H "Content-Type: application/json" \
  -d '{"title": "Async Todo"}'

# Benchmark so với Day 07 sync version
# (Chạy cả 2 project cùng lúc trên port khác nhau)
ab -n 500 -c 100 http://localhost:9000/todos          # async
ab -n 500 -c 100 http://localhost:9001/todos          # sync (thêm -Dhttp.port=9001)
```

---

## Cấu Trúc

```
todo-api-async/
├── app/
│   ├── models/Todo.java              ← Giống Day 07
│   ├── repositories/
│   │   └── AsyncTodoRepository.java  ← Async version (CÓ custom EC)
│   └── controllers/
│       └── TodoController.java       ← Return CompletionStage<Result>
├── conf/
│   ├── routes
│   └── application.conf              ← Có blocking-io-dispatcher config
└── build.sbt
```
