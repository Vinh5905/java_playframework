# Day 34 - Integration Testing

## Mục tiêu
- Test với real HTTP server
- Test với database (H2 in-memory)
- Test filters và authentication

---

## 1. WithServer Test

```java
import play.test.*;
import static play.test.Helpers.*;

class ApiIntegrationTest extends WithServer {

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
            .configure("db.default.driver", "org.h2.Driver")
            .configure("db.default.url", "jdbc:h2:mem:test;MODE=PostgreSQL")
            .configure("play.evolutions.db.default.autoApply", true)
            .build();
    }

    @Test
    public void testCreateTodo() {
        // Server already started at testServer.port()
        WSClient ws = play.test.WSTestClient.newClient(testServer.port());

        WSResponse response = ws.url("/todos")
            .setContentType("application/json")
            .post("{\"title\": \"Integration test todo\"}")
            .toCompletableFuture()
            .join();

        assertEquals(201, response.getStatus());
        JsonNode body = response.asJson();
        assertEquals("Integration test todo", body.get("title").asText());

        ws.close();
    }

    @Test
    public void testListTodos() {
        WSClient ws = play.test.WSTestClient.newClient(testServer.port());

        WSResponse response = ws.url("/todos").get()
            .toCompletableFuture().join();

        assertEquals(200, response.getStatus());
        assertTrue(response.asJson().isArray());

        ws.close();
    }
}
```

---

## 2. Test Authentication

```java
@Test
public void testProtectedEndpointWithoutToken() {
    WSResponse response = ws.url("/api/urls").get()
        .toCompletableFuture().join();

    assertEquals(401, response.getStatus());
}

@Test
public void testProtectedEndpointWithValidToken() {
    // Đăng ký và login để lấy token
    WSResponse loginResponse = ws.url("/auth/login")
        .setContentType("application/json")
        .post("{\"email\":\"test@test.com\",\"password\":\"password\"}")
        .toCompletableFuture().join();

    String token = loginResponse.asJson().get("token").asText();

    // Dùng token để gọi protected endpoint
    WSResponse response = ws.url("/api/urls")
        .addHeader("Authorization", "Bearer " + token)
        .get()
        .toCompletableFuture().join();

    assertEquals(200, response.getStatus());
}
```

---

## 3. Test Database State

```java
@Test
public void testCreateAndRetrieve() throws Exception {
    WSClient ws = play.test.WSTestClient.newClient(testServer.port());

    // Tạo 3 todos
    for (int i = 1; i <= 3; i++) {
        ws.url("/todos")
            .setContentType("application/json")
            .post("{\"title\": \"Todo " + i + "\"}")
            .toCompletableFuture().join();
    }

    // Verify trong database
    WSResponse listResponse = ws.url("/todos").get()
        .toCompletableFuture().join();

    JsonNode todos = listResponse.asJson();
    assertTrue(todos.size() >= 3);

    // Verify stats
    WSResponse statsResponse = ws.url("/todos/stats").get()
        .toCompletableFuture().join();
    assertTrue(statsResponse.asJson().get("total").asInt() >= 3);

    ws.close();
}
```

---

## 4. H2 Cho Testing

H2 là in-memory database, hỗ trợ PostgreSQL compatibility mode.

```scala
// build.sbt
libraryDependencies += "com.h2database" % "h2" % "2.2.224" % Test
```

```hocon
# conf/test.conf
include "application.conf"

db.default {
  driver = "org.h2.Driver"
  url = "jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE"
}

play.evolutions.db.default.autoApply = true
```

**Hạn chế**: H2 không support 100% PostgreSQL syntax (RETURNING clause, etc.). Prefer TestContainers cho test gần production hơn.

---

## 5. TestContainers (Khuyến Nghị)

```scala
libraryDependencies += "org.testcontainers" % "postgresql" % "1.19.8" % Test
```

```java
@Testcontainers
class PostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
            .configure("db.default.url", postgres.getJdbcUrl())
            .configure("db.default.username", postgres.getUsername())
            .configure("db.default.password", postgres.getPassword())
            .build();
    }
}
```

---

## 6. Test Script (curl-based)

```bash
#!/bin/bash
# integration-test.sh
BASE_URL="http://localhost:9000"

echo "=== Test Create ==="
RESPONSE=$(curl -s -X POST $BASE_URL/todos \
  -H "Content-Type: application/json" \
  -d '{"title": "Test todo"}')
TODO_ID=$(echo $RESPONSE | jq -r '.id')
echo "Created: $TODO_ID"
[ ! -z "$TODO_ID" ] && echo "PASS" || echo "FAIL"

echo "=== Test Get ==="
STATUS=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/todos/$TODO_ID)
[ "$STATUS" = "200" ] && echo "PASS" || echo "FAIL: Got $STATUS"

echo "=== Test Delete ==="
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE $BASE_URL/todos/$TODO_ID)
[ "$STATUS" = "204" ] && echo "PASS" || echo "FAIL: Got $STATUS"

echo "=== Done ==="
```
