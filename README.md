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

### Tuần 3-6
Đọc README của từng ngày và làm bài tập trong đó.

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
