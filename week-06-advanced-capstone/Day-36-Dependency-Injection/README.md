# Day 36 - Dependency Injection Nâng Cao với Guice

## Mục tiêu
- Guice bindings: interface → implementation
- Singleton, scopes
- Module configuration
- Eager singletons (khởi động trước khi nhận request)

---

## 1. Tại Sao DI?

```java
// Không DI: hard dependency, không test được
public class UserController {
    private UserRepository repo = new UserRepository();  // Hardcode!
}

// Có DI: inject dependency, dễ test với mock
public class UserController {
    private final UserRepository repo;

    @Inject
    public UserController(UserRepository repo) {
        this.repo = repo;  // Guice inject implementation
    }
}
```

---

## 2. Guice Module

```java
// app/modules/AppModule.java
package modules;

import com.google.inject.AbstractModule;
import repositories.UserRepository;
import repositories.UserRepositoryImpl;
import services.EmailService;
import services.SmtpEmailService;

public class AppModule extends AbstractModule {
    @Override
    protected void configure() {
        // Bind interface → implementation
        bind(UserRepository.class).to(UserRepositoryImpl.class);

        // Bind với Singleton scope (một instance cho cả app)
        bind(EmailService.class)
            .to(SmtpEmailService.class)
            .asEagerSingleton();  // Khởi tạo ngay khi app start

        // Bind class trực tiếp (không cần interface)
        // Play tự inject nếu có @Singleton annotation
    }
}
```

```hocon
# application.conf
play.modules.enabled += "modules.AppModule"
```

---

## 3. Các Loại Scope

```java
// Singleton: 1 instance cho toàn app
@Singleton
public class CacheService {
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
}

// No scope (default): 1 instance per injection
public class UserForm {
    public String email;
    public String name;
}

// Per-request scope: 1 instance per HTTP request
// (Play không có built-in, cần implement custom scope)
```

---

## 4. Eager Singleton vs Lazy Singleton

```java
// Lazy singleton (default): tạo khi lần đầu được inject
@Singleton
public class LazyService {
    public LazyService() {
        System.out.println("LazyService created");
        // Chỉ chạy khi có request đầu tiên dùng service này
    }
}

// Eager singleton: tạo ngay khi app start
// Dùng cho: warmup connections, background jobs, validation startup config
public class AppModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DbHealthChecker.class).asEagerSingleton();
        bind(BackgroundJobScheduler.class).asEagerSingleton();
    }
}
```

---

## 5. @Provides Method

```java
public class AppModule extends AbstractModule {
    @Provides
    @Singleton
    public ObjectMapper provideObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Provides
    public HttpClient provideHttpClient(Config config) {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.getLong("http.connectTimeout")))
            .build();
    }
}
```

---

## 6. Test Với DI Overrides

```java
@Test
void testWithMockRepository() {
    UserRepository mockRepo = mock(UserRepository.class);
    when(mockRepo.findAll()).thenReturn(...);

    Application app = new GuiceApplicationBuilder()
        .overrides(bind(UserRepository.class).toInstance(mockRepo))
        .build();

    // Test với mock repository
}
```

---

## 7. Named Bindings

```java
// Khi cần nhiều implementation của cùng interface
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class AppModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CacheService.class)
            .annotatedWith(Names.named("redis"))
            .to(RedisCacheService.class);

        bind(CacheService.class)
            .annotatedWith(Names.named("memory"))
            .to(MemoryCacheService.class);
    }
}

// Inject named binding
public class UserService {
    @Inject
    @Named("redis")
    private CacheService cache;
}
```
