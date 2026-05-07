# Day 33 - Unit Testing Trong Play

## Mục tiêu
- Test controller mà không cần HTTP server
- Mock dependencies
- Test async actions

---

## 1. Setup Dependencies

```scala
// build.sbt
libraryDependencies ++= Seq(
  "org.junit.jupiter" % "junit-jupiter-api" % "5.10.2" % Test,
  "org.junit.jupiter" % "junit-jupiter-engine" % "5.10.2" % Test,
  "org.mockito" % "mockito-core" % "5.11.0" % Test,
  "org.mockito" % "mockito-junit-jupiter" % "5.11.0" % Test
)
```

---

## 2. Test Controller Đơn Giản

```java
// test/controllers/TodoControllerTest.java
import controllers.TodoController;
import models.Todo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import repositories.AsyncTodoRepository;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TodoControllerTest {

    @Test
    void testList_returnsAllTodos() throws Exception {
        // Arrange: Mock repository
        AsyncTodoRepository mockRepo = mock(AsyncTodoRepository.class);
        when(mockRepo.findAll()).thenReturn(
            CompletableFuture.completedFuture(
                Arrays.asList(
                    new Todo(1L, "First", false),
                    new Todo(2L, "Second", true)
                )
            )
        );

        TodoController controller = new TodoController(mockRepo);

        // Act: Gọi action trực tiếp (không cần HTTP server!)
        Result result = controller.list()
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

        // Assert
        assertEquals(200, result.status());
        String body = Helpers.contentAsString(result);
        assertTrue(body.contains("First"));
        assertTrue(body.contains("Second"));
    }

    @Test
    void testCreate_withValidBody_returns201() throws Exception {
        AsyncTodoRepository mockRepo = mock(AsyncTodoRepository.class);
        when(mockRepo.save(anyString())).thenReturn(
            CompletableFuture.completedFuture(new Todo(1L, "New Todo", false))
        );

        TodoController controller = new TodoController(mockRepo);

        // Tạo fake request với JSON body
        Http.Request fakeRequest = Helpers.fakeRequest("POST", "/todos")
            .bodyJson(Json.parse("{\"title\": \"New Todo\"}"))
            .build();

        Result result = controller.create(fakeRequest)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

        assertEquals(201, result.status());
        verify(mockRepo, times(1)).save("New Todo");
    }

    @Test
    void testCreate_withMissingTitle_returns400() throws Exception {
        AsyncTodoRepository mockRepo = mock(AsyncTodoRepository.class);
        TodoController controller = new TodoController(mockRepo);

        Http.Request fakeRequest = Helpers.fakeRequest("POST", "/todos")
            .bodyJson(Json.parse("{}"))
            .build();

        Result result = controller.create(fakeRequest)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

        assertEquals(400, result.status());
        verify(mockRepo, never()).save(any());  // repository không được gọi
    }

    @Test
    void testGet_notFound_returns404() throws Exception {
        AsyncTodoRepository mockRepo = mock(AsyncTodoRepository.class);
        when(mockRepo.findById(999L)).thenReturn(
            CompletableFuture.completedFuture(java.util.Optional.empty())
        );

        TodoController controller = new TodoController(mockRepo);

        Result result = controller.get(999L)
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

        assertEquals(404, result.status());
    }
}
```

---

## 3. Integration Test Với Play Application

```java
// test/integration/TodoApiIntegrationTest.java
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.Helpers;
import play.mvc.*;

import org.junit.jupiter.api.*;
import static play.test.Helpers.*;

class TodoApiIntegrationTest {

    private static Application app;

    @BeforeAll
    static void setup() {
        // Khởi tạo full Play app với test config
        app = new GuiceApplicationBuilder()
            .configure("db.default.url", "jdbc:h2:mem:test;MODE=PostgreSQL")
            .configure("play.evolutions.db.default.enabled", true)
            .configure("play.evolutions.db.default.autoApply", true)
            .build();
        Helpers.start(app);
    }

    @AfterAll
    static void teardown() {
        Helpers.stop(app);
    }

    @Test
    void testCreateAndGetTodo() {
        // POST create
        Http.RequestBuilder createReq = fakeRequest(POST, "/todos")
            .bodyJson(play.libs.Json.parse("{\"title\": \"Integration Test\"}"));
        Result createResult = route(app, createReq);
        assertEquals(201, createResult.status());

        // GET list
        Result listResult = route(app, fakeRequest(GET, "/todos"));
        assertEquals(200, listResult.status());
        String body = contentAsString(listResult);
        assertTrue(body.contains("Integration Test"));
    }
}
```

---

## 4. Test Bất Đồng Bộ

```java
@Test
void testAsyncWithTimeout() throws Exception {
    // CompletionStage timeout
    Result result = controller.slowEndpoint()
        .toCompletableFuture()
        .get(10, TimeUnit.SECONDS);  // Timeout sau 10s

    assertEquals(200, result.status());
}

// Dùng awaitility cho readable assertions
// libraryDependencies += "org.awaitility" % "awaitility" % "4.2.0" % Test
import static org.awaitility.Awaitility.*;
import static java.util.concurrent.TimeUnit.*;

@Test
void testWithAwaitility() {
    CompletionStage<Result> stage = controller.asyncEndpoint();

    await()
        .atMost(5, SECONDS)
        .until(() -> stage.toCompletableFuture().isDone());

    Result result = stage.toCompletableFuture().join();
    assertEquals(200, result.status());
}
```

---

## 5. Test Routes

```java
@Test
void testRouting() {
    // Test route matching
    Result result = route(app, fakeRequest("GET", "/todos/1"));
    assertNotEquals(404, result.status());  // Route exists

    Result notFound = route(app, fakeRequest("GET", "/nonexistent"));
    assertEquals(404, notFound.status());
}
```

---

## 6. Bài Tập

1. Viết unit tests cho TodoController từ Day 14
2. Test tất cả happy paths: list, get, create, update, delete
3. Test error paths: not found, invalid body, empty title
4. Mock repository và verify interactions
5. Integration test với H2 in-memory database

```bash
sbt test
# Hoặc test một class cụ thể
sbt "testOnly controllers.TodoControllerTest"
```
