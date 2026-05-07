# Day 19 - JSON với Jackson và Play

## Mục tiêu
- Dùng `play.libs.Json` API
- Custom serialization/deserialization
- Jackson annotations quan trọng
- Xử lý các trường hợp đặc biệt (date, enum, optional)

---

## 1. Play Json API Cơ Bản

```java
import play.libs.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

// Serialize Java object → JSON
User user = new User(1L, "Alice", "alice@example.com");
JsonNode json = Json.toJson(user);
// → {"id":1,"name":"Alice","email":"alice@example.com"}

// Deserialize JSON → Java object
JsonNode input = request.body().asJson();
User parsed = Json.fromJson(input, User.class);

// Tạo JSON thủ công
ObjectNode obj = Json.newObject();
obj.put("id", 1);
obj.put("name", "Alice");
obj.put("active", true);
obj.putNull("deletedAt");

ArrayNode arr = Json.newArray();
arr.add("a");
arr.add("b");
arr.add(obj);

// Đọc giá trị từ JsonNode
String name = json.get("name").asText();
int id = json.get("id").asInt();
boolean active = json.get("active").asBoolean();
JsonNode nested = json.get("address");

// Check field existence
boolean hasField = json.has("email");
boolean fieldNotNull = !json.get("email").isNull();

// Iterate
json.fields().forEachRemaining(entry -> {
    System.out.println(entry.getKey() + " = " + entry.getValue());
});
```

---

## 2. Jackson Annotations Quan Trọng

```java
import com.fasterxml.jackson.annotation.*;
import java.time.Instant;
import java.util.Optional;

public class Product {

    public Long id;

    // Đổi tên field trong JSON
    @JsonProperty("product_name")
    public String name;

    // Serialize Instant thành ISO 8601 string
    @JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    public Instant createdAt;

    // Ẩn field trong JSON output (nhưng vẫn có thể deserialize)
    @JsonIgnore
    public String internalCode;

    // Ẩn khi serialize (write) nhưng vẫn đọc được (read)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String password;

    // Chỉ serialize (read), không deserialize (write)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String computedField;

    // Include field khi null (mặc định Jackson bỏ qua null)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String maybeNull;

    // Ignore field nếu null (mặc định behavior)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String anotherField;

    // Serialize enum thành string thay vì ordinal
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Status status;

    public enum Status { ACTIVE, INACTIVE, PENDING }
}
```

---

## 3. Custom Serializer

```java
// Custom date format
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.core.JsonGenerator;
import java.time.format.DateTimeFormatter;

public class InstantSerializer extends StdSerializer<Instant> {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public InstantSerializer() { super(Instant.class); }

    @Override
    public void serialize(Instant instant, JsonGenerator gen,
                          SerializerProvider provider) throws IOException {
        gen.writeString(FORMATTER.format(instant));
    }
}

// Dùng annotation
@JsonSerialize(using = InstantSerializer.class)
public Instant createdAt;
```

---

## 4. ObjectMapper Configuration

```java
// app/modules/JsonModule.java
// Custom global Jackson config

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import play.libs.Json;
import com.google.inject.AbstractModule;

public class JsonModule extends AbstractModule {
    @Override
    protected void configure() {
        // Cấu hình ObjectMapper global
        ObjectMapper mapper = Json.mapper();

        // Support Java 8 date/time types
        mapper.registerModule(new JavaTimeModule());

        // Serialize dates thành string (ISO 8601) thay vì timestamp số
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Không fail khi gặp field không biết trong JSON
        mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Indent JSON output (dev mode)
        // mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
}
```

```hocon
# application.conf
play.modules.enabled += "modules.JsonModule"
```

---

## 5. Xử Lý Optional và Nullable

```java
// Model với Optional
public class UserProfile {
    public Long id;
    public String name;

    // Optional field - null nếu chưa điền
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String bio;

    // Java Optional → Jackson cần JavaTimeModule + Jdk8Module
    public Optional<String> phone;
}

// build.sbt thêm:
// "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % "2.17.1"
```

---

## 6. Đọc Nested JSON

```java
public Result processNested(Http.Request request) {
    JsonNode body = request.body().asJson();

    // {"user": {"name": "Alice", "address": {"city": "HCM"}}}
    String name = body.path("user").path("name").asText();
    String city = body.path("user").path("address").path("city").asText("Unknown");

    // path() khác get():
    // - get("field") → null nếu không tồn tại
    // - path("field") → MissingNode (không throw NPE khi chain)
    boolean exists = !body.path("user").path("bio").isMissingNode();

    return ok("Name: " + name + ", City: " + city);
}
```

---

## 7. Bài Tập

Xem `json-demo/` trong thư mục này.

Exercises:
1. Serialize/deserialize `Instant` dates (ISO 8601 format)
2. Model với `@JsonProperty` đổi tên camelCase ↔ snake_case
3. Custom serializer cho enum (trả về label thay vì tên constant)
4. Xử lý polymorphic JSON (`@JsonTypeInfo`, `@JsonSubTypes`)

```bash
cd json-demo
sbt run

curl -X POST http://localhost:9000/json/echo \
  -H "Content-Type: application/json" \
  -d '{"product_name": "Widget", "status": "ACTIVE"}'
```
