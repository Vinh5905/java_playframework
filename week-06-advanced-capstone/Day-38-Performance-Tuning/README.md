# Day 38 - Performance Tuning

## Mục tiêu
- JVM tuning cho Play
- Pekko HTTP server config
- Profiling với async-profiler
- Monitoring metrics

---

## 1. JVM Tuning

```bash
# Thêm vào conf/jvm.options hoặc start script
-server
-Xmx2g                          # Max heap: 2GB
-Xms512m                        # Initial heap: 512MB
-XX:+UseG1GC                    # G1 GC (Java 9+ default, tốt cho latency)
-XX:MaxGCPauseMillis=200        # Target GC pause < 200ms
-XX:+HeapDumpOnOutOfMemoryError # Dump heap khi OOM
-XX:HeapDumpPath=/tmp/heapdump.hprof
-XX:+DisableExplicitGC          # Tắt System.gc()
-Djava.net.preferIPv4Stack=true
```

---

## 2. Pekko HTTP Server Tuning

```hocon
# application.conf
pekko.http.server {
  # Số connections tối đa
  max-connections = 1024

  # Timeout cho idle connections
  idle-timeout = 75s

  # Request timeout
  request-timeout = 20s

  # Max request size
  parsing.max-content-length = 10m

  # Pipelining
  pipelining-limit = 16
}

# Pekko dispatcher tuning
pekko.actor.default-dispatcher {
  fork-join-executor {
    parallelism-min = 4
    parallelism-factor = 2.0  # threads = cores * 2
    parallelism-max = 32
  }
}
```

---

## 3. Benchmark Với wrk

```bash
# Cài wrk (HTTP benchmarking tool)
brew install wrk

# Basic benchmark
wrk -t4 -c100 -d30s http://localhost:9000/todos
# -t4: 4 threads
# -c100: 100 connections
# -d30s: 30 seconds duration

# Output:
# Requests/sec:  15234.56
# Latency:        6.54ms mean, 12.34ms max
# Transfer/sec:   3.21MB

# Benchmark với custom script (POST)
wrk -t4 -c100 -d30s -s create-todo.lua http://localhost:9000/todos

# create-todo.lua:
# wrk.method = "POST"
# wrk.body   = '{"title":"Load test todo"}'
# wrk.headers["Content-Type"] = "application/json"
```

---

## 4. Profiling Với async-profiler

```bash
# Download async-profiler
curl -L -o async-profiler.tar.gz \
  https://github.com/async-profiler/async-profiler/releases/download/v3.0/async-profiler-3.0-macos.zip

# Profile Play app (thay PID)
./profiler.sh -d 30 -f profile.html $(jps | grep play | awk '{print $1}')

# Mở profile.html trong browser → thấy flamegraph
```

---

## 5. Metrics Với Micrometer

```scala
// build.sbt
libraryDependencies += "io.micrometer" % "micrometer-core" % "1.12.4"
libraryDependencies += "io.micrometer" % "micrometer-registry-prometheus" % "1.12.4"
```

```java
@Singleton
public class MetricsController extends Controller {

    private final MeterRegistry registry;

    @Inject
    public MetricsController(MeterRegistry registry) {
        this.registry = registry;
    }

    // GET /metrics → Prometheus format
    public Result metrics() {
        PrometheusMeterRegistry prometheusRegistry = (PrometheusMeterRegistry) registry;
        return ok(prometheusRegistry.scrape()).as("text/plain; version=0.0.4");
    }
}

// Instrument business logic
@Singleton
public class TodoService {
    private final Counter createCounter;
    private final Timer findTimer;

    @Inject
    public TodoService(MeterRegistry registry) {
        createCounter = registry.counter("todos.created");
        findTimer = registry.timer("todos.find.duration");
    }

    public CompletionStage<Todo> create(String title) {
        return todoRepo.save(title)
            .thenApply(todo -> {
                createCounter.increment();
                return todo;
            });
    }
}
```

---

## 6. Common Performance Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| High latency, low CPU | Blocking on default EC | Custom dispatcher |
| High CPU, slow | N+1 queries | Batch queries |
| Memory leak | Large session data | Slim sessions |
| Connection timeout | Pool too small | Increase pool size |
| GC pauses | Small heap | Increase -Xmx |
| Thread starvation | Too many blocking calls | Async everywhere |
