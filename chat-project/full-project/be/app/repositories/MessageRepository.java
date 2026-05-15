package repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;
import models.Message;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import services.SeedData;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MessageRepository {
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("hh:mm a").withZone(ZoneId.systemDefault());

    private final MongoCollection<Document> messages;
    private final Materializer mat;
    private final boolean externalStorage;
    private final Map<String, List<Message>> memoryMessages = new ConcurrentHashMap<>();

    @Inject
    public MessageRepository(Config config, Materializer mat) {
        this.mat = mat;
        this.externalStorage = config.hasPath("chat.persistence.external")
            && config.getBoolean("chat.persistence.external");

        if (externalStorage) {
            String uri = withServerSelectionTimeout(config.getString("mongodb.uri"));
            MongoClient client = MongoClients.create(uri);
            this.messages = client.getDatabase("chatapp").getCollection("messages");
        } else {
            this.messages = null;
        }
    }

    public CompletionStage<List<Message>> getMessages(String convId, int limit) {
        String canonicalConvId = canonicalizeConversationId(convId);
        if (!externalStorage) {
            return CompletableFuture.completedFuture(memoryMessages(canonicalConvId, limit));
        }

        var publisher = messages
            .find(Filters.in("conversationId", conversationIds(canonicalConvId)))
            .sort(Sorts.descending("timestamp"))
            .limit(limit);

        return Source.fromPublisher(publisher)
            .runWith(Sink.seq(), mat)
            .thenApply(docs -> {
                List<Message> result = new ArrayList<>();
                for (Document doc : docs) {
                    result.add(docToMessage(doc));
                }
                Collections.reverse(result);
                return result.isEmpty() ? memoryMessages(canonicalConvId, limit) : result;
            })
            .exceptionally(t -> memoryMessages(canonicalConvId, limit))
            .toCompletableFuture();
    }

    public CompletionStage<Message> saveMessage(Message msg) {
        msg.conversationId = canonicalizeConversationId(msg.conversationId);
        if (!externalStorage) {
            return CompletableFuture.completedFuture(saveMemory(msg));
        }

        Document doc = new Document()
            .append("conversationId", msg.conversationId)
            .append("senderId", msg.senderId)
            .append("text", msg.text)
            .append("timestamp", msg.timestamp);

        CompletableFuture<Message> future = new CompletableFuture<>();
        messages.insertOne(doc).subscribe(new Subscriber<>() {
            @Override public void onSubscribe(Subscription s) { s.request(1); }
            @Override public void onNext(InsertOneResult result) {
                msg.id = result.getInsertedId().asObjectId().getValue().toHexString();
                future.complete(msg);
            }
            @Override public void onError(Throwable t) { future.complete(saveMemory(msg)); }
            @Override public void onComplete() {}
        });
        return future;
    }

    private Message saveMemory(Message msg) {
        if (msg.id == null || msg.id.isBlank()) {
            msg.id = "mem-" + System.nanoTime();
        }
        if (msg.timestamp == null) {
            msg.timestamp = Instant.now();
        }
        if (msg.time == null || msg.time.isBlank()) {
            msg.time = TIME_FORMAT.format(msg.timestamp);
        }
        memoryMessages.computeIfAbsent(msg.conversationId, SeedData::messages).add(msg);
        return msg;
    }

    private List<Message> memoryMessages(String convId, int limit) {
        List<Message> source = memoryMessages.computeIfAbsent(convId, SeedData::messages);
        List<Message> combined = new ArrayList<>(source);
        for (String alias : conversationIds(convId)) {
            if (!alias.equals(convId) && memoryMessages.containsKey(alias)) {
                combined.addAll(memoryMessages.get(alias));
            }
        }
        combined.sort(java.util.Comparator.comparing(m -> m.timestamp));
        int from = Math.max(0, combined.size() - Math.max(1, limit));
        return new ArrayList<>(combined.subList(from, combined.size()));
    }

    private Message docToMessage(Document doc) {
        Message msg = new Message();
        msg.id = doc.getObjectId("_id").toHexString();
        msg.conversationId = doc.getString("conversationId");
        msg.senderId = doc.getLong("senderId");
        msg.text = doc.getString("text");
        Object timestamp = doc.get("timestamp");
        if (timestamp instanceof java.util.Date date) {
            msg.timestamp = date.toInstant();
        } else if (timestamp instanceof Instant instant) {
            msg.timestamp = instant;
        } else {
            msg.timestamp = Instant.now();
        }
        msg.time = TIME_FORMAT.format(msg.timestamp);
        return msg;
    }

    private String withServerSelectionTimeout(String uri) {
        if (uri.contains("serverSelectionTimeoutMS=")) {
            return uri;
        }
        return uri + (uri.contains("?") ? "&" : "?") + "serverSelectionTimeoutMS=1000";
    }

    private List<String> conversationIds(String convId) {
        List<String> ids = new ArrayList<>();
        ids.add(convId);
        switch (convId) {
            case "1" -> ids.add("dm-1-3");
            case "2" -> ids.add("dm-1-4");
            case "3" -> ids.add("dm-1-5");
            case "4" -> ids.add("dm-1-6");
            case "5" -> ids.add("dm-1-7");
            case "6" -> ids.add("dm-1-8");
            case "7" -> ids.add("dm-1-9");
            default -> {
            }
        }
        return ids;
    }

    private String canonicalizeConversationId(String convId) {
        if (convId == null || !convId.startsWith("dm-")) {
            return convId;
        }

        String[] parts = convId.split("-");
        if (parts.length != 3) {
            return convId;
        }

        try {
            long first = Long.parseLong(parts[1]);
            long second = Long.parseLong(parts[2]);
            long min = Math.min(first, second);
            long max = Math.max(first, second);
            if (min == 1L && max >= 3L && max <= 9L) {
                return String.valueOf(max - 2L);
            }
        } catch (NumberFormatException ignored) {
            return convId;
        }
        return convId;
    }
}
