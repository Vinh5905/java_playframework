package models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class Todo {
    public Long id;
    public String title;
    public boolean done;

    @JsonProperty("created_at")
    public String createdAt;

    public Todo(Long id, String title, boolean done) {
        this.id = id;
        this.title = title;
        this.done = done;
        this.createdAt = Instant.now().toString();
    }

    public Todo() {}
}
