package security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.typesafe.config.Config;
import play.libs.typedmap.TypedKey;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Singleton
public class JwtService {

    public static final TypedKey<Long> USER_ID_KEY = TypedKey.create("userId");
    public static final TypedKey<String> USER_EMAIL_KEY = TypedKey.create("userEmail");

    private final Algorithm algorithm;
    private final long expirationHours;

    @Inject
    public JwtService(Config config) {
        String secret = config.getString("jwt.secret");
        this.algorithm = Algorithm.HMAC256(secret);
        this.expirationHours = config.getLong("jwt.expirationHours");
    }

    public String generateToken(Long userId, String email) {
        return JWT.create()
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withIssuedAt(Date.from(Instant.now()))
            .withExpiresAt(Date.from(Instant.now().plus(expirationHours, ChronoUnit.HOURS)))
            .sign(algorithm);
    }

    public DecodedJWT verify(String token) throws JWTVerificationException {
        return JWT.require(algorithm).build().verify(token);
    }
}
