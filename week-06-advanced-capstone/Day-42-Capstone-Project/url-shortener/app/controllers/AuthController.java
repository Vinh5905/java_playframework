package controllers;

import models.User;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import repositories.UserRepository;
import security.JwtService;
import org.mindrot.jbcrypt.BCrypt;

import javax.inject.Inject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthController extends Controller {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Inject
    public AuthController(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    // POST /auth/register
    public CompletionStage<Result> register(Http.Request request) {
        JsonNode body = request.body().asJson();
        if (body == null) {
            return ok400("JSON body required");
        }

        String email = body.path("email").asText("").trim().toLowerCase();
        String name = body.path("name").asText("").trim();
        String password = body.path("password").asText("");

        if (email.isEmpty() || !email.contains("@")) {
            return ok400("Valid email required");
        }
        if (name.isEmpty()) {
            return ok400("Name required");
        }
        if (password.length() < 6) {
            return ok400("Password must be at least 6 characters");
        }

        return userRepository.findByEmail(email)
            .thenCompose(existing -> {
                if (existing.isPresent()) {
                    return ok409("Email already registered");
                }

                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
                return userRepository.create(email, name, hashedPassword)
                    .thenApply(user -> {
                        String token = jwtService.generateToken(user.id, user.email);
                        ObjectNode response = Json.newObject();
                        response.put("token", token);
                        response.set("user", Json.toJson(user));
                        return created(response);
                    });
            })
            .exceptionally(t -> {
                log.error("Register error", t);
                return internalServerError(errorJson("Registration failed"));
            });
    }

    // POST /auth/login
    public CompletionStage<Result> login(Http.Request request) {
        JsonNode body = request.body().asJson();
        if (body == null) {
            return ok400("JSON body required");
        }

        String email = body.path("email").asText("").trim().toLowerCase();
        String password = body.path("password").asText("");

        if (email.isEmpty() || password.isEmpty()) {
            return ok400("Email and password required");
        }

        return userRepository.findByEmail(email)
            .thenApply(opt -> {
                if (opt.isEmpty()) {
                    return unauthorized(errorJson("Invalid credentials"));
                }

                User user = opt.get();
                if (!BCrypt.checkpw(password, user.passwordHash)) {
                    return unauthorized(errorJson("Invalid credentials"));
                }

                String token = jwtService.generateToken(user.id, user.email);
                ObjectNode response = Json.newObject();
                response.put("token", token);
                response.set("user", Json.toJson(user));
                return ok(response);
            })
            .exceptionally(t -> {
                log.error("Login error", t);
                return internalServerError(errorJson("Login failed"));
            });
    }

    private CompletionStage<Result> ok400(String message) {
        return CompletableFuture.completedFuture(badRequest(errorJson(message)));
    }

    private CompletionStage<Result> ok409(String message) {
        return CompletableFuture.completedFuture(status(409, errorJson(message)));
    }

    private ObjectNode errorJson(String message) {
        return Json.newObject().put("error", message);
    }
}
