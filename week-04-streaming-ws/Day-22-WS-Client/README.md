# Day 22 - WS Client: Gọi API Ngoài Bất Đồng Bộ

## Mục tiêu
- Dùng Play WS Client để gọi HTTP API
- Xử lý response, timeout, retry
- Parallel HTTP calls

---

## 1. Setup

```scala
// build.sbt
libraryDependencies += ws  // Thêm WS Client
```

---

## 2. Basic GET Request

```java
import play.libs.ws.*;
import javax.inject.Inject;
import java.time.Duration;

@Singleton
public class ExternalApiService {

    private final WSClient ws;

    @Inject
    public ExternalApiService(WSClient ws) {
        this.ws = ws;
    }

    // GET đơn giản
    public CompletionStage<JsonNode> fetchUser(Long id) {
        return ws.url("https://jsonplaceholder.typicode.com/users/" + id)
            .setRequestTimeout(Duration.ofSeconds(5))
            .get()
            .thenApply(response -> {
                if (response.getStatus() != 200) {
                    throw new RuntimeException("API returned: " + response.getStatus());
                }
                return response.asJson();
            });
    }

    // POST với JSON body
    public CompletionStage<JsonNode> createUser(ObjectNode userData) {
        return ws.url("https://jsonplaceholder.typicode.com/users")
            .setContentType("application/json")
            .setRequestTimeout(Duration.ofSeconds(10))
            .post(userData.toString())
            .thenApply(WSResponse::asJson);
    }

    // Với headers và auth
    public CompletionStage<JsonNode> fetchPrivateData(String apiKey) {
        return ws.url("https://api.example.com/data")
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("X-API-Version", "2")
            .addQueryParameter("format", "json")
            .get()
            .thenApply(WSResponse::asJson);
    }
}
```

---

## 3. Parallel Calls

```java
public CompletionStage<Result> aggregateData() {
    // 3 calls chạy song song
    CompletionStage<JsonNode> usersF =
        ws.url("https://jsonplaceholder.typicode.com/users").get()
            .thenApply(WSResponse::asJson);

    CompletionStage<JsonNode> postsF =
        ws.url("https://jsonplaceholder.typicode.com/posts").get()
            .thenApply(WSResponse::asJson);

    CompletionStage<JsonNode> todosF =
        ws.url("https://jsonplaceholder.typicode.com/todos").get()
            .thenApply(WSResponse::asJson);

    return usersF
        .thenCombine(postsF, (users, posts) -> new Object[]{users, posts})
        .thenCombine(todosF, (arr, todos) -> {
            ObjectNode result = Json.newObject();
            result.set("users", (JsonNode) arr[0]);
            result.set("posts", (JsonNode) arr[1]);
            result.set("todos", todos);
            return ok(result);
        })
        .exceptionally(t -> internalServerError("External API failed: " + t.getMessage()));
}
```

---

## 4. Retry Logic

```java
public CompletionStage<WSResponse> fetchWithRetry(String url, int maxAttempts) {
    return fetchWithRetryHelper(url, maxAttempts, 1);
}

private CompletionStage<WSResponse> fetchWithRetryHelper(String url, int max, int attempt) {
    return ws.url(url)
        .setRequestTimeout(Duration.ofSeconds(5))
        .get()
        .thenCompose(response -> {
            if (response.getStatus() == 200) {
                return CompletableFuture.completedFuture(response);
            }
            if (attempt >= max) {
                return CompletableFuture.failedFuture(
                    new RuntimeException("Failed after " + max + " attempts")
                );
            }
            // Exponential backoff
            long delayMs = (long) Math.pow(2, attempt) * 100;
            return delayedFuture(delayMs)
                .thenCompose(v -> fetchWithRetryHelper(url, max, attempt + 1));
        });
}

private CompletionStage<Void> delayedFuture(long ms) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    scheduler.schedule(() -> future.complete(null), ms, TimeUnit.MILLISECONDS);
    return future;
}
```

---

## 5. WS Client Configuration

```hocon
# application.conf
play.ws {
  # Timeout mặc định
  timeout.connection = 5000ms
  timeout.idle = 5000ms
  timeout.request = 10000ms

  # SSL/TLS
  ssl {
    # Trust tất cả certificates (CHỈ dev! KHÔNG dùng production)
    loose.acceptAnyCertificate = false
  }
}
```

---

## 6. HTTP Authentication

```java
// Basic Auth
ws.url("https://api.example.com/protected")
    .setAuth("username", "password", WSAuthScheme.BASIC)
    .get();

// Các scheme khác: DIGEST, KERBEROS, NTLM, SPNEGO
ws.url("https://enterprise.example.com/api")
    .setAuth("user", "pass", WSAuthScheme.DIGEST)
    .get();
```

---

## 7. Multipart Form Data (File Upload Đến API Ngoài)

```java
import play.libs.ws.WSRequest;
import akka.stream.javadsl.Source;

public CompletionStage<JsonNode> uploadFile(File file) {
    return ws.url("https://upload.example.com/files")
        .post(Source.from(Arrays.asList(
            Http.MultipartFormData.DataPart.create("description", "My file"),
            Http.MultipartFormData.FilePart.create(
                "file",                    // field name
                file.getName(),            // filename
                "application/octet-stream",
                FileIO.fromPath(file.toPath())
            )
        )))
        .thenApply(WSResponse::asJson);
}
```

---

## 8. Streaming Large Response (Không Load Vào RAM)

`get()` load toàn bộ body vào memory. Với file lớn, dùng `stream()`:

```java
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import java.nio.file.Path;

public CompletionStage<Long> downloadLargeFile(String url, Path destination) {
    return ws.url(url)
        .stream()  // Trả Source thay vì load vào memory
        .thenCompose(response -> {
            // Pipe stream đến file - không tốn RAM
            return response.getBodyAsSource()
                .runWith(FileIO.toPath(destination), materializer)
                .thenApply(ioResult -> ioResult.count());
        });
}
```

**Khi nào dùng `stream()`:**
- Download file > 10MB
- Response không biết trước kích thước
- Streaming video/audio
- Server-Sent Events từ external service

---

## 9. Bài Tập

Xem `ws-client-demo/` trong thư mục này.

```bash
cd ws-client-demo
sbt run

# Test gọi public API
curl http://localhost:9000/ws/user/1
curl http://localhost:9000/ws/aggregate
curl http://localhost:9000/ws/posts
```
