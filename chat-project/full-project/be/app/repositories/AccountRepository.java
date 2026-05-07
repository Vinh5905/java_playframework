package repositories;

import models.Account;
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
public class AccountRepository {

    private final Database db;
    private final ExecutionContext dbEc;

    @Inject
    public AccountRepository(Database db, ActorSystem system) {
        this.db = db;
        this.dbEc = system.dispatchers().lookup("blocking-db-dispatcher");
    }

    public CompletionStage<List<Account>> findAll() {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                List<Account> list = new ArrayList<>();
                try (var ps = conn.prepareStatement(
                    "SELECT id, name, username, is_bot FROM accounts ORDER BY id");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
                return list;
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    public CompletionStage<Optional<Account>> findById(Long id) {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                try (var ps = conn.prepareStatement(
                    "SELECT id, name, username, is_bot FROM accounts WHERE id = ?")) {
                    ps.setLong(1, id);
                    ResultSet rs = ps.executeQuery();
                    return rs.next() ? Optional.of(mapRow(rs)) : Optional.<Account>empty();
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    public CompletionStage<Long> getCurrentAccountId() {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                try (var ps = conn.prepareStatement(
                    "SELECT value FROM app_state WHERE key = 'current_account_id'");
                     ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Long.parseLong(rs.getString("value")) : 1L;
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    public CompletionStage<Void> setCurrentAccountId(Long accountId) {
        return CompletableFuture.runAsync(
            () -> db.withTransaction(conn -> {
                try (var ps = conn.prepareStatement(
                    "UPDATE app_state SET value = ? WHERE key = 'current_account_id'")) {
                    ps.setString(1, accountId.toString());
                    ps.executeUpdate();
                    return null;
                }
            }),
            (ExecutionContextExecutor) dbEc
        );
    }

    private Account mapRow(ResultSet rs) throws Exception {
        return new Account(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("username"),
            rs.getBoolean("is_bot")
        );
    }
}
