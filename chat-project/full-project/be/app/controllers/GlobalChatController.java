package controllers;

import actors.GlobalMemberActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import play.libs.Json;
import play.libs.streams.ActorFlow;
import play.mvc.Controller;
import play.mvc.WebSocket;
import repositories.MessageRepository;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.CompletionStage;
import play.mvc.Result;

@Singleton
public class GlobalChatController extends Controller {
    private final ActorSystem actorSystem;
    private final Materializer materializer;
    private final ActorRef globalRoom;
    private final MessageRepository messageRepo;

    @Inject
    public GlobalChatController(ActorSystem actorSystem,
                                Materializer materializer,
                                @Named("global-room") ActorRef globalRoom,
                                MessageRepository messageRepo) {
        this.actorSystem = actorSystem;
        this.materializer = materializer;
        this.globalRoom = globalRoom;
        this.messageRepo = messageRepo;
    }

    public WebSocket join() {
        return WebSocket.Json.accept(request -> {
            Long accountId = parseLongOrDefault(request.getQueryString("accountId"), 1L);
            return ActorFlow.actorRef(
                wsOut -> GlobalMemberActor.props(accountId, wsOut, globalRoom),
                actorSystem,
                materializer
            );
        });
    }

    public CompletionStage<Result> history(int limit) {
        return messageRepo.getMessages("global", Math.min(Math.max(limit, 1), 100))
            .thenApply(messages -> ok(Json.toJson(messages)))
            .exceptionally(t -> internalServerError(Json.newObject().put("error", t.getMessage())));
    }

    private Long parseLongOrDefault(String value, Long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
