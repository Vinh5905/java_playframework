# Troubleshooting - Các Lỗi Thường Gặp Day 1

## Lỗi 1: Port 9000 Already in Use

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

## Lỗi 2: Java Version Không Đúng

**Triệu chứng:**
```
[error] Java 17 or higher is required
```

**Fix:**
```bash
java -version          # Kiểm tra version hiện tại
echo $JAVA_HOME        # Kiểm tra JAVA_HOME

# macOS: có thể có nhiều JDK, chọn đúng
/usr/libexec/java_home -V    # Liệt kê tất cả JDK
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

---

## Lỗi 3: sbt Download Chậm / Timeout

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

## Lỗi 4: OutOfMemoryError khi compile

**Triệu chứng:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Fix:**
```bash
# Tạo file ~/.sbtopts với nội dung:
echo "-J-Xmx4G\n-J-Xms512M" > ~/.sbtopts
```

---

## Lỗi 5: "Not a valid command: run" trong sbt shell

**Triệu chứng:** Chạy `sbt run` nhưng báo lỗi command không tồn tại.

**Nguyên nhân:** `build.sbt` thiếu `enablePlugins(PlayJava)`.

**Fix:** Kiểm tra `build.sbt` có dòng:
```scala
lazy val root = (project in file(".")).enablePlugins(PlayJava)
```

---

## Lỗi 6: CSRF Token Error khi POST

**Triệu chứng:** POST request bị reject với lỗi CSRF.

**Fix tạm thời trong dev** (thêm vào `application.conf`):
```hocon
play.filters.csrf.bypassCorsTrustedOrigins = true
# Hoặc disable CSRF hoàn toàn (CHỈ trong dev!)
play.filters.disabled += "play.filters.csrf.CSRFFilter"
```

**Fix đúng:** Gửi header `Csrf-Token: nocheck` khi test bằng curl:
```bash
curl -X POST http://localhost:9000/todos \
  -H "Csrf-Token: nocheck" \
  -H "Content-Type: application/json" \
  -d '{"title":"test"}'
```

---

## Lỗi 7: Hot Reload Không Hoạt Động

**Triệu chứng:** Sửa code nhưng browser vẫn show code cũ.

**Nguyên nhân:** Play hot reload chỉ kích hoạt khi có **request mới đến**, không phải khi save.

**Fix:** Sau khi sửa code → refresh browser (F5) → Play sẽ recompile.

Nếu vẫn không reload: `sbt clean` rồi `sbt run` lại.
