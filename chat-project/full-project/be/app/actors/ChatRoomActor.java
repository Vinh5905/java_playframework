package actors;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import play.libs.Json;
import com.google.inject.Provider;
import repositories.MessageRepository;
import services.BotService;
import services.PresenceService;
import services.SettingsService;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Singleton actor - quản lý TẤT CẢ connections và broadcast messages.
 * Inject qua Guice (AppModule.bindActor).
 */
public class ChatRoomActor extends AbstractActor {

    // ── Message types ────────────────────────────────────────
    public static class UserConnected    {
        public final Long accountId; public final ActorRef actor;
        public UserConnected(Long id, ActorRef a) { accountId=id; actor=a; }
    }
    public static class UserDisconnected {
        public final Long accountId;
        public UserDisconnected(Long id) { accountId=id; }
    }
    public static class SendMessage {
        public final String convId; public final Long senderId; public final String text;
        public SendMessage(String c, Long s, String t) { convId=c; senderId=s; text=t; }
    }
    public static class TypingEvent {
        public final String convId; public final Long userId; public final boolean isTyping;
        public TypingEvent(String c, Long u, boolean t) { convId=c; userId=u; isTyping=t; }
    }
    public static class BotChunk {
        public final String convId; public final Long targetUserId;
        public final String chunk;  public final boolean isDone;
        public BotChunk(String c, Long u, String ch, boolean d) {
            convId=c; targetUserId=u; chunk=ch; isDone=d;
        }
    }
    public static class MessageSaved {
        public final Long senderId;
        public final String originalText;
        public final boolean isWithBot;
        public final ActorRef senderActor;
        public final models.Message message;
        public MessageSaved(Long senderId, String originalText, boolean isWithBot,
                            ActorRef senderActor, models.Message message) {
            this.senderId = senderId;
            this.originalText = originalText;
            this.isWithBot = isWithBot;
            this.senderActor = senderActor;
            this.message = message;
        }
    }
    public static class TypingReady {
        public final TypingEvent event;
        public final boolean allowed;
        public final ActorRef senderActor;
        public TypingReady(TypingEvent event, boolean allowed, ActorRef senderActor) {
            this.event = event;
            this.allowed = allowed;
            this.senderActor = senderActor;
        }
    }

    // ── State ────────────────────────────────────────────────
    private final Map<Long, Set<ActorRef>> connectedUsers = new HashMap<>();
    private final MessageRepository messageRepo;
    private final PresenceService presenceService;
    private final SettingsService settingsService;
    private final Provider<BotService> botServiceProvider;

    public static Props props(MessageRepository mr, PresenceService ps, SettingsService ss, Provider<BotService> bs) {
        return Props.create(ChatRoomActor.class, () -> new ChatRoomActor(mr, ps, ss, bs));
    }

    @Inject
    public ChatRoomActor(MessageRepository mr, PresenceService ps, SettingsService ss, Provider<BotService> bs) {
        this.messageRepo   = mr;
        this.presenceService = ps;
        this.settingsService  = ss;
        this.botServiceProvider = bs;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(UserConnected.class, msg -> {
                boolean alreadyOnline = connectedUsers.containsKey(msg.accountId)
                    && !connectedUsers.get(msg.accountId).isEmpty();
                connectedUsers.computeIfAbsent(msg.accountId, id -> new HashSet<>()).add(msg.actor);
                presenceService.markOnline(msg.accountId);
                sendPresenceSnapshot(msg.actor);
                if (!alreadyOnline) {
                    broadcastPresence(msg.accountId, "online");
                }
            })
            .match(UserDisconnected.class, msg -> {
                Set<ActorRef> actors = connectedUsers.get(msg.accountId);
                if (actors != null) {
                    actors.remove(sender());
                    if (actors.isEmpty()) {
                        connectedUsers.remove(msg.accountId);
                        presenceService.markOffline(msg.accountId);
                        broadcastPresence(msg.accountId, "offline");
                    }
                }
            })
            .match(SendMessage.class, this::handleSendMessage)
            .match(MessageSaved.class, this::handleMessageSaved)
            .match(TypingEvent.class, this::handleTyping)
            .match(TypingReady.class, this::handleTypingReady)
            .match(BotChunk.class, this::handleBotChunk)
            .build();
    }

    private void handleSendMessage(SendMessage cmd) {
        boolean isWithBot = "7".equals(cmd.convId) || cmd.convId.contains("bot") || cmd.convId.endsWith("-9");
        ActorRef senderActor = sender();

        var msg = new models.Message(cmd.convId, cmd.senderId, cmd.text);
        messageRepo.saveMessage(msg).thenAccept(saved -> {
            self().tell(new MessageSaved(cmd.senderId, cmd.text, isWithBot, senderActor, saved), self());
        });
    }

    private void handleMessageSaved(MessageSaved event) {
        ObjectNode payload = Json.newObject()
            .put("type", "message")
            .put("convId", event.message.conversationId);
        payload.set("message", Json.toJson(event.message));

        Set<Long> recipients = recipientsFor(event.message.conversationId);
        connectedUsers.forEach((id, actors) -> {
            if (recipients.isEmpty() || recipients.contains(id)) {
                actors.forEach(actor -> {
                    if (!actor.equals(event.senderActor)) {
                        actor.tell(new UserConnectionActor.Outgoing(payload), self());
                    }
                });
            }
        });

        if (event.isWithBot) {
            botServiceProvider.get().streamResponse(event.originalText, event.message.conversationId, event.senderId);
        }
    }

    private void handleTyping(TypingEvent event) {
        ActorRef senderActor = sender();
        settingsService.getSettings(event.userId).thenAccept(settings -> {
            self().tell(new TypingReady(event, settings.typingIndicators, senderActor), self());
        });
    }

    private void handleTypingReady(TypingReady ready) {
        if (!ready.allowed && ready.event.isTyping) return;

        ObjectNode payload = Json.newObject()
            .put("type", "typing")
            .put("convId", ready.event.convId)
            .put("userId", ready.event.userId)
            .put("isTyping", ready.event.isTyping);

        Set<Long> recipients = recipientsFor(ready.event.convId);
        connectedUsers.forEach((id, actors) -> {
            if (!id.equals(ready.event.userId) && (recipients.isEmpty() || recipients.contains(id))) {
                actors.forEach(actor -> actor.tell(new UserConnectionActor.Outgoing(payload), self()));
            }
        });
    }

    private void handleBotChunk(BotChunk bc) {
        Set<ActorRef> targets = connectedUsers.get(bc.targetUserId);
        if (targets == null || targets.isEmpty()) return;

        ObjectNode payload = Json.newObject()
            .put("type", "bot_chunk")
            .put("convId", bc.convId)
            .put("chunk", bc.chunk)
            .put("isDone", bc.isDone);

        targets.forEach(target -> target.tell(new UserConnectionActor.Outgoing(payload), self()));
    }

    private void broadcastPresence(Long userId, String status) {
        ObjectNode payload = Json.newObject()
            .put("type", "presence")
            .put("userId", userId)
            .put("status", status);

        connectedUsers.forEach((id, actors) -> {
            if (!id.equals(userId)) {
                actors.forEach(actor -> actor.tell(new UserConnectionActor.Outgoing(payload), self()));
            }
        });
    }

    private void sendPresenceSnapshot(ActorRef actor) {
        ObjectNode payload = Json.newObject()
            .put("type", "presence_snapshot")
            .put("onlineCount", presenceService.getOnlineUsers().size());

        ArrayNode users = payload.putArray("onlineUsers");
        presenceService.getOnlineUsers().forEach(users::add);

        actor.tell(new UserConnectionActor.Outgoing(payload), self());
    }

    private Set<Long> recipientsFor(String convId) {
        Set<Long> recipients = new HashSet<>();
        if (convId == null || convId.isBlank()) {
            return recipients;
        }

        switch (convId) {
            case "1" -> { recipients.add(1L); recipients.add(3L); }
            case "2" -> { recipients.add(1L); recipients.add(4L); }
            case "3" -> { recipients.add(1L); recipients.add(5L); }
            case "4" -> { recipients.add(1L); recipients.add(6L); }
            case "5" -> { recipients.add(1L); recipients.add(7L); }
            case "6" -> { recipients.add(1L); recipients.add(8L); }
            case "7" -> { recipients.add(1L); recipients.add(9L); }
            default -> {
                if (convId.startsWith("dm-")) {
                    String[] parts = convId.split("-");
                    if (parts.length == 3) {
                        try {
                            recipients.add(Long.parseLong(parts[1]));
                            recipients.add(Long.parseLong(parts[2]));
                        } catch (NumberFormatException ignored) {
                            recipients.clear();
                        }
                    }
                }
            }
        }
        return recipients;
    }
}
