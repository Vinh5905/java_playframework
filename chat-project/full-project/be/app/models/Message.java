package models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class Message {
    public String id;

    @JsonProperty("conversation_id")
    public String conversationId;

    @JsonProperty("sender_id")
    public Long senderId;

    public String text;
    public Instant timestamp;

    public Message() {}

    public Message(String convId, Long senderId, String text) {
        this.conversationId = convId;
        this.senderId = senderId;
        this.text = text;
        this.timestamp = Instant.now();
    }
}
