# Day 41 - Deploy với Docker

## Mục tiêu
- Build Docker image của Play app
- Docker Compose cho cả app + DB
- Production configuration

---

## 1. Build Production Package

```bash
# Build distribution package
sbt clean dist
# → target/universal/your-app-1.0.zip

# Hoặc build Docker image trực tiếp
sbt docker:publishLocal
```

---

## 2. Dockerfile (Manual)

```dockerfile
# Stage 1: Build
FROM sbtscala/scala-sbt:eclipse-temurin-17.0.10_7_1.10.0_2.13.13 AS builder
WORKDIR /build
COPY . .
RUN sbt clean stage

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy compiled app từ builder stage
COPY --from=builder /build/target/universal/stage /app

# Non-root user cho security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 9000

# Env vars sẽ override conf
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

CMD ["bin/your-app", \
     "-Dplay.http.secret.key=${APP_SECRET}", \
     "-Dconfig.file=conf/prod.conf", \
     "-Dhttp.port=9000"]
```

---

## 3. sbt-native-packager (Recommended)

Play đã include `sbt-native-packager`. Build Docker image:

```bash
# Build Docker image
sbt docker:publishLocal
# → Creates local image: your-app:1.0-SNAPSHOT

# Push to registry
sbt docker:publish
```

```scala
// build.sbt - Docker settings
enablePlugins(PlayJava, DockerPlugin)

dockerBaseImage := "eclipse-temurin:17-jre-alpine"
dockerExposedPorts := Seq(9000)
dockerUsername := Some("yourdockerhub")

Docker / maintainer := "you@example.com"

// JVM options trong Docker
javaOptions in Universal ++= Seq(
  "-Dpidfile.path=/dev/null",
  "-J-Xmx512m"
)
```

---

## 4. Docker Compose

```yaml
# docker-compose.yml
version: '3.8'

services:
  app:
    image: url-shortener:1.0-SNAPSHOT
    ports:
      - "9000:9000"
    environment:
      - APP_SECRET=your-production-secret-key-min-32-chars
      - DATABASE_URL=jdbc:postgresql://db:5432/urlshortener
      - DATABASE_USER=urluser
      - DATABASE_PASSWORD=urlpass
      - JWT_SECRET=your-jwt-secret-min-32-chars
      - BASE_URL=https://short.example.com
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped

  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_USER=urluser
      - POSTGRES_PASSWORD=urlpass
      - POSTGRES_DB=urlshortener
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U urluser -d urlshortener"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

volumes:
  postgres_data:
```

```bash
# Build và start
docker compose up -d

# Xem logs
docker compose logs -f app

# Stop
docker compose down

# Stop và xóa data
docker compose down -v
```

---

## 5. Production Configuration

```hocon
# conf/prod.conf
include "application.conf"

# Tắt autoApply evolutions trong production!
play.evolutions.db.default.autoApply = false

# Chỉ cho phép production domain
play.filters.hosts.allowed = ["short.example.com"]

# Tắt dev mode error pages
play.http.errorHandler = "ErrorHandler"

# Log thấp hơn trong production
```

```bash
# Chạy với production config
./bin/url-shortener \
  -Dconfig.file=/conf/prod.conf \
  -Dplay.http.secret.key="$APP_SECRET" \
  -Dplay.evolutions.db.default.applyEvolutions=false
```

---

## 6. Health Check

```java
// app/controllers/HealthController.java
public Result health() {
    // Check DB connectivity
    try {
        db.withConnection(conn -> conn.isValid(1));
        return ok(Json.newObject()
            .put("status", "UP")
            .put("db", "UP")
        );
    } catch (Exception e) {
        return internalServerError(Json.newObject()
            .put("status", "DOWN")
            .put("db", "DOWN")
            .put("error", e.getMessage())
        );
    }
}
```

---

## 7. Bài Tập

1. Build Docker image của URL Shortener (Day 42 project)
2. Viết docker-compose.yml với app + postgres
3. Test: `docker compose up` → `curl http://localhost:9000/health`
4. Test evolutions chạy khi app start
5. Add NGINX reverse proxy vào docker-compose
