# Day 37 - Configuration & Logging

## Mục tiêu
- HOCON config sâu hơn (includes, substitutions, environment)
- Structured logging với Logback
- Log levels và production logging

---

## 1. HOCON Advanced

```hocon
# conf/application.conf - Base config

# Include file khác
include "database.conf"
include "security.conf"

# Substitution từ environment variable
# ${?VAR} = nếu VAR không tồn tại → bỏ qua (không lỗi)
# ${VAR}  = nếu VAR không tồn tại → lỗi khi start
play.http.secret.key = ${?APP_SECRET}

# Substitution với fallback
db.default.url = "jdbc:postgresql://localhost:5432/mydb"
db.default.url = ${?DATABASE_URL}  # Override nếu có

# Object merge
myapp {
  timeout = 30s

  # Overriding chỉ 1 field trong nested object
  server {
    host = localhost
    port = 9000
  }
}

# Duration
http.timeout = 30 seconds
cache.ttl = 5 minutes

# Bytes
buffer.size = 10 MB
```

```hocon
# conf/prod.conf - Production override
include "application.conf"

play.filters.hosts.allowed = ["api.myapp.com"]
play.evolutions.db.default.autoApply = false
```

---

## 2. Đọc Config Trong Code

```java
import com.typesafe.config.Config;
import javax.inject.Inject;

@Singleton
public class AppConfig {

    private final String appName;
    private final int maxRetries;
    private final Duration timeout;
    private final List<String> allowedDomains;

    @Inject
    public AppConfig(Config config) {
        this.appName = config.getString("myapp.name");
        this.maxRetries = config.getInt("myapp.maxRetries");
        this.timeout = config.getDuration("myapp.timeout");
        this.allowedDomains = config.getStringList("myapp.allowedDomains");
    }

    // Getters...
}
```

---

## 3. Logback Configuration

```xml
<!-- conf/logback.xml - Production config -->
<configuration>
  <!-- Console appender (stdout) -->
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <!-- JSON format cho log aggregators (Splunk, ELK) -->
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
  </appender>

  <!-- File appender với rotation -->
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>/var/log/myapp/application.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
      <fileNamePattern>/var/log/myapp/application.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
      <maxFileSize>100MB</maxFileSize>
      <maxHistory>30</maxHistory>
      <totalSizeCap>3GB</totalSizeCap>
    </rollingPolicy>
    <encoder>
      <pattern>%date{ISO8601} [%level] %logger{40} - %message%n%xException</pattern>
    </encoder>
  </appender>

  <!-- Levels cho từng package -->
  <logger name="play" level="WARN"/>
  <logger name="application" level="INFO"/>
  <logger name="controllers" level="DEBUG"/>
  <logger name="repositories" level="INFO"/>
  <logger name="org.hibernate" level="WARN"/>

  <root level="WARN">
    <appender-ref ref="STDOUT"/>
    <!-- <appender-ref ref="FILE"/> → Enable trong production -->
  </root>
</configuration>
```

---

## 4. Structured Logging

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;  // Mapped Diagnostic Context

@Singleton
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public CompletionStage<User> findById(Long id) {
        // Thêm context vào log (có thể dùng trong log pattern)
        MDC.put("userId", id.toString());
        MDC.put("operation", "findUser");

        log.info("Finding user");  // Log với MDC context

        return userRepo.findById(id)
            .thenApply(user -> {
                log.info("Found user: {}", user.email);
                return user;
            })
            .exceptionally(t -> {
                log.error("Failed to find user {}: {}", id, t.getMessage(), t);
                throw new RuntimeException(t);
            })
            .whenComplete((u, t) -> MDC.clear());
    }
}
```

```xml
<!-- Logback pattern với MDC -->
<pattern>%date [%level] [userId=%X{userId}] [op=%X{operation}] %logger - %message%n</pattern>
<!-- Output: 2024-01-15 [INFO] [userId=42] [op=findUser] UserService - Found user: alice@example.com -->
```

---

## 5. Log Levels

| Level | Dùng khi |
|-------|---------|
| ERROR | Lỗi nghiêm trọng, cần action ngay |
| WARN | Vấn đề không fatal nhưng đáng chú ý |
| INFO | Sự kiện quan trọng (request đến, user login) |
| DEBUG | Chi tiết cho debugging (DB query, HTTP response) |
| TRACE | Rất chi tiết (chỉ dùng khi debug cụ thể) |

**Production**: INFO level cho app, WARN cho thư viện
