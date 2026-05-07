package controllers;

import models.Account;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import repositories.AccountRepository;
import services.PresenceService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletionStage;

/**
 * Full-project reference implementation (Tuần 2+).
 * So sánh với your-project/be để học.
 */
@Singleton
public class AccountController extends Controller {

    private final AccountRepository accountRepo;
    private final PresenceService presenceService;

    @Inject
    public AccountController(AccountRepository accountRepo, PresenceService presenceService) {
        this.accountRepo = accountRepo;
        this.presenceService = presenceService;
    }

    public CompletionStage<Result> list() {
        return accountRepo.findAll()
            .thenApply(accounts -> ok(Json.toJson(accounts)))
            .exceptionally(t -> internalServerError(
                Json.newObject().put("error", t.getMessage())
            ));
    }

    public CompletionStage<Result> current() {
        return accountRepo.getCurrentAccountId()
            .thenCompose(accountRepo::findById)
            .thenApply(opt -> opt
                .map(acc -> ok(Json.toJson(acc)))
                .orElse(notFound(Json.newObject().put("error", "No current account")))
            );
    }

    public CompletionStage<Result> switchTo(Long id) {
        return accountRepo.findById(id)
            .thenCompose(opt -> {
                if (opt.isEmpty()) {
                    return java.util.concurrent.CompletableFuture.completedFuture(
                        notFound(Json.newObject().put("error", "Account not found: " + id))
                    );
                }
                return accountRepo.setCurrentAccountId(id)
                    .thenApply(v -> ok(Json.toJson(opt.get())));
            })
            .exceptionally(t -> internalServerError(
                Json.newObject().put("error", t.getMessage())
            ));
    }

    public Result presence() {
        return ok(Json.toJson(presenceService.getOnlineUsers()));
    }

    public Result health() {
        return ok(Json.newObject()
            .put("status", "UP")
            .put("service", "chat-backend-full")
            .put("timestamp", System.currentTimeMillis())
        );
    }
}
