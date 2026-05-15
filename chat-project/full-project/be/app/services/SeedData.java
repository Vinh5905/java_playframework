package services;

import models.Account;
import models.Conversation;
import models.Message;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class SeedData {
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("hh:mm a").withZone(ZoneId.systemDefault());

    private SeedData() {}

    public static List<Account> accounts() {
        return List.of(
            new Account(1L, "Alice Johnson", "alice", false),
            new Account(2L, "Bob Smith", "bob", false),
            new Account(3L, "Elmer Laverty", "elmer", false),
            new Account(4L, "Florencio Dorrance", "florencio", false),
            new Account(5L, "Lavern Laboy", "lavern", false),
            new Account(6L, "Titus Kitamura", "titus", false),
            new Account(7L, "Geoffrey Mott", "geoffrey", false),
            new Account(8L, "Alfonzo Schuessler", "alfonzo", false),
            new Account(9L, "ChatGPT Bot", "gpt_bot", true)
        );
    }

    public static List<Conversation> conversationsFor(Long accountId) {
        if (accountId == null || accountId == 1L) {
            return aliceConversations();
        }

        List<Conversation> conversations = new ArrayList<>();
        conversations.add(conversationForPair(accountId, 1L));
        conversations.add(conversationForPair(accountId, 9L));
        accounts().stream()
            .filter(a -> !a.id.equals(accountId) && a.id != 1L && a.id != 9L)
            .forEach(a -> conversations.add(conversationForPair(accountId, a.id)));
        return conversations;
    }

    public static Conversation conversationForPair(Long viewerId, Long partnerId) {
        String id = canonicalConversationId(viewerId, partnerId);
        Conversation aliceView = aliceConversations().stream()
            .filter(c -> c.id.equals(id))
            .findFirst()
            .orElse(null);

        if (aliceView != null) {
            return new Conversation(
                aliceView.id,
                partnerId,
                aliceView.lastMessage,
                aliceView.lastTime,
                viewerId == 1L ? aliceView.unread : 0,
                aliceView.tags
            );
        }

        return new Conversation(
            id,
            partnerId,
            "Start a conversation",
            "Now",
            0,
            List.of(new Conversation.Tag("Direct", "gray"))
        );
    }

    public static String canonicalConversationId(Long userA, Long userB) {
        long min = Math.min(userA, userB);
        long max = Math.max(userA, userB);
        if (min == 1L && max >= 3L && max <= 9L) {
            return String.valueOf(max - 2L);
        }
        return "dm-" + min + "-" + max;
    }

    public static List<Conversation> aliceConversations() {
        return List.of(
            new Conversation("1", 3L, "Haha oh man", "12m", 2,
                List.of(new Conversation.Tag("Question", "orange"), new Conversation.Tag("Help wanted", "green"))),
            new Conversation("2", 4L, "woohoooo", "24m", 0,
                List.of(new Conversation.Tag("Some content", "gray"))),
            new Conversation("3", 5L, "Haha that's terrifying", "1h", 0,
                List.of(new Conversation.Tag("Bug", "red"), new Conversation.Tag("Hacktoberfest", "green"))),
            new Conversation("4", 6L, "omg, this is amazing", "5h", 1,
                List.of(new Conversation.Tag("Question", "orange"), new Conversation.Tag("Some content", "gray"))),
            new Conversation("5", 7L, "aww", "2d", 0,
                List.of(new Conversation.Tag("Request", "green"))),
            new Conversation("6", 8L, "perfect!", "1m", 0,
                List.of(new Conversation.Tag("Follow up", "gray"))),
            new Conversation("7", 9L, "Sure! How can I help?", "3m", 0,
                List.of(new Conversation.Tag("Bot", "purple")))
        );
    }

    public static List<Message> messages(String conversationId) {
        Instant now = Instant.now();
        List<Message> messages = new ArrayList<>();
        switch (conversationId) {
            case "1" -> {
                messages.add(seedMessage("1", "seed-1-1", 3L, "Hey! Are you free to review my PR?", now.minusSeconds(3600)));
                messages.add(seedMessage("1", "seed-1-2", 1L, "Sure, link it here!", now.minusSeconds(3480)));
                messages.add(seedMessage("1", "seed-1-3", 3L, "github.com/elmer/awesome-project", now.minusSeconds(3420)));
                messages.add(seedMessage("1", "seed-1-4", 3L, "Haha oh man", now.minusSeconds(3300)));
            }
            case "2" -> {
                messages.add(seedMessage("2", "seed-2-1", 4L, "omg, this is amazing", now.minusSeconds(900)));
                messages.add(seedMessage("2", "seed-2-2", 4L, "perfect!", now.minusSeconds(880)));
                messages.add(seedMessage("2", "seed-2-3", 4L, "Wow, this is really epic", now.minusSeconds(840)));
                messages.add(seedMessage("2", "seed-2-4", 1L, "How are you?", now.minusSeconds(780)));
                messages.add(seedMessage("2", "seed-2-5", 4L, "just ideas for next time", now.minusSeconds(660)));
                messages.add(seedMessage("2", "seed-2-6", 4L, "I'll be there in 2 mins", now.minusSeconds(630)));
                messages.add(seedMessage("2", "seed-2-7", 1L, "woohoooo", now.minusSeconds(520)));
                messages.add(seedMessage("2", "seed-2-8", 1L, "Haha oh man", now.minusSeconds(500)));
                messages.add(seedMessage("2", "seed-2-9", 1L, "Haha that's terrifying", now.minusSeconds(420)));
                messages.add(seedMessage("2", "seed-2-10", 4L, "aww", now.minusSeconds(360)));
                messages.add(seedMessage("2", "seed-2-11", 4L, "omg, this is amazing", now.minusSeconds(320)));
                messages.add(seedMessage("2", "seed-2-12", 4L, "woohoooo", now.minusSeconds(280)));
            }
            case "7" -> {
                messages.add(seedMessage("7", "seed-7-1", 1L, "Hello! Can you help me with Play Framework?", now.minusSeconds(5000)));
                messages.add(seedMessage("7", "seed-7-2", 9L, "Of course. Play Framework is a reactive web framework built on top of Pekko. What would you like to know?", now.minusSeconds(4960)));
                messages.add(seedMessage("7", "seed-7-3", 1L, "What's the difference between sync and async actions?", now.minusSeconds(4900)));
                messages.add(seedMessage("7", "seed-7-4", 9L, "Async actions return a CompletionStage so Play can release the request thread while waiting for I/O.", now.minusSeconds(4860)));
            }
            case "global" -> {
                messages.add(seedMessage("global", "seed-global-1", 1L, "Welcome to Global Chat.", now.minusSeconds(600)));
                messages.add(seedMessage("global", "seed-global-2", 4L, "Everyone online can see messages here.", now.minusSeconds(560)));
            }
            default -> {
            }
        }
        return messages;
    }

    public static Message seedMessage(String conversationId, String id, Long senderId, String text, Instant timestamp) {
        Message msg = new Message(conversationId, senderId, text);
        msg.id = id;
        msg.timestamp = timestamp;
        msg.time = TIME_FORMAT.format(timestamp);
        return msg;
    }
}
