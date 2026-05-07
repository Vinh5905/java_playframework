# Day 15 - Database Setup: PostgreSQL + Play Evolutions

## Mục tiêu
- Cài PostgreSQL qua Docker
- Cấu hình JDBC trong Play
- Dùng Play Evolutions để quản lý schema

---

## 1. Cài PostgreSQL Bằng Docker

```bash
# Cài Docker Desktop nếu chưa có
brew install --cask docker

# Chạy PostgreSQL container
docker run -d \
  --name play-postgres \
  -e POSTGRES_USER=playuser \
  -e POSTGRES_PASSWORD=playpass \
  -e POSTGRES_DB=playapp \
  -p 5432:5432 \
  postgres:16-alpine

# Verify đang chạy
docker ps
docker logs play-postgres

# Connect thử
docker exec -it play-postgres psql -U playuser -d playapp
```

---

## 2. Cấu Hình JDBC Trong build.sbt

```scala
libraryDependencies ++= Seq(
  jdbc,                // Play JDBC support
  evolutions,          // Play Evolutions (migrations)
  "org.postgresql" % "postgresql" % "42.7.4"  // PostgreSQL JDBC driver
)
```

---

## 3. Cấu Hình application.conf

```hocon
# Cấu hình database
db {
  default {
    driver = "org.postgresql.Driver"
    url = "jdbc:postgresql://localhost:5432/playapp"
    username = "playuser"
    password = "playpass"

    # Override bằng environment variables trong production
    # url = ${?DATABASE_URL}
    # username = ${?DATABASE_USER}
    # password = ${?DATABASE_PASSWORD}

    # HikariCP - connection pool
    hikaricp {
      # Số connection tối đa trong pool
      # Công thức: (số CPU cores * 2) + số disk (thường 10-20)
      maximumPoolSize = 10
      minimumIdle = 2

      # Timeout khi đợi connection từ pool (ms)
      connectionTimeout = 30000

      # Thời gian connection được phép idle (ms)
      idleTimeout = 600000

      # Thời gian sống tối đa của connection (ms)
      maxLifetime = 1800000
    }
  }
}

# Play Evolutions - tự động apply migrations
play.evolutions {
  db.default.enabled = true
  # CHỈ autoApply trong development!
  # Production: tắt autoApply, chạy migration thủ công
  db.default.autoApply = true
  db.default.autoApplyDowns = false  # Không tự rollback
}

# Dispatcher cho JDBC blocking calls
blocking-db-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    # Bằng với maximumPoolSize của HikariCP
    fixed-pool-size = 10
  }
  throughput = 1
}
```

---

## 4. Play Evolutions - Migration Files

Play Evolutions là migration tool có sẵn trong Play.

**Quy tắc đặt tên**: `conf/evolutions/default/N.sql` (N là số thứ tự)

```sql
-- conf/evolutions/default/1.sql
-- !Ups    ← Apply migration (bắt buộc)

CREATE TABLE todos (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    done BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_todos_done ON todos(done);

-- !Downs  ← Rollback migration (tùy chọn nhưng nên có)

DROP TABLE IF EXISTS todos;
```

```sql
-- conf/evolutions/default/2.sql
-- Thêm users table và foreign key

-- !Ups

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE todos ADD COLUMN user_id BIGINT REFERENCES users(id);

-- !Downs

ALTER TABLE todos DROP COLUMN IF EXISTS user_id;
DROP TABLE IF EXISTS users;
```

---

## 5. Dùng Database API Của Play

```java
import play.db.Database;
import javax.inject.Inject;
import java.sql.*;

@Singleton
public class TodoJdbcRepository {

    private final Database db;
    private final ExecutionContext dbEc;

    @Inject
    public TodoJdbcRepository(Database db, ActorSystem system) {
        this.db = db;
        this.dbEc = system.dispatchers().lookup("blocking-db-dispatcher");
    }

    public CompletionStage<List<Todo>> findAll() {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                List<Todo> todos = new ArrayList<>();
                String sql = "SELECT id, title, done, created_at FROM todos ORDER BY id";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        todos.add(mapRow(rs));
                    }
                }
                return todos;
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    public CompletionStage<Todo> save(String title) {
        return CompletableFuture.supplyAsync(
            () -> db.withTransaction(conn -> {
                String sql = "INSERT INTO todos (title) VALUES (?) RETURNING id, title, done, created_at";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, title);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    return mapRow(rs);
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    private Todo mapRow(ResultSet rs) throws SQLException {
        return new Todo(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getBoolean("done"),
            rs.getTimestamp("created_at").toInstant().toString()
        );
    }
}
```

---

## 6. Production Considerations

```hocon
# production.conf
include "application.conf"

# Tắt autoApply trong production!
play.evolutions.db.default.autoApply = false

# Secret key từ environment
play.http.secret.key = ${APP_SECRET}

# Database từ environment
db.default.url = ${DATABASE_URL}
db.default.username = ${DATABASE_USER}
db.default.password = ${DATABASE_PASSWORD}
```

**Tools migration tốt hơn cho production:**
- **Flyway**: Simple, Java-native, tốt cho SQL migrations
- **Liquibase**: Flexible hơn, support nhiều format

---

## 7. Bài Tập

Xem `database-setup-demo/` trong thư mục này:

```bash
# 1. Start PostgreSQL
docker run -d --name play-postgres \
  -e POSTGRES_PASSWORD=playpass -e POSTGRES_DB=playapp -e POSTGRES_USER=playuser \
  -p 5432:5432 postgres:16-alpine

# 2. Chạy project
cd database-setup-demo
sbt run

# Play Evolutions tự động create tables khi start

# 3. Test CRUD
curl -X POST http://localhost:9000/todos \
  -H "Content-Type: application/json" \
  -d '{"title": "DB Todo Test"}'

curl http://localhost:9000/todos
```
