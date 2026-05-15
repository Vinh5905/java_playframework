package actors;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import play.libs.Json;

public class GlobalMemberActor extends AbstractActor {
    private final Long accountId;
    private final ActorRef wsOut;
    private final ActorRef globalRoom;

    public static Props props(Long accountId, ActorRef wsOut, ActorRef globalRoom) {
        return Props.create(GlobalMemberActor.class,
            () -> new GlobalMemberActor(accountId, wsOut, globalRoom));
    }

    public GlobalMemberActor(Long accountId, ActorRef wsOut, ActorRef globalRoom) {
        this.accountId = accountId;
        this.wsOut = wsOut;
        this.globalRoom = globalRoom;
    }

    @Override
    public void preStart() {
        globalRoom.tell(new GlobalRoomActor.Join(accountId, self()), self());
    }

    @Override
    public void postStop() {
        globalRoom.tell(new GlobalRoomActor.Leave(accountId), self());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(UserConnectionActor.Outgoing.class, out -> wsOut.tell(out.payload, self()))
            .match(JsonNode.class, this::handleFromBrowser)
            .build();
    }

    private void handleFromBrowser(JsonNode msg) {
        String type = msg.path("type").asText();
        if ("message".equals(type)) {
            globalRoom.tell(
                new GlobalRoomActor.Broadcast(accountId, msg.path("text").asText()),
                self()
            );
        } else if ("ping".equals(type)) {
            wsOut.tell(Json.newObject().put("type", "pong"), self());
        }
    }
}
