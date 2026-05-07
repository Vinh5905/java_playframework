# 🗺️ Lộ Trình Dự Án: Real-time Chat App

## Tổng Quan 8 Tuần

| Tuần | Tính năng | Công nghệ Play mới | Kết quả thấy được |
|------|-----------|--------------------|--------------------|
| **1** | Account Switcher + REST cơ bản | Routing, Controller, JSON | Chọn account, thấy danh sách giả, giao diện đẹp |
| **2** | User persistence (PostgreSQL) | JDBC async, Evolutions, HikariCP | Accounts lưu vào DB thật, restart không mất |
| **3** | Chat messages (MongoDB) | Reactive MongoDB driver | Gửi/nhận tin lưu vào DB, phân trang lịch sử |
| **4** | Real-time WebSocket | Pekko Streams, WebSocket actors | Tin nhắn xuất hiện ngay không cần reload |
| **5** | Typing + Online Status | Pekko Pub/Sub, Presence system | "A đang soạn tin...", dot xanh/xám real-time |
| **6** | Global Chat Room | Broadcast actor, Room management | Room chung cho tất cả users online |
| **7** | ChatGPT Bot Streaming | OpenAI API, SSE streaming | Bot gõ từng ký tự như người thật |
| **8** | Settings + Hoàn thiện | Play Cache, Filters, Production | Toggle tính năng, deploy Docker |

---

## Cấu Trúc `full-project/be` (Backend Hoàn Chỉnh)

```
full-project/be/
├── app/
│   │
│   ├── actors/                          ← Pekko Actors (Tuần 4-7)
│   │   ├── ChatRoomActor.java           ← Quản lý 1 conversation
│   │   ├── GlobalRoomActor.java         ← Global chat room
│   │   ├── PresenceActor.java           ← Track online/offline
│   │   └── BotActor.java               ← ChatGPT streaming
│   │
│   ├── controllers/
│   │   ├── AccountController.java       ← GET/POST accounts (Tuần 1-2)
│   │   ├── ConversationController.java  ← CRUD conversations (Tuần 3)
│   │   ├── MessageController.java       ← Send/get messages (Tuần 3)
│   │   ├── WebSocketController.java     ← WS endpoint (Tuần 4)
│   │   ├── GlobalChatController.java    ← Global room WS (Tuần 6)
│   │   └── SettingsController.java      ← User settings (Tuần 8)
│   │
│   ├── models/
│   │   ├── Account.java                 ← User model
│   │   ├── Conversation.java
│   │   ├── Message.java                 ← MongoDB document
│   │   └── Settings.java
│   │
│   ├── repositories/
│   │   ├── AccountRepository.java       ← PostgreSQL async JDBC
│   │   ├── ConversationRepository.java  ← PostgreSQL
│   │   └── MessageRepository.java       ← MongoDB reactive
│   │
│   └── services/
│       ├── AccountService.java          ← Business logic
│       ├── PresenceService.java         ← Online tracking
│       ├── TypingService.java           ← Typing indicator
│       ├── BotService.java             ← OpenAI integration
│       └── NotificationService.java
│
├── conf/
│   ├── application.conf                 ← Tất cả config
│   ├── routes                           ← Tất cả routes
│   ├── logback.xml
│   └── evolutions/default/
│       ├── 1.sql                        ← accounts table
│       ├── 2.sql                        ← conversations table
│       └── 3.sql                        ← settings table
│
├── build.sbt                            ← Tất cả dependencies
└── project/
    ├── build.properties
    └── plugins.sbt
```

---

## Tech Stack Tổng Hợp

```
Backend (Play Framework 3.x + Java 17)
├── Web Server:    Pekko HTTP (built-in)
├── Async:         CompletionStage + Pekko Actors
├── PostgreSQL:    JDBC async (play.db + HikariCP)
│   └── Lưu: accounts, conversations, settings
├── MongoDB:       Reactive Streams driver
│   └── Lưu: messages (NoSQL vì dạng log)
├── WebSocket:     Pekko Streams + WebSocket.Message
├── External API:  Play WS Client → OpenAI GPT-4
└── Cache:         Play Cache API (EhCache/Redis)

Frontend (Vanilla JS + CSS - không cần build)
├── Serve:  python3 -m http.server 3000
├── API:    fetch() với fallback về mock data
└── WS:     native WebSocket API
```

---

## Cách Sử Dụng

```bash
# Bước 1: Mở Frontend (tuần nào cũng dùng lệnh này)
cd chat-project/your-project/fe
python3 -m http.server 3000
# → Mở http://localhost:3000

# Bước 2: Chạy Backend (sau khi code theo hướng dẫn)
cd chat-project/your-project/be
sbt run
# → Backend chạy ở http://localhost:9000

# Bước 3: Đối chiếu khi bí
# Xem chat-project/full-project/be/ để biết code đúng trông như thế nào
```

---

## File Hướng Dẫn

| File | Nội dung |
|------|----------|
| `week-01.md` | Setup + Account Switcher + Play basics |
| `week-02.md` | PostgreSQL + Async JDBC |
| `week-03.md` | MongoDB + Message API |
| `week-04.md` | WebSocket + Real-time |
| `week-05.md` | Typing Indicator + Presence |
| `week-06.md` | Global Chat Room |
| `week-07.md` | ChatGPT Bot Streaming |
| `week-08.md` | Settings + Production Build |
