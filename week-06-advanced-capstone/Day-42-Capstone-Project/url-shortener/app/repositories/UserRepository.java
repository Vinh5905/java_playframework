package repositories;

import models.User;
import org.apache.pekko.actor.ActorSystem;
import play.db.Database;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class UserRepository {

    private final Database db;
    private final ExecutionContext dbEc;

    @Inject
    public UserRepository(Database db, ActorSystem actorSystem) {
        this.db = db;
        this.dbEc = actorSystem.dispatchers().lookup("blocking-db-dispatcher");
    }

    public CompletionStage<Optional<User>> findByEmail(String email) {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                var ps = conn.prepareStatement(
                    "SELECT id, email, name, password_hash, created_at FROM users WHERE email = ?"
                );
                ps.setString(1, email);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.<User>empty();
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    public CompletionStage<User> create(String email, String name, String passwordHash) {
        return CompletableFuture.supplyAsync(
            () -> db.withTransaction(conn -> {
                var ps = conn.prepareStatement(
                    "INSERT INTO users (email, name, password_hash) VALUES (?, ?, ?) " +
                    "RETURNING id, email, name, password_hash, created_at"
                );
                ps.setString(1, email);
                ps.setString(2, name);
                ps.setString(3, passwordHash);
                ResultSet rs = ps.executeQuery();
                rs.next();
                return mapRow(rs);
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    private User mapRow(ResultSet rs) throws Exception {
        return new User(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("name"),
            rs.getString("password_hash"),
            rs.getTimestamp("created_at").toInstant().toString()
        );
    }
}
