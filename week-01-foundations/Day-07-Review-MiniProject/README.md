# Day 07 - Mini Project: Todo API (Sync Version)

## Mục tiêu
- Tổng hợp kiến thức Tuần 1 (routing, controller, request/response, JSON)
- Build REST API hoàn chỉnh: CRUD cho Todos
- **Chưa dùng async** - sẽ refactor sang async ở Day 14 và so sánh performance

---

## API Specification

| Method | URL | Description |
|--------|-----|-------------|
| GET | /todos | Lấy tất cả todos |
| GET | /todos/:id | Lấy todo theo ID |
| POST | /todos | Tạo todo mới |
| PUT | /todos/:id | Cập nhật todo |
| DELETE | /todos/:id | Xóa todo |
| GET | /todos/stats | Thống kê (tổng, done, pending) |

---

## Chạy Project

```bash
cd todo-api-sync
sbt run
# → http://localhost:9000
```

---

## Test Bằng curl

```bash
# Tạo todo
curl -X POST http://localhost:9000/todos \
  -H "Content-Type: application/json" \
  -H "Csrf-Token: nocheck" \
  -d '{"title": "Learn Play Framework", "done": false}'

# Lấy tất cả
curl http://localhost:9000/todos

# Lấy theo ID
curl http://localhost:9000/todos/1

# Cập nhật
curl -X PUT http://localhost:9000/todos/1 \
  -H "Content-Type: application/json" \
  -H "Csrf-Token: nocheck" \
  -d '{"title": "Learn Play Framework", "done": true}'

# Xóa
curl -X DELETE http://localhost:9000/todos/1 \
  -H "Csrf-Token: nocheck"

# Stats
curl http://localhost:9000/todos/stats
```

---

## Cấu Trúc Code

```
todo-api-sync/
├── app/
│   ├── models/Todo.java          ← Data model
│   ├── repositories/
│   │   └── TodoRepository.java   ← In-memory data store (Singleton)
│   └── controllers/
│       └── TodoController.java   ← HTTP handlers
├── conf/
│   ├── routes                    ← URL mapping
│   └── application.conf
└── build.sbt
```

---

## Điểm Cần Chú Ý

1. `@Singleton` trên Repository - Guice đảm bảo 1 instance cho toàn app
2. `@Inject` constructor - Dependency Injection
3. `ConcurrentHashMap` thay vì `HashMap` - thread-safe cho concurrent requests
4. `AtomicLong` cho ID generation - thread-safe
5. JSON tự động với `Json.toJson()` - Play dùng Jackson
6. Error handling: trả 404 khi không tìm thấy, 400 khi body không hợp lệ

---

## Kiến Thức Áp Dụng Từ Tuần 1

- Day 02: Cấu trúc project, routes file
- Day 03: Path params (`:id`), HTTP methods trong routes
- Day 04: Controller, Result types (ok, created, notFound, etc.)
- Day 05: Request body (`asJson()`), validation
