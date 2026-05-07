# Day 35 - Load Testing

## Mục tiêu
- Benchmark với Apache Bench (ab)
- Benchmark với wrk
- Interpret kết quả đúng cách
- Tìm bottleneck

---

## 1. Apache Bench (ab)

```bash
# Đã có sẵn trên macOS
which ab

# Basic: 1000 requests, 50 concurrent
ab -n 1000 -c 50 http://localhost:9000/todos

# Với POST body
ab -n 500 -c 20 \
   -T "application/json" \
   -p create-body.json \
   http://localhost:9000/todos

# create-body.json:
echo '{"title":"Load test todo"}' > create-body.json

# Với auth header
ab -n 500 -c 20 \
   -H "Authorization: Bearer your-jwt-token" \
   http://localhost:9000/api/urls
```

---

## 2. Đọc Kết Quả ab

```
Concurrency Level:      50           ← Số concurrent connections
Time taken for tests:   12.345 secs
Complete requests:      1000
Failed requests:        0            ← Phải = 0 (hoặc rất ít)

Requests per second:    81.00 [#/sec] (mean)  ← QUAN TRỌNG NHẤT
Time per request:       617.34 [ms] (mean)
Time per request:       12.35 [ms] (mean, across all concurrent)

Transfer rate:          256.34 [Kbytes/sec]

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    1   0.5      1      5
Processing:   480  610  45.2    600   1200
Waiting:      480  609  45.1    599   1199
Total:        481  611  45.3    601   1205

Percentage of the requests served within a certain time (ms)
  50%    601   ← Median: 50% requests < 601ms
  66%    620
  75%    635
  80%    645
  90%    680
  95%    750   ← P95: 95% requests < 750ms
  99%    900   ← P99: 99% requests < 900ms
 100%   1205   ← Max (có thể outlier)
```

**Metrics cần quan tâm:**
- `Requests per second` - throughput
- `Failed requests` - phải = 0
- P95, P99 latency - đuôi dài cho thấy vấn đề

---

## 3. wrk - Benchmark Nâng Cao

```bash
brew install wrk

# Basic
wrk -t4 -c100 -d30s http://localhost:9000/todos

# Với Lua script (POST với header)
cat > post-todo.lua << 'EOF'
wrk.method = "POST"
wrk.body   = '{"title":"wrk test todo"}'
wrk.headers["Content-Type"] = "application/json"
wrk.headers["Authorization"] = "Bearer your-token"
EOF

wrk -t4 -c100 -d30s -s post-todo.lua http://localhost:9000/todos
```

---

## 4. Interpret Results

```
Sync endpoint (1 giây delay):
  Requests/sec: ~16   ← 200 threads / ~12s (200 requests * 1s / ~threads)
  
Async endpoint (1 giây delay):
  Requests/sec: ~80   ← Custom EC 50 threads / ~0.6s per batch

Async non-blocking (no thread block):
  Requests/sec: ~200+ ← Giới hạn chỉ là network + event loop overhead
```

---

## 5. Tìm Bottleneck

```bash
# Tăng dần concurrent requests
ab -n 500 -c 10  http://localhost:9000/todos  # Baseline
ab -n 500 -c 50  http://localhost:9000/todos  # 5x concurrent
ab -n 500 -c 100 http://localhost:9000/todos  # 10x concurrent
ab -n 500 -c 200 http://localhost:9000/todos  # 20x concurrent

# Nếu Req/sec giảm đột ngột ở concurrent cao → tìm được bottleneck
# Thường là: thread pool exhausted, DB connections, CPU
```

---

## 6. Monitoring Khi Load Test

```bash
# Terminal 1: Load test
ab -n 1000 -c 100 http://localhost:9000/todos

# Terminal 2: Xem CPU và thread count
top -pid $(jps | grep play | cut -d' ' -f1)

# Terminal 3: Xem DB connections
watch -n 1 'psql -c "SELECT count(*), state FROM pg_stat_activity WHERE application_name != '"'"'psql'"'"' GROUP BY state"'
```

---

## 7. Bài Tập

1. Benchmark Todo API từ Day 14 (async)
2. So sánh với Day 07 (sync)
3. Tăng concurrent → tìm điểm breaking
4. Fix bottleneck → benchmark lại
5. Document: số liệu trước/sau + giải thích

```bash
# Quick comparison script
echo "=== SYNC ===" && ab -n 500 -c 50 http://localhost:9001/todos 2>&1 | grep "Req"
echo "=== ASYNC ===" && ab -n 500 -c 50 http://localhost:9000/todos 2>&1 | grep "Req"
```
