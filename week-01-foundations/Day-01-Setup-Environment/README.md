# Day 01 - Cài Đặt Môi Trường & Chạy Project Đầu Tiên

## Mục tiêu hôm nay
- Cài Java 17, sbt
- Tạo và chạy được Play project đầu tiên
- Hiểu tại sao Play khác Spring Boot ở mức thiết kế

---

## 1. Play Framework là gì?

Play là web framework theo mô hình **MVC**, xây trên nền **Apache Pekko** (phiên bản 3.x). Điểm khác biệt cốt lõi so với Spring Boot:

| Đặc điểm | Play 3.x | Spring Boot |
|----------|----------|-------------|
| Threading model | Non-blocking, event-loop | Thread-per-request (blocking mặc định) |
| Hot reload | Có sẵn (`sbt run`) | Cần DevTools plugin |
| Routing | File `conf/routes` riêng | Annotation `@RequestMapping` |
| Build tool | sbt | Maven hoặc Gradle |
| Số thread | ~ số CPU cores | Tomcat default 200 threads |

**Khi nào chọn Play?**
- API server cần xử lý nhiều concurrent connection (chat, streaming, notification)
- Team quen với reactive/async programming
- Cần type-safe routing

**Khi nào chọn Spring Boot?**
- Team đã quen Spring ecosystem
- Project nặng về JPA/Hibernate blocking queries
- Cần nhiều Spring starter có sẵn

---

## 2. Cài đặt trên macOS

### Bước 1: Java 17

```bash
# Cài via Homebrew
brew install openjdk@17

# Thêm vào ~/.zshrc
echo 'export JAVA_HOME=/opt/homebrew/opt/openjdk@17' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# Kiểm tra
java -version
# openjdk version "17.x.x"
```

> **Lưu ý**: Play 3.x yêu cầu Java 17 trở lên. Java 11 sẽ compile nhưng một số tính năng mới không dùng được. Java 21 cũng hoạt động tốt.

### Bước 2: sbt (Scala Build Tool)

```bash
brew install sbt
sbt --version
# sbt version in this project: ...
# sbt script version: 1.x.x
```

sbt là build tool chính thức của Play. Dù bạn viết Java, sbt vẫn cần thiết vì Play framework viết bằng Scala.

### Bước 3: IDE

**IntelliJ IDEA** (khuyến nghị):
- Cài plugin "Scala" (hỗ trợ sbt)
- File → Open → chọn thư mục project, chọn "Import as sbt project"

**VS Code** (alternative):
- Extension: "Metals" (Scala language server)
- Extension: "Play Framework"

---

## 3. Tạo project đầu tiên

```bash
# Dùng Giter8 template chính thức của Play
sbt new playframework/play-java-seed.g8

# Trả lời câu hỏi:
# name [play-java-seed]: hello-play
# organization [com.example]: com.example
# (Enter để dùng default)

cd hello-play
sbt run
```

> **Lần đầu chạy rất chậm (5-15 phút)** vì sbt download toàn bộ dependencies. Lần sau nhanh vì đã cache. Đừng tưởng bị treo!

Mở browser: **http://localhost:9000**

---

## 4. Cấu trúc project Play

```
hello-play/
├── app/                    ← Source code của bạn
│   ├── controllers/        ← Xử lý HTTP request
│   │   └── HomeController.java
│   └── views/              ← Twirl templates (HTML)
│       ├── index.scala.html
│       └── main.scala.html
│
├── conf/
│   ├── application.conf    ← Cấu hình app (HOCON format)
│   ├── routes              ← URL routing (QUAN TRỌNG)
│   ├── logback.xml         ← Cấu hình logging
│   └── messages            ← i18n text
│
├── public/                 ← Static files (CSS, JS, images)
├── test/                   ← Tests
│
├── project/
│   ├── build.properties    ← Phiên bản sbt
│   └── plugins.sbt         ← sbt plugins (Play plugin ở đây)
│
├── build.sbt               ← Dependencies và build config
└── target/                 ← Compiled output (đừng commit vào git)
```

---

## 5. Flow khi có HTTP Request

```
Browser gửi GET /
    ↓
[Pekko HTTP Server] lắng nghe port 9000
    ↓
[Filters] - pre-processing (logging, security headers...)
    ↓
[Router] - đọc conf/routes, tìm dòng khớp "GET /"
    ↓
controllers.HomeController.index()
    ↓
[Result] - ok("Hello World")
    ↓
HTTP Response 200
```

---

## 6. Hot Reload - Cơ chế đặc biệt của Play

**Spring DevTools** reload khi bạn save file.

**Play hot reload** hoạt động khác: reload xảy ra khi **request mới đến sau khi bạn sửa code**. Không reload ngay khi save.

```
Bạn sửa Controller.java và save
→ Chưa reload gì cả

Bạn refresh browser (gửi request)
→ Play phát hiện file thay đổi
→ Recompile nhanh (chỉ compile file thay đổi)
→ Phục vụ request với code mới
```

Điều này có nghĩa: nếu code compile lỗi, bạn thấy **error page đẹp** ngay trong browser, không cần nhìn terminal.

---

## 7. Các lỗi thường gặp Day 1

**Lỗi 1: Port 9000 đã bị dùng**
```bash
# Đổi port
sbt "run 9001"
# Hoặc thêm vào conf/application.conf:
# play.server.http.port = 9001
```

**Lỗi 2: Java version sai**
```bash
java -version  # phải là 17+
# Nếu sai, kiểm tra JAVA_HOME
echo $JAVA_HOME
```

**Lỗi 3: sbt download chậm**
```bash
# Thêm mirror vào ~/.sbt/repositories (nếu ở VN)
# Hoặc dùng VPN nếu bị chặn
```

**Lỗi 4: Memory heap khi compile**
```bash
# Thêm vào ~/.sbtopts
echo "-J-Xmx2G" >> ~/.sbtopts
```

---

## 8. Bài tập

1. Chạy được `hello-play` tại http://localhost:9000
2. Sửa `HomeController.java` để trả về text "Hello, Play 3!"
3. Thêm 1 route mới: `GET /hello` → trả về "World"
4. Quan sát hot reload khi sửa code

## File code thực hành

Xem thư mục `02-first-project/hello-play/` - đây là project Play đầy đủ bạn có thể chạy ngay.

```bash
cd 02-first-project/hello-play
sbt run
```
