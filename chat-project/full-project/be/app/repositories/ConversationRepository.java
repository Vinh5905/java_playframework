package repositories;

import models.Conversation;
import org.apache.pekko.actor.ActorSystem;
import play.db.Database;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;
import services.SeedData;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class ConversationRepository {
    private final Database db;
    private final ExecutionContext dbEc;

    @Inject
    public ConversationRepository(Database db, ActorSystem system) {
        this.db = db;
        this.dbEc = system.dispatchers().lookup("blocking-db-dispatcher");
    }

    public CompletionStage<List<Conversation>> findForAccount(Long accountId) {
        return CompletableFuture.supplyAsync(
            () -> db.withConnection(conn -> {
                List<Conversation> conversations = new ArrayList<>();
                String sql = """
                    SELECT id,
                           CASE WHEN participant1 = ? THEN participant2 ELSE participant1 END AS participant_id
                    FROM conversations
                    WHERE participant1 = ? OR participant2 = ?
                    ORDER BY id
                    """;
                try (var ps = conn.prepareStatement(sql)) {
                    ps.setLong(1, accountId);
                    ps.setLong(2, accountId);
                    ps.setLong(3, accountId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        conversations.add(enrich(
                            String.valueOf(rs.getLong("id")),
                            rs.getLong("participant_id")
                        ));
                    }
                }
                return conversations.isEmpty() ? SeedData.conversationsFor(accountId) : conversations;
            }),
            (ExecutionContextExecutor) dbEc
        ).exceptionally(t -> SeedData.conversationsFor(accountId));
    }

    private Conversation enrich(String id, Long participantId) {
        return SeedData.aliceConversations().stream()
            .filter(c -> c.id.equals(id))
            .findFirst()
            .map(c -> new Conversation(
                c.id,
                participantId,
                c.lastMessage,
                c.lastTime,
                0,
                c.tags
            ))
            .orElseGet(() -> new Conversation(
                id,
                participantId,
                "Start a conversation",
                "Now",
                0,
                List.of(new Conversation.Tag("Direct", "gray"))
            ));
    }
}
