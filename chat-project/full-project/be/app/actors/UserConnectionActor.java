package actors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import play.libs.Json;

/**
 * 1 instance per WebSocket connection.
 * Lifecycle tự động: tạo khi connect, destroy khi disconnect.
 */
public class UserConnectionActor extends AbstractActor {

    private final Long accountId;
    private final ActorRef wsOut;
    private final ActorRef chatRegistry;
    private long lastPingTime = System.currentTimeMillis();

    public static Props props(Long accountId, ActorRef wsOut, ActorRef chatRegistry) {
        return Props.create(UserConnectionActor.class,
            () -> new UserConnectionActor(accountId, wsOut, chatRegistry));
    }

    public UserConnectionActor(Long accountId, ActorRef wsOut, ActorRef chatRegistry) {
        this.accountId = accountId;
        this.wsOut = wsOut;
        this.chatRegistry = chatRegistry;
    }

    @Override
    public void preStart() {
        chatRegistry.tell(new ChatRoomActor.UserConnected(accountId, self()), self());

        // Schedule heartbeat check mỗi 30s
        context().system().scheduler().scheduleWithFixedDelay(
            java.time.Duration.ofSeconds(30),
            java.time.Duration.ofSeconds(30),
            self(), "check_heartbeat",
            context().dispatcher(), self()
        );
    }

    @Override
    public void postStop() {
        chatRegistry.tell(new ChatRoomActor.UserDisconnected(accountId), self());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(JsonNode.class, this::handleFromBrowser)
            .match(ObjectNode.class, msg -> wsOut.tell(msg.toString(), self()))
            .match(String.class, cmd -> {
                if ("check_heartbeat".equals(cmd)) {
                    long elapsed = System.currentTimeMillis() - lastPingTime;
                    if (elapsed > 60_000) context().stop(self());
                }
            })
            .build();
    }

    private void handleFromBrowser(JsonNode msg) {
        String type = msg.path("type").asText();
        switch (type) {
            case "message":
                chatRegistry.tell(new ChatRoomActor.SendMessage(
                    msg.path("convId").asText(),
                    accountId,
                    msg.path("text").asText()
                ), self());
                break;
            case "typing":
                chatRegistry.tell(new ChatRoomActor.TypingEvent(
                    msg.path("convId").asText(),
                    accountId,
                    msg.path("isTyping").asBoolean()
                ), self());
                break;
            case "ping":
                lastPingTime = System.currentTimeMillis();
                wsOut.tell(Json.newObject().put("type", "pong").toString(), self());
                break;
        }
    }
}
