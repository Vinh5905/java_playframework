package models;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Message {
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("hh:mm a").withZone(ZoneId.systemDefault());

    public String id;
    public String conversationId;
    public Long senderId;
    public String text;
    public Instant timestamp;
    public String time;

    public Message() {}

    public Message(String convId, Long senderId, String text) {
        this.conversationId = convId;
        this.senderId = senderId;
        this.text = text;
        this.timestamp = Instant.now();
        this.time = TIME_FORMAT.format(this.timestamp);
    }
}
