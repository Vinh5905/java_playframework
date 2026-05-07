package actors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import play.libs.Json;
import repositories.MessageRepository;
import services.BotService;
import services.PresenceService;
import services.SettingsService;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

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

    // ── State ────────────────────────────────────────────────
    private final Map<Long, ActorRef> connectedUsers = new HashMap<>();
    private final MessageRepository messageRepo;
    private final PresenceService presenceService;
    private final SettingsService settingsService;

    // BotService injected lazily to avoid circular dependency
    private BotService botService;

    public static Props props(MessageRepository mr, PresenceService ps, SettingsService ss) {
        return Props.create(ChatRoomActor.class, () -> new ChatRoomActor(mr, ps, ss));
    }

    public ChatRoomActor(MessageRepository mr, PresenceService ps, SettingsService ss) {
        this.messageRepo   = mr;
        this.presenceService = ps;
        this.settingsService  = ss;
    }

    public void setBotService(BotService bs) { this.botService = bs; }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(UserConnected.class, msg -> {
                connectedUsers.put(msg.accountId, msg.actor);
                presenceService.markOnline(msg.accountId);
                broadcastPresence(msg.accountId, "online");
            })
            .match(UserDisconnected.class, msg -> {
                connectedUsers.remove(msg.accountId);
                presenceService.markOffline(msg.accountId);
                broadcastPresence(msg.accountId, "offline");
            })
            .match(SendMessage.class, this::handleSendMessage)
            .match(TypingEvent.class, this::handleTyping)
            .match(BotChunk.class, this::handleBotChunk)
            .build();
    }

    private void handleSendMessage(SendMessage cmd) {
        boolean isWithBot = cmd.convId.contains("bot") || cmd.convId.endsWith("-9");

        if (isWithBot && botService != null) {
            botService.streamResponse(cmd.text, cmd.convId, cmd.senderId);
            return;
        }

        var msg = new models.Message(cmd.convId, cmd.senderId, cmd.text);
        messageRepo.saveMessage(msg).thenAccept(saved -> {
            ObjectNode payload = Json.newObject()
                .put("type", "message")
                .put("convId", saved.conversationId);
            payload.set("message", Json.toJson(saved));

            connectedUsers.forEach((id, actor) -> actor.tell(payload, self()));
        });
    }

    private void handleTyping(TypingEvent event) {
        settingsService.getSettings(event.userId).thenAccept(settings -> {
            if (!settings.typingIndicators) return;

            ObjectNode payload = Json.newObject()
                .put("type", "typing")
                .put("convId", event.convId)
                .put("userId", event.userId)
                .put("isTyping", event.isTyping);

            connectedUsers.forEach((id, actor) -> {
                if (!id.equals(event.userId)) actor.tell(payload, self());
            });
        });
    }

    private void handleBotChunk(BotChunk bc) {
        ActorRef target = connectedUsers.get(bc.targetUserId);
        if (target == null) return;

        ObjectNode payload = Json.newObject()
            .put("type", "bot_chunk")
            .put("convId", bc.convId)
            .put("chunk", bc.chunk)
            .put("isDone", bc.isDone);

        target.tell(payload, self());
    }

    private void broadcastPresence(Long userId, String status) {
        ObjectNode payload = Json.newObject()
            .put("type", "presence")
            .put("userId", userId)
            .put("status", status);

        connectedUsers.forEach((id, actor) -> {
            if (!id.equals(userId)) actor.tell(payload, self());
        });
    }
}
