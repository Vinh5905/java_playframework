package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class User {
    public Long id;
    public String email;
    public String name;

    @JsonIgnore  // Không expose password hash trong JSON response
    public String passwordHash;

    @JsonProperty("created_at")
    public String createdAt;

    public User() {}

    public User(Long id, String email, String name, String passwordHash, String createdAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }
}
