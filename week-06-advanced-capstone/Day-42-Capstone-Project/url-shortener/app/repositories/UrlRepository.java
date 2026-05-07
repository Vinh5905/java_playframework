package repositories;

import models.ShortUrl;
import org.apache.pekko.actor.ActorSystem;
import play.db.Database;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class UrlRepository {

    private final Database db;
    private final ExecutionContext dbEc;

    @Inject
    public UrlRepository(Database db, ActorSystem actorSystem) {
        this.db = db;
        this.dbEc = actorSystem.dispatchers().lookup("blocking-db-dispatcher");
    }

    public CompletionStage<Optional<ShortUrl>> findByCode(String code) {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                var ps = conn.prepareStatement(
                    "SELECT id, code, original_url, user_id, click_count, " +
                    "created_at, last_accessed_at FROM short_urls WHERE code = ?"
                );
                ps.setString(1, code);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.<ShortUrl>empty();
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    public CompletionStage<List<ShortUrl>> findByUserId(Long userId) {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                var ps = conn.prepareStatement(
                    "SELECT id, code, original_url, user_id, click_count, " +
                    "created_at, last_accessed_at FROM short_urls " +
                    "WHERE user_id = ? ORDER BY created_at DESC"
                );
                ps.setLong(1, userId);
                ResultSet rs = ps.executeQuery();
                List<ShortUrl> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    public CompletionStage<ShortUrl> create(String code, String originalUrl, Long userId) {
        return CompletableFuture.supplyAsync(
            () -> db.withTransaction(conn -> {
                var ps = conn.prepareStatement(
                    "INSERT INTO short_urls (code, original_url, user_id) VALUES (?, ?, ?) " +
                    "RETURNING id, code, original_url, user_id, click_count, created_at, last_accessed_at"
                );
                ps.setString(1, code);
                ps.setString(2, originalUrl);
                ps.setLong(3, userId);
                ResultSet rs = ps.executeQuery();
                rs.next();
                return mapRow(rs);
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    public CompletionStage<Void> incrementClickCount(String code) {
        return CompletableFuture.runAsync(
            () -> db.withTransaction(conn -> {
                var ps = conn.prepareStatement(
                    "UPDATE short_urls SET click_count = click_count + 1, " +
                    "last_accessed_at = CURRENT_TIMESTAMP WHERE code = ?"
                );
                ps.setString(1, code);
                ps.executeUpdate();
                return null;
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    public CompletionStage<Boolean> deleteByCodeAndUserId(String code, Long userId) {
        return CompletableFuture.supplyAsync(
            () -> db.withTransaction(conn -> {
                var ps = conn.prepareStatement(
                    "DELETE FROM short_urls WHERE code = ? AND user_id = ?"
                );
                ps.setString(1, code);
                ps.setLong(2, userId);
                return ps.executeUpdate() > 0;
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    private ShortUrl mapRow(ResultSet rs) throws Exception {
        var lastAccessed = rs.getTimestamp("last_accessed_at");
        return new ShortUrl(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("original_url"),
            rs.getLong("user_id"),
            rs.getLong("click_count"),
            rs.getTimestamp("created_at").toInstant().toString(),
            lastAccessed != null ? lastAccessed.toInstant().toString() : null
        );
    }
}
