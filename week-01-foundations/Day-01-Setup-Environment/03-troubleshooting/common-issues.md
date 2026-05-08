# Troubleshooting - Các Lỗi Thường Gặp

---

## Lỗi 1: "The import play cannot be resolved" trong VSCode

**Triệu chứng:**
```
The import play cannot be resolved  Java(268435846)
package play.xxx does not exist
```

**Nguyên nhân:**  
Metals (Java/Scala LSP trong VSCode) chưa import build từ sbt. Metals không biết Play JARs ở đâu trên classpath → báo đỏ toàn bộ import.

**Cách fix — làm theo thứ tự:**

**Bước 1 — Đặt đúng JAVA_HOME (bắt buộc, làm một lần):**
```bash
# Mở terminal, chạy lệnh này để thêm vào shell profile
echo 'export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# Kiểm tra
java -version   # Phải ra: openjdk version "21.x.x"
```

> Lý do: sbt dùng `$JAVA_HOME` để biết dùng JDK nào. Nếu JAVA_HOME sai hoặc trống, sbt sẽ dùng JDK mặc định của hệ thống (có thể là JDK 25).

**Bước 2 — Compile lần đầu để tải dependencies:**
```bash
cd path/to/project   # VD: cd week-01-foundations/Day-01-Setup-Environment/02-first-project/hello-play

sbt compile
```
> Lệnh này tải Play Framework JARs về `~/.ivy2/cache/` (~2–5 phút lần đầu, có internet).  
> Các lần sau chỉ mất vài giây vì dùng cache.

**Bước 3 — Mở đúng folder trong VSCode:**
```
VSCode → File → Open Folder → chọn đúng folder chứa build.sbt
```
> **Quan trọng:** Phải mở folder chứa `build.sbt`, không mở folder cha.  
> Nếu mở sai folder, Metals sẽ không nhận ra đây là sbt project.

**Bước 4 — Import Build vào Metals:**

Cách A — Dùng popup tự động:
```
VSCode hiện popup "Import build?" → Click "Import build"
```

Cách B — Chạy command thủ công:
```
Cmd+Shift+P → gõ "Metals: Import Build" → Enter
```

> Lệnh này bảo Metals chạy `sbt bspConfig` để lấy classpath từ sbt.  
> Chờ ~1–2 phút. Thanh trạng thái góc dưới bên trái VSCode sẽ hiện tiến trình.

**Bước 5 — Kiểm tra:**
- Mở `app/controllers/HomeController.java`
- Lỗi đỏ `import play` biến mất
- Hover chuột lên `play.mvc.Controller` → hiện Javadoc

---

**Nếu vẫn còn lỗi sau khi làm đủ bước trên:**
```bash
# Xóa bloop cache và import lại
rm -rf .bloop project/.bloop target

# Trong VSCode: Cmd+Shift+P → "Metals: Restart Build Server"
# Rồi "Metals: Import Build" lại
```

---

## Lỗi 2: sbt dùng nhầm JDK (JDK 25 thay vì JDK 21)

**Triệu chứng:**
```bash
$ java -version
openjdk version "25.0.x"   # Không phải JDK 21
```

**Nguyên nhân:**  
Máy cài nhiều JDK, Homebrew JDK 25 là default.

**Fix vĩnh viễn — thêm vào `~/.zshrc`:**
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

**Hoặc dùng tạm thời cho một session:**
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
sbt run   # Chạy trong cùng terminal
```

**Kiểm tra:**
```bash
source ~/.zshrc
java -version        # → openjdk version "21.x.x"
sbt "java -version"  # → sbt cũng dùng JDK 21
```

---

## Lỗi 3: Port 9000 Already in Use

**Triệu chứng:**
```
[error] Bind failed for TCP channel on endpoint [/0.0.0.0:9000]
```

**Fix:**
```bash
# Tìm process đang dùng port 9000
lsof -i :9000

# Kill process đó (thay PID bằng số thực)
kill -9 <PID>

# Hoặc chạy Play trên port khác
sbt "run 9001"
```

---

## Lỗi 4: sbt Download Chậm / Timeout

**Nguyên nhân:** Repository ở nước ngoài, mạng VN chậm.

**Fix:**
```bash
# Thử dùng VPN

# Hoặc thêm mirror vào ~/.sbt/1.0/global.sbt
cat >> ~/.sbt/1.0/global.sbt << 'EOF'
resolvers += "Central Mirror" at "https://repo1.maven.org/maven2/"
EOF
```

---

## Lỗi 5: OutOfMemoryError khi compile

**Triệu chứng:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Fix:**
```bash
# Tạo/sửa file ~/.sbtopts
echo "-J-Xmx4G" >> ~/.sbtopts
echo "-J-Xms512M" >> ~/.sbtopts
```

---

## Lỗi 6: "Not a valid command: run" trong sbt shell

**Nguyên nhân:** `build.sbt` thiếu `enablePlugins(PlayJava)`.

**Fix:** Kiểm tra `build.sbt` có dòng:
```scala
lazy val root = (project in file(".")).enablePlugins(PlayJava)
```

---

## Lỗi 7: CSRF Token Error khi POST

**Triệu chứng:** POST request bị reject với lỗi CSRF.

**Fix tạm thời trong dev** (thêm vào `application.conf`):
```hocon
play.filters.disabled += "play.filters.csrf.CSRFFilter"
```

**Fix đúng khi test bằng curl:**
```bash
curl -X POST http://localhost:9000/todos \
  -H "Csrf-Token: nocheck" \
  -H "Content-Type: application/json" \
  -d '{"title":"test"}'
```

---

## Lỗi 8: Hot Reload Không Hoạt Động

**Nguyên nhân:** Play hot reload chỉ kích hoạt khi có **request mới đến**, không phải khi save.

**Fix:** Sau khi sửa code → refresh browser (F5) → Play recompile tự động.

Nếu vẫn không reload: `sbt clean` rồi `sbt run` lại.

---

## Cheat sheet — Các lệnh sbt thường dùng

| Lệnh | Ý nghĩa |
|------|---------|
| `sbt compile` | Compile code, tải dependencies nếu chưa có |
| `sbt run` | Chạy server ở port 9000 với hot reload |
| `sbt "run 9001"` | Chạy server ở port 9001 |
| `sbt test` | Chạy tất cả unit tests |
| `sbt clean` | Xóa thư mục `target/` (build artifacts) |
| `sbt clean compile` | Compile từ đầu hoàn toàn |
| `sbt stage` | Build production JAR |
| `sbt dist` | Build production ZIP package |
| `sbt` | Vào sbt interactive shell |
| `~compile` | (trong sbt shell) Auto-compile khi file thay đổi |

## Cheat sheet — Metals VSCode commands

| Command (Cmd+Shift+P) | Khi nào dùng |
|-----------------------|-------------|
| `Metals: Import Build` | Lần đầu mở project, hoặc khi sửa build.sbt |
| `Metals: Restart Build Server` | Khi Metals bị stuck / không phản hồi |
| `Metals: Run Doctor` | Kiểm tra Metals setup có vấn đề gì |
| `Metals: Compile Cascade` | Force compile toàn bộ project |
| `Metals: Reset Workspace` | Reset toàn bộ Metals data, làm mới hoàn toàn |
