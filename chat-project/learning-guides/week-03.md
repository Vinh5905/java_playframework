# 📅 Tuần 3: MongoDB + Message Storage + REST API

---

## 1. 🎯 Mục Tiêu Cuối Tuần

**Thấy gì trên màn hình:**
- Gửi tin nhắn → lưu MongoDB → load lại vẫn thấy
- Lịch sử chat persist qua restart
- Phân trang tin nhắn (load 20 tin mới nhất)

**Demo flow:**
```
1. Switch sang Alice → chat với Florencio
2. Gửi "Hello! Test Tuần 3"
3. Ctrl+C → sbt run lại
4. Mở lại conversation → VẪN thấy "Hello! Test Tuần 3"
```

---

## 2. 📚 Kiến Thức Lý Thuyết

### 2.1 Tại Sao MongoDB Cho Messages?

**PostgreSQL** (relational): Phù hợp khi:
- Schema cố định, quan hệ phức tạp
- ACID transactions quan trọng
- Queries phức tạp với JOIN

**MongoDB** (document): Phù hợp khi:
- Document structure linh hoạt
- Append-mostly (ghi nhiều hơn đọc)
- Horizontal scaling dễ hơn

Messages trong chat:
```json
{
  "id": "msg_abc123",
  "conversationId": "conv_1",
  "senderId": 1,
  "text": "Hello!",
  "attachments": [...],   ← Linh hoạt, không cần ALTER TABLE
  "reactions": {"❤️": [2, 4]},
  "timestamp": "2024-01-15T10:30:00Z",
  "readBy": [1, 2]
}
```

### 2.2 MongoDB Reactive Streams Driver

MongoDB Java driver có 2 loại:
- **Sync** (`MongoClient`) → blocking → cần custom EC
- **Reactive Streams** (`MongoClient` với ReactiveStreams) → non-blocking → dùng trực tiếp với Pekko Streams

Trong dự án này dùng **Reactive Streams** cho messages:

```java
// Không block thread!
Publisher<Message> publisher = collection.find(filter).sort(...).limit(20);
// Convert Publisher → CompletionStage bằng Pekko adapter
```

### 2.3 REST API Design Cho Messages

```
GET  /api/conversations                    → List conversations của current user
GET  /api/conversations/:id/messages       → Messages (phân trang)
     ?before=<timestamp>&limit=20          ← Cursor-based pagination
POST /api/conversations/:id/messages       → Gửi tin mới
GET  /api/conversations/:convId/messages   → Load conversation (tạo nếu chưa có)
```

**Tại sao cursor-based thay vì offset?** Offset pagination (`?page=2&size=20`) không ổn định nếu có tin mới insert - page 2 có thể repeat hoặc skip messages. Cursor dùng timestamp/ID không bị vấn đề này.

---

## 3. 🛠️ Setup Môi Trường

```bash
# Chạy MongoDB container
docker run -d \
  --name chat-mongo \
  -e MONGO_INITDB_ROOT_USERNAME=chatuser \
  -e MONGO_INITDB_ROOT_PASSWORD=chatpass \
  -e MONGO_INITDB_DATABASE=chatapp \
  -p 27017:27017 \
  mongo:7

# Verify
docker exec -it chat-mongo mongosh -u chatuser -p chatpass chatapp
# Gõ db.runCommand({ping:1}) → { ok: 1 }
```

---

## 4. 📂 Cấu Trúc File Tuần 3

```
your-project/be/
├── app/
│   ├── controllers/
│   │   ├── AccountController.java        ← Không đổi
│   │   ├── ConversationController.java   ← TẠO MỚI
│   │   └── MessageController.java        ← TẠO MỚI
│   ├── models/
│   │   ├── Account.java                  ← Không đổi
│   │   ├── Conversation.java             ← TẠO MỚI
│   │   └── Message.java                  ← TẠO MỚI
│   └── repositories/
│       ├── AccountRepository.java        ← Không đổi
│       ├── ConversationRepository.java   ← TẠO MỚI (PostgreSQL)
│       └── MessageRepository.java        ← TẠO MỚI (MongoDB)
├── conf/
│   ├── application.conf                  ← SỬA: thêm MongoDB config
│   ├── routes                            ← SỬA: thêm message routes
│   └── evolutions/default/
│       ├── 1.sql                         ← Không đổi
│       └── 2.sql                         ← TẠO MỚI: conversations table
└── build.sbt                             ← SỬA: thêm MongoDB dependency
```

---

## 5. 👨‍💻 Hướng Dẫn Code Từng Bước

### Bước 5.1: Thêm MongoDB dependency

```scala
// build.sbt
libraryDependencies ++= Seq(
  // ... existing dependencies ...

  // MongoDB Reactive Streams driver
  "org.mongodb" % "mongodb-driver-reactivestreams" % "5.1.0",
  // Pekko Streams để convert Publisher → CompletionStage
  "org.apache.pekko" %% "pekko-stream" % "1.1.2",
)
```

### Bước 5.2: Evolution cho Conversations table

```sql
-- conf/evolutions/default/2.sql

-- !Ups
CREATE TABLE conversations (
    id           BIGSERIAL PRIMARY KEY,
    participant1 BIGINT NOT NULL REFERENCES accounts(id),
    participant2 BIGINT NOT NULL REFERENCES accounts(id),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(LEAST(participant1, participant2), GREATEST(participant1, participant2))
);
-- UNIQUE constraint: không tạo duplicate conversation cho cùng 2 người
-- LEAST/GREATEST: đảm bảo (1,2) và (2,1) đều unique về (1,2)

-- !Downs
DROP TABLE IF EXISTS conversations;
```

### Bước 5.3: Model Message

```java
// app/models/Message.java
package models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class Message {
    public String id;             // MongoDB ObjectId dạng string

    @JsonProperty("conversation_id")
    public String conversationId;

    @JsonProperty("sender_id")
    public Long senderId;

    public String text;
    public Instant timestamp;

    public Message() {}

    public Message(String convId, Long senderId, String text) {
        this.conversationId = convId;
        this.senderId = senderId;
        this.text = text;
        this.timestamp = Instant.now();
    }
}
```

### Bước 5.4: MessageRepository (MongoDB)

```java
// app/repositories/MessageRepository.java
package repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import models.Message;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Source;
import play.libs.streams.Accumulator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class MessageRepository {

    private final MongoCollection<Document> messages;
    private final Materializer mat;

    @Inject
    public MessageRepository(com.typesafe.config.Config config, Materializer mat) {
        this.mat = mat;

        // Tạo MongoDB connection từ config
        String uri = config.getString("mongodb.uri");
        MongoClient client = MongoClients.create(uri);
        this.messages = client.getDatabase("chatapp").getCollection("messages");
    }

    /** Lấy N tin nhắn mới nhất của 1 conversation */
    public CompletionStage<List<Message>> getMessages(String convId, int limit) {
        var publisher = messages
            .find(Filters.eq("conversationId", convId))
            .sort(Sorts.descending("timestamp"))
            .limit(limit);

        // Convert Reactive Streams Publisher → Pekko Source → List
        return Source.fromPublisher(publisher)
            .map(this::docToMessage)
            .runWith(org.apache.pekko.stream.javadsl.Sink.seq(), mat)
            .toCompletableFuture();
    }

    /** Lưu tin nhắn mới */
    public CompletionStage<Message> saveMessage(Message msg) {
        Document doc = new Document()
            .append("conversationId", msg.conversationId)
            .append("senderId", msg.senderId)
            .append("text", msg.text)
            .append("timestamp", msg.timestamp);

        var future = new CompletableFuture<Message>();

        messages.insertOne(doc).subscribe(new org.reactivestreams.Subscriber<>() {
            @Override public void onSubscribe(org.reactivestreams.Subscription s) { s.request(1); }
            @Override public void onNext(com.mongodb.client.result.InsertOneResult r) {
                msg.id = r.getInsertedId().asObjectId().getValue().toHexString();
                future.complete(msg);
            }
            @Override public void onError(Throwable t) { future.completeExceptionally(t); }
            @Override public void onComplete() {}
        });

        return future;
    }

    private Message docToMessage(Document doc) {
        Message msg = new Message();
        msg.id = doc.getObjectId("_id").toHexString();
        msg.conversationId = doc.getString("conversationId");
        msg.senderId = doc.getLong("senderId");
        msg.text = doc.getString("text");
        return msg;
    }
}
```

### Bước 5.5: MessageController

```java
// app/controllers/MessageController.java
package controllers;

import models.Message;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import repositories.MessageRepository;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

public class MessageController extends Controller {

    private final MessageRepository messageRepo;

    @Inject
    public MessageController(MessageRepository messageRepo) {
        this.messageRepo = messageRepo;
    }

    // GET /api/conversations/:convId/messages?limit=20
    public CompletionStage<Result> getMessages(String convId, int limit) {
        return messageRepo.getMessages(convId, Math.min(limit, 100))
            .thenApply(msgs -> ok(Json.toJson(msgs)))
            .exceptionally(t -> internalServerError(
                Json.newObject().put("error", t.getMessage())
            ));
    }

    // POST /api/conversations/:convId/messages
    public CompletionStage<Result> sendMessage(String convId, Http.Request request) {
        var body = request.body().asJson();
        if (body == null || !body.has("text")) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                badRequest(Json.newObject().put("error", "text field required"))
            );
        }

        String text = body.get("text").asText().trim();
        if (text.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                badRequest(Json.newObject().put("error", "text cannot be empty"))
            );
        }

        // TODO: Lấy senderId từ current account (đơn giản: lấy từ header hoặc query param)
        Long senderId = Long.parseLong(request.getQueryString("senderId").orElse("1"));

        Message msg = new Message(convId, senderId, text);
        return messageRepo.saveMessage(msg)
            .thenApply(saved -> created(Json.toJson(saved)))  // 201 Created
            .exceptionally(t -> internalServerError(
                Json.newObject().put("error", t.getMessage())
            ));
    }
}
```

### Bước 5.6: Thêm MongoDB config

```hocon
# application.conf
mongodb {
  uri = "mongodb://chatuser:chatpass@localhost:27017/chatapp?authSource=admin"
  uri = ${?MONGODB_URI}  # Override bằng env var trong production
}
```

### Bước 5.7: Thêm Routes

```
# conf/routes - thêm message routes
GET     /api/conversations/:convId/messages   controllers.MessageController.getMessages(convId: String, limit: Int ?= 20)
POST    /api/conversations/:convId/messages   controllers.MessageController.sendMessage(convId: String, request: Request)
```

### Bước 5.8: Cập nhật Frontend config.js

```javascript
// config.js
USE_MOCK: false,
```

Frontend `api.js` đã có sẵn `getMessages()` và `sendMessage()` → hoạt động với backend mới.

---

## 6. 🔄 Sự Tiến Hóa

| | Tuần 1-2 | Tuần 3 |
|--|---------|--------|
| Messages | In-memory (mock) | MongoDB |
| Conversations | Hard-coded mock | PostgreSQL |
| Send message | Local state | HTTP POST → MongoDB |
| Lịch sử | Mất khi reload | Persist |

---

## 7. 🎭 Mock Code Còn Lại

```javascript
// Frontend websocket.js vẫn là no-op (Tuần 4 mới dùng)
// Typing indicator vẫn là client-side mock (Tuần 5)
// Bot reply vẫn là simulateBotReply() ở frontend (Tuần 7)
```

---

## 8. ⚠️ Pitfalls Tuần 3

**MongoDB ObjectId** không phải `Long` như PostgreSQL ID → frontend phải chấp nhận `string` ID cho messages.

**Pagination direction**: Lấy tin mới nhất → sort `descending` → nhưng hiển thị lại phải `reverse` ở frontend (show cũ → mới từ trên xuống).

**Charset**: Đảm bảo MongoDB collection và Java String đều UTF-8 để support emoji.

---

## 9. ✅ Checklist Tuần 3

- [ ] MongoDB container chạy
- [ ] `curl -X POST .../conversations/1/messages -d '{"text":"Hello"}'` → 201
- [ ] `curl .../conversations/1/messages` → thấy tin vừa gửi
- [ ] Restart backend → messages vẫn còn
- [ ] Frontend: gửi tin → hiện ngay, reload → vẫn thấy

---

## 10. 🔗 Kết Nối Tuần 4

Tuần 4 thêm WebSocket để tin nhắn hiện **real-time** không cần reload. Sẽ dùng Pekko Actors để manage connections.
