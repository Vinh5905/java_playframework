package models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Account {
    public Long id;
    public String name;
    public String username;

    @JsonProperty("isBot")
    public boolean isBot;

    public Account() {}

    public Account(Long id, String name, String username, boolean isBot) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.isBot = isBot;
    }
}
