package models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Model đơn giản, không dùng JPA/ORM.
 * Lưu in-memory trong TodoRepository.
 *
 * Jackson tự serialize/deserialize với Play Json.toJson() / Json.fromJson().
 */
public class Todo {

    public Long id;
    public String title;
    public boolean done;

    @JsonProperty("created_at")
    public String createdAt;

    // Constructor cho tạo mới
    public Todo(Long id, String title, boolean done) {
        this.id = id;
        this.title = title;
        this.done = done;
        this.createdAt = Instant.now().toString();
    }

    // Default constructor cần thiết cho Jackson deserialization
    public Todo() {}
}
