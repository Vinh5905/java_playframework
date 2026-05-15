package controllers;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import repositories.ConversationRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletionStage;

@Singleton
public class ConversationController extends Controller {
    private final ConversationRepository conversationRepo;

    @Inject
    public ConversationController(ConversationRepository conversationRepo) {
        this.conversationRepo = conversationRepo;
    }

    public CompletionStage<Result> list(Long accountId) {
        return conversationRepo.findForAccount(accountId)
            .thenApply(conversations -> ok(Json.toJson(conversations)))
            .exceptionally(t -> internalServerError(Json.newObject().put("error", t.getMessage())));
    }
}
