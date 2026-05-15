package models;

import java.util.List;

public class Conversation {
    public String id;
    public Long participantId;
    public String name;
    public boolean isGlobal;
    public String lastMessage;
    public String lastTime;
    public int unread;
    public List<Tag> tags;

    public Conversation() {}

    public Conversation(String id, Long participantId, String lastMessage, String lastTime, int unread, List<Tag> tags) {
        this.id = id;
        this.participantId = participantId;
        this.lastMessage = lastMessage;
        this.lastTime = lastTime;
        this.unread = unread;
        this.tags = tags;
        this.isGlobal = false;
    }

    public static Conversation global() {
        Conversation c = new Conversation();
        c.id = "global";
        c.name = "Global Chat";
        c.isGlobal = true;
        c.lastMessage = "Everyone can chat here";
        c.lastTime = "Now";
        c.unread = 0;
        c.tags = List.of(new Tag("Global", "blue"));
        return c;
    }

    public static class Tag {
        public String label;
        public String color;

        public Tag() {}

        public Tag(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }
}
