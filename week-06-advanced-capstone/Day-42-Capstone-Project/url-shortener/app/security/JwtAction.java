package security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import play.libs.Json;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class JwtAction extends Action.Simple {

    @Inject
    private JwtService jwtService;

    @Override
    public CompletionStage<Result> call(Http.Request request) {
        return request.header("Authorization")
            .filter(h -> h.startsWith("Bearer "))
            .map(h -> h.substring(7))
            .map(token -> {
                try {
                    DecodedJWT decoded = jwtService.verify(token);
                    Long userId = Long.parseLong(decoded.getSubject());
                    String email = decoded.getClaim("email").asString();

                    Http.Request enriched = request
                        .addAttr(JwtService.USER_ID_KEY, userId)
                        .addAttr(JwtService.USER_EMAIL_KEY, email);

                    return delegate.call(enriched);
                } catch (JWTVerificationException e) {
                    return CompletableFuture.completedFuture(
                        Results.unauthorized(
                            Json.newObject().put("error", "Invalid or expired token")
                        )
                    );
                }
            })
            .orElseGet(() ->
                CompletableFuture.completedFuture(
                    Results.unauthorized(
                        Json.newObject().put("error", "Authorization header required")
                    )
                )
            );
    }
}
