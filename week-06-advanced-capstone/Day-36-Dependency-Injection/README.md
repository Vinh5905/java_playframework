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

## 7. ApplicationLifecycle - Cleanup Khi App Dừng

```java
import play.inject.ApplicationLifecycle;
import java.util.concurrent.CompletableFuture;

@Singleton
public class DatabasePool {

    private final HikariDataSource dataSource;

    @Inject
    public DatabasePool(ApplicationLifecycle lifecycle, Config config) {
        this.dataSource = createPool(config);

        // Đăng ký stop hook - chạy khi app shutdown
        lifecycle.addStopHook(() -> {
            dataSource.close();  // Đóng connection pool
            return CompletableFuture.completedFuture(null);
        });
    }
}

@Singleton
public class BackgroundWorker {

    private final ScheduledExecutorService executor;

    @Inject
    public BackgroundWorker(ApplicationLifecycle lifecycle) {
        this.executor = Executors.newScheduledThreadPool(2);

        // Cleanup khi app dừng
        lifecycle.addStopHook(() -> {
            executor.shutdown();
            return CompletableFuture.completedFuture(null);
        });
    }
}
```

> **Thứ tự stop**: Components stop theo thứ tự NGƯỢC lại khi tạo. Nếu A được tạo trước B, B sẽ stop trước A.

---

## 8. Provider Pattern - Giải Circular Dependencies

```java
import com.google.inject.Provider;

// Vấn đề: A cần B, B cần A → circular dependency
// Giải pháp: Inject Provider<B> vào A, defer instantiation

public class ServiceA {
    private final Provider<ServiceB> serviceBProvider;

    @Inject
    public ServiceA(Provider<ServiceB> serviceBProvider) {
        this.serviceBProvider = serviceBProvider;  // Không tạo B ngay
    }

    public void doWork() {
        ServiceB b = serviceBProvider.get();  // B được tạo lazily khi cần
        b.execute();
    }
}
```

---

## 9. @ImplementedBy - Binding Đơn Giản Không Cần Module

```java
// Thay vì viết Module để bind interface → impl
// Dùng @ImplementedBy trực tiếp trên interface

import com.google.inject.ImplementedBy;

@ImplementedBy(UserRepositoryImpl.class)
public interface UserRepository {
    CompletionStage<Optional<User>> findById(Long id);
    CompletionStage<User> save(User user);
}

// Guice tự động bind UserRepository → UserRepositoryImpl
// Không cần AppModule.configure()
```

> **Khi nào dùng `@ImplementedBy` vs Module**: `@ImplementedBy` cho binding đơn giản. Module khi cần scope, qualifiers, hoặc `@Provides`.

---

## 10. Named Bindings

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

---

## 11. Compile-Time DI (Nâng Cao)

Play hỗ trợ **Compile-Time DI** - wiring được kiểm tra lúc compile, không phải runtime:

```java
// app/AppLoader.java - ApplicationLoader thay thế Guice
public class AppLoader implements ApplicationLoader {
    @Override
    public Application load(Context context) {
        return new AppComponents(context).application();
    }
}

// app/AppComponents.java - Wiring tường minh
public class AppComponents extends BuiltInComponentsFromContext
        implements HttpFiltersComponents {

    public AppComponents(ApplicationLoader.Context context) {
        super(context);
        // Wiring xảy ra ở đây, fail nhanh nếu thiếu dependency
    }

    // Manually wire dependencies
    @Override
    public Router router() {
        UserRepository repo = new UserRepositoryImpl(dbApi());
        UserController controller = new UserController(repo);
        return new _root_.router.Routes(httpErrorHandler(), controller);
    }
}
```

```hocon
# application.conf - Dùng AppLoader thay Guice
play.application.loader = "AppLoader"
```

**Khi nào dùng Compile-Time DI:**
- Muốn lỗi wiring được phát hiện sớm (compile time, không phải startup)
- Team muốn dependency graph rõ ràng
- Không thích annotation magic

**Tradeoff**: Boilerplate nhiều hơn, nhưng an toàn hơn runtime DI.
