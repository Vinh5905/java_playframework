# Day 37 - Configuration, Logging & I18N

## Mục tiêu
- HOCON config sâu hơn (includes, substitutions, environment)
- Structured logging với Logback
- Internationalization (i18n) - đa ngôn ngữ
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

## 5. Log Markers - Enrichment & Filtering

Markers cho phép thêm metadata vào log và filter theo marker:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // Định nghĩa markers
    private static final Marker PAYMENT = MarkerFactory.getMarker("PAYMENT");
    private static final Marker SECURITY = MarkerFactory.getMarker("SECURITY");

    public void processPayment(String orderId, BigDecimal amount) {
        // Log với marker - dễ filter trong log aggregator
        log.info(PAYMENT, "Processing payment orderId={} amount={}", orderId, amount);
    }

    public void fraudDetected(String userId) {
        log.warn(SECURITY, "Fraud detected for userId={}", userId);
    }
}
```

```xml
<!-- logback.xml: Filter chỉ ghi SECURITY markers vào file riêng -->
<appender name="SECURITY_FILE" class="ch.qos.logback.core.FileAppender">
    <file>/var/log/security.log</file>
    <filter class="ch.qos.logback.core.filter.EvaluatorFilter">
        <evaluator class="ch.qos.logback.classic.boolex.OnMarkerEvaluator">
            <marker>SECURITY</marker>
        </evaluator>
        <onMatch>ACCEPT</onMatch>
        <onMismatch>DENY</onMismatch>
    </filter>
    <encoder><pattern>%date [%level] %message%n</pattern></encoder>
</appender>
```

> **Lưu ý**: `play.Logger` static methods đã bị **deprecated từ Play 2.7+**. Luôn dùng `LoggerFactory.getLogger(MyClass.class)` từ SLF4J.

---

## 6. Log Levels

| Level | Dùng khi |
|-------|---------|
| ERROR | Lỗi nghiêm trọng, cần action ngay |
| WARN | Vấn đề không fatal nhưng đáng chú ý |
| INFO | Sự kiện quan trọng (request đến, user login) |
| DEBUG | Chi tiết cho debugging (DB query, HTTP response) |
| TRACE | Rất chi tiết (chỉ dùng khi debug cụ thể) |

**Production**: INFO level cho app, WARN cho thư viện

---

## 7. Internationalization (I18N) - Đa Ngôn Ngữ

### 7.1 Cấu hình ngôn ngữ

```hocon
# application.conf
play.i18n.langs = ["en", "vi", "fr"]
```

### 7.2 Message Files

```
conf/
├── messages           ← Default (fallback cho tất cả)
├── messages.en        ← Tiếng Anh
├── messages.vi        ← Tiếng Việt
└── messages.fr        ← Tiếng Pháp
```

```properties
# conf/messages (default)
hello=Hello
welcome=Welcome, {0}!
items.count=You have {0} items

# conf/messages.vi
hello=Xin chào
welcome=Chào mừng, {0}!
items.count=Bạn có {0} mục
```

> **Lưu ý**: Dùng `{0}`, `{1}` cho parameter substitution (java.text.MessageFormat). Dấu nháy đơn `'` là escape character - dùng `''` để hiển thị literal apostrophe.

### 7.3 Dùng MessagesApi Trong Controller

```java
import play.i18n.MessagesApi;
import play.i18n.Messages;
import play.i18n.Lang;

public class HomeController extends Controller {

    private final MessagesApi messagesApi;

    @Inject
    public HomeController(MessagesApi messagesApi) {
        this.messagesApi = messagesApi;
    }

    public Result index(Http.Request request) {
        // Tự động negotiate từ Accept-Language header + PLAY_LANG cookie
        Messages messages = messagesApi.preferred(request);

        String greeting = messages.at("hello");
        String welcome = messages.at("welcome", "Alice");  // → "Welcome, Alice!"

        return ok(Json.newObject()
            .put("greeting", greeting)
            .put("welcome", welcome));
    }

    // Chỉ định ngôn ngữ tường minh
    public Result french(Http.Request request) {
        Messages messages = messagesApi.preferred(List.of(Lang.forCode("fr")));
        return ok(messages.at("hello"));
    }
}
```

### 7.4 Language Negotiation (Thứ Tự Ưu Tiên)

```
1. request.withTransientLang(lang)  ← Explicit trong code
2. Cookie "PLAY_LANG"               ← User preference đã lưu
3. Accept-Language header           ← Browser preference
4. play.i18n.langs[0]              ← Default của app
```

### 7.5 Đổi Ngôn Ngữ

```java
// Tạm thời (chỉ request này)
Http.Request localizedRequest = request.withTransientLang(Lang.forCode("vi"));

// Vĩnh viễn (lưu vào cookie cho requests sau)
public Result setLanguage(Http.Request request, String lang) {
    Lang newLang = Lang.forCode(lang);
    return redirect(routes.HomeController.index())
        .withLang(newLang, messagesApi);
    // → Set cookie PLAY_LANG=vi
}
```

### 7.6 I18N Trong Twirl Templates

```html
@* Template nhận Messages object *@
@()(implicit messages: Messages)

<h1>@messages("hello")</h1>
<p>@messages("welcome", user.name)</p>

@* Shorthand *@
<p>@Messages("hello")</p>
```

```java
// Controller truyền implicit messages
public Result show(Http.Request request) {
    Messages messages = messagesApi.preferred(request);
    return ok(views.html.index.render(messages));
}
