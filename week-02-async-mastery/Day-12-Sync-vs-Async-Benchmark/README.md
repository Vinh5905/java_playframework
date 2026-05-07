# Day 12 - Benchmark: Sync vs Async vs Async With Blocking EC

## Mục tiêu
- Tự tay đo và CHỨNG KIẾN sự khác biệt
- Hiểu sâu: async không phải phép màu, dùng sai vẫn chậm
- Biết đọc kết quả Apache Bench (ab)

---

## Cách Chạy Benchmark

```bash
cd benchmark-project
sbt run

# Cài Apache Bench (macOS đã có sẵn)
which ab

# BƯỚC 1: Warm up JVM
curl http://localhost:9000/bench/sync
curl http://localhost:9000/bench/async-wrong
curl http://localhost:9000/bench/async-right

# BƯỚC 2: Benchmark
# 500 requests, 50 concurrent
ab -n 500 -c 50 http://localhost:9000/bench/sync
ab -n 500 -c 50 http://localhost:9000/bench/async-wrong
ab -n 500 -c 50 http://localhost:9000/bench/async-right
```

---

## Kết Quả Mong Đợi

Mỗi endpoint giả lập I/O mất 500ms:

| Endpoint | Throughput | Time/request | Lý giải |
|----------|-----------|-------------|---------|
| `/bench/sync` | ~8 req/s | ~6000ms | Block default EC 8 threads |
| `/bench/async-wrong` | ~12 req/s | ~4000ms | Block ForkJoinPool, khá hơn 1 chút |
| `/bench/async-right` | ~80 req/s | ~625ms | Custom EC 50 threads, gần lý tưởng |

---

## Đọc Output của ab

```
Concurrency Level:      50
Time taken for tests:   6.234 seconds
Complete requests:      500
Failed requests:        0

Requests per second:    80.21 [#/sec] (mean)   ← QUAN TRỌNG NHẤT
Time per request:       623.386 [ms] (mean)
Time per request:       12.468 [ms] (mean, across all concurrent requests)

Percentage of the requests served within a certain time (ms)
  50%    610
  66%    625
  75%    630
  80%    635
  90%    645
  95%    660
  98%    690
  99%    700        ← P99: 99% request trả về trong 700ms
 100%   1200        ← Max (có thể là outlier)
```

---

## Cấu Trúc Code

Xem `benchmark-project/app/controllers/BenchmarkController.java`
