package controllers;

import actors.ChatRoomActor;
import actors.UserConnectionActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import play.libs.streams.ActorFlow;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.WebSocket;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class WebSocketController extends Controller {

    private final ActorSystem actorSystem;
    private final Materializer materializer;
    private final ActorRef chatRoom;

    @Inject
    public WebSocketController(ActorSystem sys, Materializer mat,
                                @Named("chat-room") ActorRef chatRoom) {
        this.actorSystem = sys;
        this.materializer = mat;
        this.chatRoom = chatRoom;
    }

    // GET /ws/chat?accountId=1
    public WebSocket chat(Http.Request request) {
        Long accountId = Long.parseLong(
            request.getQueryString("accountId").orElse("1")
        );

        return WebSocket.Json.accept(req ->
            ActorFlow.actorRef(
                wsOut -> UserConnectionActor.props(accountId, wsOut, chatRoom),
                actorSystem, materializer
            )
        );
    }
}
