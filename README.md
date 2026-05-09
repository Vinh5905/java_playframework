# Lộ Trình Học Play Framework (Java) - 6 Tuần

## Cách Sử Dụng

Mỗi thư mục ngày có:
- **`README.md`** - Lý thuyết chi tiết, giải thích khái niệm
- **Project code** (các ngày mini-project/review) - Chạy được ngay với `sbt run`

---

## Cấu Trúc

```
learn_playframework/
├── week-01-foundations/        ← Nền tảng (Day 01-07)
├── week-02-async-mastery/      ← Async - PHẦN QUAN TRỌNG NHẤT (Day 08-14)
├── week-03-data-forms/         ← Database & Forms (Day 15-21)
├── week-04-streaming-ws/       ← WebSocket & Streaming (Day 22-28)
├── week-05-security-testing/   ← Security & Testing (Day 29-35)
└── week-06-advanced-capstone/  ← Advanced & Capstone (Day 36-42)
```

---

## Projects Có Thể Chạy Ngay

| Project | Thư mục | Mô tả |
|---------|---------|-------|
| hello-play | `week-01/Day-01/02-first-project/hello-play/` | Project đầu tiên |
| todo-api-sync | `week-01/Day-07/todo-api-sync/` | Todo CRUD (sync) |
| benchmark | `week-02/Day-12/benchmark-project/` | So sánh sync vs async |
| todo-api-async | `week-02/Day-14/todo-api-async/` | Todo CRUD (async) |
| url-shortener | `week-06/Day-42/url-shortener/` | Capstone project đầy đủ |

**Chạy bất kỳ project nào:**
```bash
cd <project-folder>
sbt run
# → http://localhost:9000
```

---

## Yêu Cầu Hệ Thống

- Java 17+ (`java -version`)
- sbt 1.10+ (`sbt --version`)
- Docker (cho Day 15+ dùng PostgreSQL)

---

## Lộ Trình Học

### Tuần 1: Nền Tảng
| Ngày | Chủ đề | File học |
|------|--------|---------|
| 01 | Cài đặt môi trường | `week-01/Day-01/README.md` |
| 02 | Giải phẫu project | `week-01/Day-02/README.md` |
| 03 | Routing | `week-01/Day-03/README.md` |
| 04 | Controllers & Actions | `week-01/Day-04/README.md` |
| 05 | Request & Response | `week-01/Day-05/README.md` |
| 06 | Twirl Templates | `week-01/Day-06/README.md` |
| 07 | **Mini Project: Todo API (sync)** | `week-01/Day-07/todo-api-sync/` |

### Tuần 2: Async (QUAN TRỌNG NHẤT) ⭐
| Ngày | Chủ đề | File học |
|------|--------|---------|
| 08 | Thread Model | `week-02/Day-08/README.md` |
| 09 | CompletionStage | `week-02/Day-09/README.md` |
| 10 | Async Actions | `week-02/Day-10/README.md` |
| 11 | Execution Contexts | `week-02/Day-11/README.md` |
| 12 | **Benchmark Project** | `week-02/Day-12/benchmark-project/` |
| 13 | Async Pitfalls | `week-02/Day-13/README.md` |
| 14 | **Mini Project: Todo API (async)** | `week-02/Day-14/todo-api-async/` |

### Tuần 3: Database & Forms
| Ngày | Chủ đề | File học |
|------|--------|---------|
| 15 | Database Setup (HikariCP + PostgreSQL) | `week-03/Day-15/README.md` |
| 16 | JPA & Hibernate | `week-03/Day-16/README.md` |
| 17 | Async JDBC + DatabaseExecutionContext | `week-03/Day-17/README.md` |
| 18 | Forms & Validation (Bean Validation, cross-field, groups) | `week-03/Day-18/README.md` |
| 19 | JSON & Jackson | `week-03/Day-19/README.md` |
| 20 | Connection Pooling | `week-03/Day-20/README.md` |
| 21 | **Mini Project: CRUD API** | `week-03/Day-21/README.md` |

### Tuần 4: Streaming & WS
| Ngày | Chủ đề | File học |
|------|--------|---------|
| 22 | WS Client (HTTP calls, auth, streaming) | `week-04/Day-22/README.md` |
| 23 | Pekko Streams Basics | `week-04/Day-23/README.md` |
| 24 | WebSocket | `week-04/Day-24/README.md` |
| 25 | Server-Sent Events | `week-04/Day-25/README.md` |
| 26 | File Upload & Streaming | `week-04/Day-26/README.md` |
| 27 | Reactive Patterns | `week-04/Day-27/README.md` |
| 28 | **Mini Project: Realtime** | `week-04/Day-28/README.md` |

### Tuần 5: Security & Testing
| Ngày | Chủ đề | File học |
|------|--------|---------|
| 29 | Filters & Middleware | `week-05/Day-29/README.md` |
| 30 | Authentication (JWT) | `week-05/Day-30/README.md` |
| 31 | Authorization & CSRF (annotations, test helper) | `week-05/Day-31/README.md` |
| 32 | Error Handling (global, JsonHttpErrorHandler) | `week-05/Day-32/README.md` |
| 33 | Unit Testing (GuiceApplicationBuilder, GuiceInjectorBuilder) | `week-05/Day-33/README.md` |
| 34 | Integration Testing | `week-05/Day-34/README.md` |
| 35 | Load Testing | `week-05/Day-35/README.md` |

### Tuần 6: Advanced & Capstone
| Ngày | Chủ đề | File học |
|------|--------|---------|
| 36 | DI Nâng Cao (Lifecycle, Provider, @ImplementedBy, Compile-Time DI) | `week-06/Day-36/README.md` |
| 37 | Configuration, Logging & **I18N** | `week-06/Day-37/README.md` |
| 38 | Performance Tuning | `week-06/Day-38/README.md` |
| 39 | Caching (Named caches, Caffeine) | `week-06/Day-39/README.md` |
| 40 | Production Build | `week-06/Day-40/README.md` |
| 41 | Docker Deploy | `week-06/Day-41/README.md` |
| 42 | **Capstone Project** | `week-06/Day-42/url-shortener/` |

---

## Top Sai Lầm Cần Tránh

1. Blocking code trên default EC → tách dispatcher
2. `.get()/.join()` → dùng `thenApply/thenCompose`
3. `thenApply` với function trả CompletionStage → dùng `thenCompose`
4. Quên `exceptionally` → exception bị nuốt
5. Route generic trước route cụ thể → 404 hoặc wrong match
6. Lưu data nhạy cảm trong session cookie

---

## Benchmark Nhanh

```bash
# Sau khi chạy benchmark-project
ab -n 200 -c 50 http://localhost:9000/bench/sync       # ~16 req/s
ab -n 200 -c 50 http://localhost:9000/bench/async-right # ~80 req/s
```
