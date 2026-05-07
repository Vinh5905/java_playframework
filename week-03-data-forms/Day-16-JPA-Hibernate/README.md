# Day 16 - JPA/Hibernate Trong Play

## Mục tiêu
- Setup JPA với Play
- Tại sao JPA trong async context cần xử lý đặc biệt
- Thực hành entity, repository với JPAApi

---

## 1. Setup

```scala
// build.sbt
libraryDependencies ++= Seq(
  javaJpa,
  "org.hibernate" % "hibernate-core" % "6.4.4.Final",
  "org.postgresql" % "postgresql" % "42.7.4"
)
```

```xml
<!-- conf/META-INF/persistence.xml -->
<persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0">
  <persistence-unit name="default" transaction-type="RESOURCE_LOCAL">
    <provider>org.hibernate.jpa.HibernatePersistenceProvider</provider>
    <properties>
      <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQLDialect"/>
    </properties>
  </persistence-unit>
</persistence>
```

---

## 2. Entity

```java
// app/models/TodoEntity.java
package models;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "todos")
public class TodoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private boolean done = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Getters & Setters (hoặc dùng Lombok @Data)
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
    public Instant getCreatedAt() { return createdAt; }
}
```

---

## 3. Repository Với JPAApi

```java
// app/repositories/JpaTodoRepository.java
package repositories;

import models.TodoEntity;
import play.db.jpa.JPAApi;
import org.apache.pekko.actor.ActorSystem;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;

@Singleton
public class JpaTodoRepository {

    private final JPAApi jpaApi;
    private final ExecutionContext jpaEc;

    @Inject
    public JpaTodoRepository(JPAApi jpaApi, ActorSystem system) {
        this.jpaApi = jpaApi;
        this.jpaEc = system.dispatchers().lookup("blocking-db-dispatcher");
    }

    public CompletionStage<List<TodoEntity>> findAll() {
        return CompletableFuture.supplyAsync(
            () -> jpaApi.withTransaction(em ->
                em.createQuery("SELECT t FROM TodoEntity t ORDER BY t.id", TodoEntity.class)
                  .getResultList()
            ),
            (ExecutionContextExecutor) jpaEc
        );
    }

    public CompletionStage<Optional<TodoEntity>> findById(Long id) {
        return CompletableFuture.supplyAsync(
            () -> jpaApi.withTransaction(em ->
                Optional.ofNullable(em.find(TodoEntity.class, id))
            ),
            (ExecutionContextExecutor) jpaEc
        );
    }

    public CompletionStage<TodoEntity> save(String title) {
        return CompletableFuture.supplyAsync(
            () -> jpaApi.withTransaction(em -> {
                TodoEntity todo = new TodoEntity();
                todo.setTitle(title);
                em.persist(todo);
                return todo;
            }),
            (ExecutionContextExecutor) jpaEc
        );
    }
}
```

---

## 4. Vấn Đề JPA + Async

JPA `EntityManager` **không thread-safe**. Không dùng 1 EntityManager cho nhiều thread.

`JPAApi.withTransaction(em -> ...)` tạo EntityManager mới cho mỗi lần gọi → an toàn.

**Khuyến nghị cho project mới**: Dùng plain JDBC (như Day 15, 17) hoặc jOOQ thay vì JPA. JPA trong async context dễ có leak và khó debug.

---

## 5. Bài Tập

1. Tạo entity `User` với quan hệ OneToMany với `Todo`
2. Query: lấy tất cả todos của 1 user
3. Transaction: tạo user và todo cùng 1 transaction
4. So sánh code JPA vs plain JDBC từ Day 15
