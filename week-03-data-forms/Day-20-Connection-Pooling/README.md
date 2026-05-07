# Day 20 - Connection Pooling: HikariCP Tuning

## Mục tiêu
- Hiểu cách HikariCP hoạt động
- Tune pool size đúng cách
- Monitor và debug connection issues

---

## 1. HikariCP là gì?

HikariCP là JDBC connection pool mặc định của Play (thay thế BoneCP từ Play 2.4+).

```
App Code
  │
  ▼
HikariCP Pool [conn1, conn2, ..., connN]
  │
  ▼
PostgreSQL (max_connections config)
```

HikariCP giữ sẵn pool connections, khi có request:
1. Mượn connection từ pool (nếu có sẵn - fast)
2. Nếu không có → đợi đến `connectionTimeout` (ms)
3. Nếu timeout → throw `SQLTimeoutException`
4. Sau khi xong → trả connection về pool

---

## 2. Cấu Hình HikariCP

```hocon
db.default.hikaricp {
  # Số connection tối đa (quan trọng nhất)
  # Công thức: (number_of_cores * 2) + effective_spindle_count
  # Ví dụ 4 core, 1 disk: (4 * 2) + 1 = 9 → làm tròn = 10
  maximumPoolSize = 10

  # Số connection tối thiểu duy trì
  minimumIdle = 2

  # Timeout chờ connection từ pool (default 30s)
  connectionTimeout = 30000

  # Timeout đóng idle connection (default 10min)
  idleTimeout = 600000

  # Thời gian sống tối đa của 1 connection (default 30min)
  # Giúp rotate connections, tránh stale connections
  maxLifetime = 1800000

  # Timeout validation query
  validationTimeout = 5000

  # Pool name (dễ identify trong JMX/logging)
  poolName = "PlayDefaultPool"

  # Bật JMX monitoring
  registerMbeans = true
}
```

---

## 3. Pool Size: Sai Lầm Phổ Biến

**Sai lầm**: Tăng pool size = tăng throughput.

**Thực tế**: Quá nhiều connections gây:
- DB server quá tải (PostgreSQL default max_connections = 100)
- Context switching overhead
- Memory tăng (mỗi connection tốn ~10MB RAM trên PostgreSQL)

**Công thức đúng (PostgreSQL wiki)**:
```
max_pool_size = (number_of_cores * 2) + effective_spindle_count

Với:
- number_of_cores = số CPU cores của DB server
- effective_spindle_count = số disk spindles (SSD → 1)

Ví dụ: 8 core server, SSD:
max_pool_size = (8 * 2) + 1 = 17 → dùng 15-20
```

**Số thread của dispatcher phải = pool size:**
```hocon
# Sai: 50 threads nhưng chỉ 10 DB connections → 40 threads chờ connection
blocking-db-dispatcher.thread-pool-executor.fixed-pool-size = 50
db.default.hikaricp.maximumPoolSize = 10

# Đúng: match nhau
blocking-db-dispatcher.thread-pool-executor.fixed-pool-size = 10
db.default.hikaricp.maximumPoolSize = 10
```

---

## 4. Monitor Connection Pool

```java
// Logging HikariCP metrics
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import play.db.Database;

@Singleton
public class DbMonitor {
    @Inject
    public DbMonitor(Database db) {
        // Cast để lấy HikariDataSource
        HikariDataSource ds = (HikariDataSource) db.getDataSource();
        // Log pool stats mỗi 30s
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            log.info("DB Pool - Active: {}, Idle: {}, Total: {}, Awaiting: {}",
                ds.getHikariPoolMXBean().getActiveConnections(),
                ds.getHikariPoolMXBean().getIdleConnections(),
                ds.getHikariPoolMXBean().getTotalConnections(),
                ds.getHikariPoolMXBean().getThreadsAwaitingConnection()
            );
        }, 0, 30, TimeUnit.SECONDS);
    }
}
```

---

## 5. Diagnose Connection Pool Issues

**Triệu chứng**: `SQLTimeoutException: Connection is not available, request timed out after 30000ms`

**Nguyên nhân có thể:**
1. Pool size quá nhỏ so với concurrent requests
2. Query chậm giữ connection lâu
3. Transaction không được close (connection leak)
4. DB server quá tải

**Debug:**
```bash
# Xem active connections trên PostgreSQL
psql -c "SELECT count(*), state FROM pg_stat_activity GROUP BY state;"

# Xem slow queries
psql -c "SELECT query, calls, mean_exec_time FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;"
```
