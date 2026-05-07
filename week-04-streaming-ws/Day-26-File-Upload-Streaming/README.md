# Day 26 - File Upload & Download Streaming

## Mục tiêu
- Xử lý file upload (multipart form data)
- Stream file download không tốn RAM
- Validate file type và size

---

## 1. File Upload

```java
import play.mvc.*;
import play.libs.Files;

public class FileController extends Controller {

    // POST /upload - multipart form data
    public Result upload(Http.Request request) {
        Http.MultipartFormData<Files.TemporaryFile> body =
            request.body().asMultipartFormData();

        Http.MultipartFormData.FilePart<Files.TemporaryFile> filePart =
            body.getFile("file");

        if (filePart == null) {
            return badRequest("No file uploaded");
        }

        String filename = filePart.getFilename();
        String contentType = filePart.getContentType();
        long fileSize = filePart.getFileSize();

        // Validate
        if (!contentType.startsWith("image/")) {
            return badRequest("Only images allowed");
        }
        if (fileSize > 5 * 1024 * 1024) {  // 5MB
            return badRequest("File too large. Max 5MB");
        }

        // Lưu file
        Files.TemporaryFile file = filePart.getRef();
        Path dest = Paths.get("uploads", filename);
        file.copyTo(dest, true);

        ObjectNode response = Json.newObject();
        response.put("filename", filename);
        response.put("size", fileSize);
        response.put("url", "/files/" + filename);
        return ok(response);
    }
}
```

---

## 2. Stream File Download

```java
// Stream file từ disk - không load vào RAM
public Result download(String filename) {
    Path filePath = Paths.get("uploads", filename);

    if (!Files.exists(filePath)) {
        return notFound("File not found: " + filename);
    }

    // Source từ file
    Source<ByteString, ?> source = FileIO.fromPath(filePath);

    // Detect MIME type
    String contentType = "application/octet-stream";
    try {
        contentType = Files.probeContentType(filePath);
    } catch (Exception ignored) {}

    return ok()
        .chunked(source)
        .as(contentType)
        .withHeader("Content-Disposition", "attachment; filename=" + filename);
}

// Stream ảnh inline (không download)
public Result serveImage(String filename) {
    Path filePath = Paths.get("uploads", filename);
    Source<ByteString, ?> source = FileIO.fromPath(filePath);
    return ok().chunked(source).as("image/jpeg");
}
```

---

## 3. Multipart Upload với Pekko Streams

```java
// Xử lý upload lớn (multi-GB) mà không tốn RAM
public CompletionStage<Result> streamUpload(Http.Request request) {
    return request.body().asMultipartFormData().runWith(
        Sink.foreach(part -> {
            if (part instanceof FilePart) {
                FilePart<?> filePart = (FilePart<?>) part;
                // Process file chunks as they arrive
                // Không cần đợi toàn bộ file upload xong
            }
        }),
        materializer
    ).thenApply(done -> ok("Upload complete"));
}
```

---

## 4. Cấu Hình Upload Size

```hocon
# application.conf
# Tăng giới hạn body size
play.http.parser.maxMemoryBuffer = 10MB
play.http.parser.maxDiskBuffer = 100MB

# Multipart
play.http.parser.maxDiskBuffer = 100MB
```

---

## 5. Bài Tập

```bash
# Test upload
curl -X POST http://localhost:9000/upload \
  -F "file=@/path/to/image.jpg"

# Test download
curl http://localhost:9000/files/image.jpg -o downloaded.jpg

# Test size limit (file > 5MB)
dd if=/dev/zero bs=6MB count=1 | curl -X POST http://localhost:9000/upload \
  -F "file=@-;filename=big.bin"
# → 400 File too large
```
