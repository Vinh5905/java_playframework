# Day 21 - Mini Project: CRUD API Với PostgreSQL

## Mục tiêu
- Tổng hợp Tuần 3 (database, forms, JSON)
- REST API đầy đủ với PostgreSQL backend
- Async repository pattern, validation, error handling

---

## Spec

```
GET    /api/todos              List todos (có phân trang)
POST   /api/todos              Tạo todo (validate input)
GET    /api/todos/:id          Lấy todo theo ID
PUT    /api/todos/:id          Cập nhật todo
DELETE /api/todos/:id          Xóa todo
GET    /api/todos/stats        Statistics (tổng, done, pending)
GET    /health                 Health check + DB connectivity
```

---

## Setup Và Chạy

```bash
# 1. Start PostgreSQL
docker run -d --name todo-postgres \
  -e POSTGRES_USER=todouser \
  -e POSTGRES_PASSWORD=todopass \
  -e POSTGRES_DB=todoapp \
  -p 5432:5432 postgres:16-alpine

# 2. Chạy project
cd crud-api-postgres
sbt run

# Play Evolutions tự động tạo tables
```

---

## Test

```bash
# Tạo todos
curl -X POST http://localhost:9000/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title": "Learn Play Framework"}'

curl -X POST http://localhost:9000/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title": "Build REST API"}'

# List (phân trang)
curl "http://localhost:9000/api/todos?page=1&size=10"

# Get by ID
curl http://localhost:9000/api/todos/1

# Update
curl -X PUT http://localhost:9000/api/todos/1 \
  -H "Content-Type: application/json" \
  -d '{"title": "Learn Play Framework", "done": true}'

# Delete
curl -X DELETE http://localhost:9000/api/todos/2

# Stats
curl http://localhost:9000/api/todos/stats

# Health
curl http://localhost:9000/health
```

---

## Điểm Học Được Từ Tuần 3

1. **Day 15**: Play Evolutions tạo schema tự động
2. **Day 16**: JPA alternative (dùng plain JDBC cho simplicity)
3. **Day 17**: Async JDBC pattern với custom dispatcher
4. **Day 18**: Form validation (title required, maxLength)
5. **Day 19**: JSON serialization với Jackson annotations
6. **Day 20**: HikariCP pool size = dispatcher thread count

---

## Cấu Trúc Code

```
crud-api-postgres/
├── app/
│   ├── controllers/TodoController.java  ← Thin, chỉ HTTP concerns
│   ├── models/Todo.java                 ← Domain model + Jackson annotations
│   ├── repositories/TodoRepository.java ← Async JDBC queries
│   └── services/TodoService.java        ← Business logic (pagination, etc.)
├── conf/
│   ├── evolutions/default/1.sql         ← Schema
│   ├── application.conf                 ← DB + dispatchers config
│   └── routes
└── build.sbt
```

Xem code trong `crud-api-postgres/` thư mục này để chạy.
