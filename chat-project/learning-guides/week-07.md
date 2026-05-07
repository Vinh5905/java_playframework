# 📅 Tuần 7: ChatGPT Bot với Streaming Response

---

## 1. 🎯 Mục Tiêu Cuối Tuần

**Thấy gì trên màn hình:**
- Chat với account "ChatGPT Bot"
- Gửi câu hỏi → Bot hiển thị "đang gõ..."
- Từng ký tự xuất hiện dần như người đang thật sự gõ
- Xong toàn bộ → tin nhắn hoàn chỉnh lưu vào MongoDB

**Demo flow:**
```
1. Switch sang Alice → Click conversation với ChatGPT Bot
2. Gửi: "Giải thích async trong Play Framework"
3. Thấy "ChatGPT Bot is typing..." với 3 chấm nhảy
4. Từng chữ xuất hiện: "A", "As", "Asy", "Asyn", "Async"...
5. Sau ~5-10 giây → full câu trả lời xuất hiện
```

---

## 2. 📚 Kiến Thức Lý Thuyết

### 2.1 OpenAI Streaming: SSE (Server-Sent Events)

OpenAI API hỗ trợ 2 mode:

**Non-streaming (thường dùng):**
```
Client → POST /v1/chat/completions
         Chờ... 5-10 giây...
Server → {"choices": [{"message": {"content": "Full answer here"}}]}
→ Cả câu trả lời dump ra 1 lần → UX tệ
```

**Streaming (chúng ta dùng):**
```
Client → POST /v1/chat/completions {"stream": true}
Server → data: {"choices":[{"delta":{"content":"A"}}]}
Server → data: {"choices":[{"delta":{"content":"s"}}]}
Server → data: {"choices":[{"delta":{"content":"y"}}]}
...
Server → data: [DONE]
→ Từng chunk nhỏ → Frontend nhận và append → UX "đang gõ" thật
```

OpenAI dùng **SSE (Server-Sent Events)** format để stream:
```
Content-Type: text/event-stream

data: {"id":"...","object":"chat.completion.chunk","choices":[{"delta":{"content":"A"},"finish_reason":null}]}

data: {"id":"...","choices":[{"delta":{"content":"s"},"finish_reason":null}]}

data: [DONE]
```

### 2.2 Luồng Dữ Liệu End-to-End

```
User gửi "Hello bot"
    ↓
ChatRoomActor nhận SendMessage
    ↓ (detect botId = 9)
BotService.streamResponse("Hello bot")
    ↓
Play WS Client → POST api.openai.com {"stream":true}
    ↓ (nhận SSE stream)
Mỗi chunk → ChatRoomActor.BotChunk event
    ↓
UserConnectionActor → WebSocket → Browser
    ↓
Frontend append chunk vào bubble đang hiển thị
    ↓ (nhận [DONE])
Lưu full response vào MongoDB
```

### 2.3 Play WS Client Streaming

Play WS Client hỗ trợ consume streaming response:

```java
// Consume SSE stream từ OpenAI
wsClient.url("https://api.openai.com/v1/chat/completions")
    .addHeader("Authorization", "Bearer " + apiKey)
    .post(requestBody)
    .thenAccept(response -> {
        // response.getBodyAsSource() → Pekko Source<ByteString, ?>
        // Process từng chunk khi nhận được
    });
```

### 2.4 Detect Bot Account

Khi user gửi tin vào conversation với Bot (isBot = true), backend route đặc biệt:

```java
// ChatRoomActor.handleSendMessage():
if (isConversationWithBot(cmd.convId)) {
    // Gọi BotService thay vì broadcast thông thường
    botService.streamResponse(cmd.text, cmd.convId, cmd.senderId);
} else {
    // Direct message thường
    broadcastToParticipants(cmd);
}
```

---

## 3. 🛠️ Setup

### Lấy OpenAI API Key

1. Vào https://platform.openai.com/api-keys
2. "Create new secret key"
3. Copy key (dạng `sk-proj-...`)

```bash
# Lưu vào environment (KHÔNG hardcode trong code)
export OPENAI_API_KEY="sk-proj-your-key-here"
# Thêm vào ~/.zshrc để persist qua session
```

---

## 4. 📂 Cấu Trúc File Tuần 7

```
your-project/be/
├── app/
│   ├── actors/
│   │   └── ChatRoomActor.java     ← SỬA: detect bot và route đến BotService
│   └── services/
│       └── BotService.java        ← TẠO MỚI: OpenAI integration
└── conf/
    └── application.conf           ← SỬA: thêm openai.apiKey
```

---

## 5. 👨‍💻 Hướng Dẫn Code Từng Bước

### Bước 5.1: Cấu hình OpenAI

```hocon
# application.conf
openai {
  apiKey     = "changeme"           # Sẽ bị override bởi env var
  apiKey     = ${?OPENAI_API_KEY}   # Đọc từ env var (KHÔNG commit API key!)
  model      = "gpt-4o-mini"        # Rẻ nhất, đủ dùng cho học
  maxTokens  = 500
  baseUrl    = "https://api.openai.com/v1"
}
```

### Bước 5.2: BotService - OpenAI Streaming

**File:** `app/services/BotService.java`

```java
package services;

import actors.ChatRoomActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.util.ByteString;
import play.libs.Json;
import play.libs.ws.WSClient;
import repositories.MessageRepository;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class BotService {

    private final WSClient ws;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final MessageRepository messageRepo;
    private final ActorRef chatRoom;
    private final Materializer mat;

    // ID của Bot account (phải match với DB)
    public static final Long BOT_ACCOUNT_ID = 9L;

    @Inject
    public BotService(WSClient ws,
                      Config config,
                      MessageRepository messageRepo,
                      @Named("chat-room") ActorRef chatRoom,
                      Materializer mat) {
        this.ws = ws;
        this.apiKey = config.getString("openai.apiKey");
        this.model = config.getString("openai.model");
        this.baseUrl = config.getString("openai.baseUrl");
        this.messageRepo = messageRepo;
        this.chatRoom = chatRoom;
        this.mat = mat;
    }

    /**
     * Gọi OpenAI API với streaming.
     *
     * Mỗi chunk nhận được → gửi WS event "bot_chunk" đến ChatRoomActor
     *   → ChatRoomActor → UserConnectionActor → Browser
     *
     * Khi nhận [DONE] → lưu full response vào MongoDB
     *
     * @param userMessage  Nội dung user gửi
     * @param convId       Conversation ID (để route về đúng chat window)
     * @param userId       ID của user đang chat (để broadcast về cho họ)
     */
    public void streamResponse(String userMessage, String convId, Long userId) {
        // Xây dựng request body theo OpenAI API format
        ObjectNode requestBody = Json.newObject();
        requestBody.put("model", model);
        requestBody.put("stream", true);  // ← Quan trọng: bật streaming!
        requestBody.put("max_tokens", 500);

        // System prompt: định nghĩa "tính cách" của bot
        var systemMsg = Json.newObject()
            .put("role", "system")
            .put("content", "You are a helpful assistant in a chat app. " +
                "Keep responses concise (under 100 words) and friendly. " +
                "Respond in the same language as the user.");

        // User message
        var userMsg = Json.newObject()
            .put("role", "user")
            .put("content", userMessage);

        requestBody.set("messages", Json.newArray().add(systemMsg).add(userMsg));

        // Notify client: bot đang gõ
        chatRoom.tell(
            new ChatRoomActor.BotChunk(convId, userId, "", false),
            ActorRef.noSender()
        );

        // StringBuilder để ghép toàn bộ response
        StringBuilder fullResponse = new StringBuilder();

        ws.url(baseUrl + "/chat/completions")
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody.toString())
            .thenAccept(response -> {
                if (response.getStatus() != 200) {
                    sendErrorChunk(convId, userId, "OpenAI API error: " + response.getStatus());
                    return;
                }

                // Stream body response line by line
                response.getBodyAsSource()
                    .via(org.apache.pekko.stream.javadsl.Framing.delimiter(
                        ByteString.fromString("\n"),
                        8192,
                        org.apache.pekko.stream.javadsl.FramingTruncation.ALLOW
                    ))
                    .runForeach(chunk -> {
                        String line = chunk.utf8String().trim();

                        // SSE format: "data: {json}" hoặc "data: [DONE]"
                        if (!line.startsWith("data: ")) return;

                        String data = line.substring(6).trim();

                        if ("[DONE]".equals(data)) {
                            // Stream kết thúc → lưu full message vào MongoDB
                            String fullText = fullResponse.toString();
                            if (!fullText.isEmpty()) {
                                var botMsg = new models.Message(convId, BOT_ACCOUNT_ID, fullText);
                                messageRepo.saveMessage(botMsg);
                            }
                            // Signal frontend: bot đã gõ xong
                            chatRoom.tell(
                                new ChatRoomActor.BotChunk(convId, userId, "", true),
                                ActorRef.noSender()
                            );
                            return;
                        }

                        // Parse chunk JSON
                        try {
                            JsonNode json = Json.parse(data);
                            JsonNode deltaContent = json
                                .path("choices").path(0)
                                .path("delta").path("content");

                            if (!deltaContent.isMissingNode() && !deltaContent.isNull()) {
                                String textChunk = deltaContent.asText();
                                fullResponse.append(textChunk);

                                // Gửi chunk đến browser qua WebSocket
                                chatRoom.tell(
                                    new ChatRoomActor.BotChunk(convId, userId, textChunk, false),
                                    ActorRef.noSender()
                                );
                            }
                        } catch (Exception e) {
                            // Ignore malformed chunks (thỉnh thoảng OpenAI gửi empty lines)
                        }
                    }, mat)
                    .exceptionally(t -> {
                        sendErrorChunk(convId, userId, "Streaming error: " + t.getMessage());
                        return null;
                    });
            })
            .exceptionally(t -> {
                sendErrorChunk(convId, userId, "Failed to call OpenAI: " + t.getMessage());
                return null;
            });
    }

    private void sendErrorChunk(String convId, Long userId, String errorMsg) {
        chatRoom.tell(
            new ChatRoomActor.BotChunk(convId, userId, "[Error: " + errorMsg + "]", true),
            ActorRef.noSender()
        );
    }
}
```

### Bước 5.3: Thêm BotChunk Message vào ChatRoomActor

```java
// app/actors/ChatRoomActor.java - thêm inner class và handler:

public static class BotChunk {
    public final String convId;
    public final Long targetUserId;  // Chỉ gửi về cho user đang chat với bot
    public final String chunk;
    public final boolean isDone;

    public BotChunk(String c, Long u, String ch, boolean d) {
        convId = c; targetUserId = u; chunk = ch; isDone = d;
    }
}

// Trong createReceive():
.match(BotChunk.class, bc -> {
    ActorRef targetActor = connectedUsers.get(bc.targetUserId);
    if (targetActor == null) return;

    ObjectNode payload = Json.newObject()
        .put("type", "bot_chunk")
        .put("convId", bc.convId)
        .put("chunk", bc.chunk)
        .put("isDone", bc.isDone);

    targetActor.tell(payload, self());
})
```

### Bước 5.4: Detect Bot Trong handleSendMessage

```java
// ChatRoomActor.java - trong handleSendMessage():
private void handleSendMessage(SendMessage cmd) {
    // Kiểm tra xem conversation có với Bot không
    // TODO: Query DB để biết participantId
    // Đơn giản: parse convId format "direct-{userId1}-{userId2}"
    boolean isWithBot = cmd.convId.contains("-" + BotService.BOT_ACCOUNT_ID + "-")
                     || cmd.convId.endsWith("-" + BotService.BOT_ACCOUNT_ID);

    if (isWithBot) {
        // Route đến BotService thay vì broadcast
        botService.streamResponse(cmd.text, cmd.convId, cmd.senderId);
    } else {
        // Direct message thường - broadcast như trước
        // ... existing code ...
    }
}
```

### Bước 5.5: Frontend - Nhận Bot Chunks

Frontend `websocket.js` đã có `appendBotChunk()` hook. Implement trong `app.js`:

```javascript
// app.js - implement appendBotChunk():
let currentBotBubble = null;

function appendBotChunk(convId, chunk, isDone) {
    if (convId !== STATE.activeConvId) return;

    const area = document.getElementById('messagesArea');

    if (!currentBotBubble) {
        // Tạo bubble mới cho bot
        const group = document.createElement('div');
        group.className = 'msg-group received';
        group.id = 'bot-streaming-group';
        group.innerHTML = `
            <div class="msg-row">
                ${renderAvatarHTML('ChatGPT Bot', 'avatar-msg')}
                <div id="bot-streaming-bubble" class="msg-bubble">
                    <span id="bot-text"></span><span class="streaming-cursor"></span>
                </div>
            </div>
        `;
        area.appendChild(group);
        currentBotBubble = document.getElementById('bot-text');
        area.scrollTop = area.scrollHeight;
    }

    if (isDone) {
        // Remove cursor, finalize
        const cursor = document.querySelector('.streaming-cursor');
        if (cursor) cursor.remove();
        currentBotBubble = null;

        // Tắt typing indicator
        setTyping(convId, 9, false);
    } else {
        // Append chunk
        currentBotBubble.textContent += chunk;
        area.scrollTop = area.scrollHeight;
    }
}
```

### Bước 5.6: Test Thủ Công (không cần OpenAI key)

Nếu chưa có API key, dùng mock trong BotService:

```java
// BotService.java - thêm mock mode:
public void streamResponse(String userMessage, String convId, Long userId) {
    // MOCK: Simulate streaming khi không có API key
    if ("changeme".equals(apiKey) || apiKey.isEmpty()) {
        simulateBotStreaming(convId, userId, "Mock response: " + userMessage);
        return;
    }
    // ... OpenAI call ...
}

private void simulateBotStreaming(String convId, Long userId, String fullText) {
    // Gửi từng ký tự với delay 50ms
    for (int i = 0; i < fullText.length(); i++) {
        final String chunk = String.valueOf(fullText.charAt(i));
        final boolean isDone = (i == fullText.length() - 1);
        final int delay = i * 50;

        context().system().scheduler().scheduleOnce(
            java.time.Duration.ofMillis(delay),
            () -> chatRoom.tell(new ChatRoomActor.BotChunk(convId, userId, chunk, isDone),
                              ActorRef.noSender()),
            context().dispatcher()
        );
    }
}
```

---

## 6. 🔄 Sự Tiến Hóa

| | Tuần 1-6 (Mock) | Tuần 7 (Real) |
|--|----------------|---------------|
| Bot reply | setTimeout random response | OpenAI GPT-4o-mini |
| Display | Dump toàn bộ | Streaming từng ký tự |
| Storage | Local state | MongoDB |
| Cost | Free | ~$0.001 per query |

---

## 7. 🎭 Mock Code Còn Lại

```java
// BotService: MOCK mode khi không có API key
// ConversationRepository: vẫn dùng simple ID format
// Settings: chưa implement (Tuần 8)
```

---

## 8. ⚠️ Pitfalls Tuần 7

**API Key bị lộ:**
- KHÔNG hardcode key vào code
- KHÔNG commit key vào git
- Dùng `${?OPENAI_API_KEY}` trong config, set qua env var
- Thêm `application.conf` vào `.gitignore` nếu có hardcode tạm

**OpenAI Rate Limit:**
```
RateLimitError: You exceeded your current quota
```
→ Kiểm tra billing trên OpenAI platform. Free tier rất hạn chế.

**SSE Parsing:**
```
# Cẩn thận với format:
data: {"choices":[...]}   ← Có "data: " prefix
\n                         ← Blank line giữa events
data: [DONE]              ← Signal kết thúc
```
→ Dùng `Framing.delimiter("\n")` để split lines đúng

**Browser WebSocket size limit:**
Mỗi WS message có giới hạn kích thước. Gửi chunk nhỏ (1-10 chars) là OK.

---

## 9. ✅ Checklist Tuần 7

- [ ] `OPENAI_API_KEY` đã set trong env (hoặc dùng mock mode)
- [ ] Chat với Bot → thấy typing indicator
- [ ] Từng ký tự xuất hiện dần (không dump toàn bộ)
- [ ] Full response lưu vào MongoDB sau khi stream xong
- [ ] Reload conversation → thấy full bot message (không phải chunks)
- [ ] Error handling: API key sai → thấy "[Error: ...]" thay vì crash

---

## 10. 🔗 Kết Nối Tuần 8

Tuần 8: Settings (bật/tắt typing indicator, notifications, v.v.) và Production build với Docker.
