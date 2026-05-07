package actors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import play.libs.Json;
import repositories.MessageRepository;

import java.util.HashMap;
import java.util.Map;

public class GlobalRoomActor extends AbstractActor {

    public static class Join      { public final Long userId; public final ActorRef actor;
                                    public Join(Long u, ActorRef a){ userId=u; actor=a; } }
    public static class Leave     { public final Long userId; public Leave(Long u){ userId=u; } }
    public static class Broadcast { public final Long senderId; public final String text;
                                    public Broadcast(Long s, String t){ senderId=s; text=t; } }

    private final Map<Long, ActorRef> members = new HashMap<>();
    private final MessageRepository messageRepo;

    public static Props props(MessageRepository r) {
        return Props.create(GlobalRoomActor.class, () -> new GlobalRoomActor(r));
    }

    public GlobalRoomActor(MessageRepository r) { this.messageRepo = r; }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Join.class, j -> members.put(j.userId, j.actor))
            .match(Leave.class, l -> members.remove(l.userId))
            .match(Broadcast.class, b -> {
                var msg = new models.Message("global", b.senderId, b.text);
                messageRepo.saveMessage(msg).thenAccept(saved -> {
                    ObjectNode payload = Json.newObject()
                        .put("type", "global_message");
                    payload.set("message", Json.toJson(saved));

                    members.forEach((uid, actor) -> {
                        if (!uid.equals(b.senderId)) actor.tell(payload, self());
                    });
                });
            })
            .build();
    }
}
