package actors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import play.libs.Json;
import repositories.MessageRepository;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GlobalRoomActor extends AbstractActor {

    public static class Join      { public final Long userId; public final ActorRef actor;
                                    public Join(Long u, ActorRef a){ userId=u; actor=a; } }
    public static class Leave     { public final Long userId; public Leave(Long u){ userId=u; } }
    public static class Broadcast { public final Long senderId; public final String text;
                                    public Broadcast(Long s, String t){ senderId=s; text=t; } }
    public static class BroadcastSaved {
        public final ActorRef senderActor;
        public final models.Message message;
        public BroadcastSaved(ActorRef senderActor, models.Message message) {
            this.senderActor = senderActor;
            this.message = message;
        }
    }

    private final Map<Long, Set<ActorRef>> members = new HashMap<>();
    private final MessageRepository messageRepo;

    public static Props props(MessageRepository r) {
        return Props.create(GlobalRoomActor.class, () -> new GlobalRoomActor(r));
    }

    @Inject
    public GlobalRoomActor(MessageRepository r) { this.messageRepo = r; }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(Join.class, j -> members.computeIfAbsent(j.userId, id -> new HashSet<>()).add(j.actor))
            .match(Leave.class, l -> {
                Set<ActorRef> actors = members.get(l.userId);
                if (actors != null) {
                    actors.remove(sender());
                    if (actors.isEmpty()) members.remove(l.userId);
                }
            })
            .match(Broadcast.class, b -> {
                var msg = new models.Message("global", b.senderId, b.text);
                ActorRef senderActor = sender();
                messageRepo.saveMessage(msg).thenAccept(saved -> {
                    self().tell(new BroadcastSaved(senderActor, saved), self());
                });
            })
            .match(BroadcastSaved.class, this::handleBroadcastSaved)
            .build();
    }

    private void handleBroadcastSaved(BroadcastSaved event) {
        ObjectNode payload = Json.newObject()
            .put("type", "global_message");
        payload.set("message", Json.toJson(event.message));

        members.forEach((uid, actors) -> actors.forEach(actor -> {
            if (!actor.equals(event.senderActor)) {
                actor.tell(new UserConnectionActor.Outgoing(payload), self());
            }
        }));
    }
}
