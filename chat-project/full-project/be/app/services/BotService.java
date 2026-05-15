package services;

import actors.ChatRoomActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import models.Message;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import play.libs.Json;
import play.libs.ws.WSClient;
import repositories.MessageRepository;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;

@Singleton
public class BotService {
    public static final Long BOT_ACCOUNT_ID = 9L;

    private final ActorSystem actorSystem;
    private final MessageRepository messageRepo;
    private final ActorRef chatRoom;
    private final WSClient ws;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    @Inject
    public BotService(ActorSystem actorSystem,
                      MessageRepository messageRepo,
                      @Named("chat-room") ActorRef chatRoom,
                      WSClient ws,
                      Config config) {
        this.actorSystem = actorSystem;
        this.messageRepo = messageRepo;
        this.chatRoom = chatRoom;
        this.ws = ws;
        this.apiKey = config.getString("openai.apiKey");
        this.model = config.getString("openai.model");
        this.baseUrl = config.getString("openai.baseUrl");
    }

    public void streamResponse(String userMessage, String convId, Long userId) {
        if (apiKey != null && !apiKey.isBlank() && !"changeme".equals(apiKey)) {
            streamOpenAiResponse(userMessage, convId, userId);
            return;
        }
        streamText(buildResponse(userMessage), convId, userId);
    }

    private void streamOpenAiResponse(String userMessage, String convId, Long userId) {
        ObjectNode body = Json.newObject();
        body.put("model", model);
        body.put("max_tokens", 500);
        body.put("stream", false);

        ObjectNode systemMessage = Json.newObject()
            .put("role", "system")
            .put("content", "You are a helpful assistant in a Play Framework learning chat app. Keep answers concise and practical.");
        ObjectNode user = Json.newObject()
            .put("role", "user")
            .put("content", userMessage);
        body.set("messages", Json.newArray().add(systemMessage).add(user));

        ws.url(baseUrl + "/chat/completions")
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .thenAccept(response -> {
                if (response.getStatus() < 200 || response.getStatus() >= 300) {
                    streamText("OpenAI returned HTTP " + response.getStatus() + ". Check your API key and model config.", convId, userId);
                    return;
                }

                try {
                    JsonNode json = response.asJson();
                    String content = json.path("choices").path(0).path("message").path("content").asText();
                    streamText(content.isBlank() ? buildResponse(userMessage) : content, convId, userId);
                } catch (Exception e) {
                    streamText(buildResponse(userMessage), convId, userId);
                }
            })
            .exceptionally(t -> {
                streamText("I could not reach OpenAI right now, so I am using the local learning fallback. " + buildResponse(userMessage), convId, userId);
                return null;
            });
    }

    private void streamText(String response, String convId, Long userId) {
        String[] chunks = response.split("(?<=\\s)");
        StringBuilder full = new StringBuilder();

        for (int i = 0; i < chunks.length; i++) {
            String chunk = chunks[i];
            int index = i;
            actorSystem.scheduler().scheduleOnce(
                Duration.ofMillis(120L * (i + 1)),
                () -> {
                    full.append(chunk);
                    chatRoom.tell(new ChatRoomActor.BotChunk(convId, userId, chunk, false), ActorRef.noSender());

                    if (index == chunks.length - 1) {
                        Message botMessage = new Message(convId, BOT_ACCOUNT_ID, full.toString().trim());
                        messageRepo.saveMessage(botMessage);
                        chatRoom.tell(new ChatRoomActor.BotChunk(convId, userId, "", true), ActorRef.noSender());
                    }
                },
                actorSystem.dispatcher()
            );
        }
    }

    private String buildResponse(String userMessage) {
        String text = userMessage == null ? "" : userMessage.toLowerCase();
        if (text.contains("play") || text.contains("framework")) {
            return "Play Framework fits this chat app well because controllers stay thin, I/O can return CompletionStage, and WebSocket work maps cleanly to Pekko actors.";
        }
        if (text.contains("async") || text.contains("completionstage")) {
            return "Use CompletionStage when an action waits for database, HTTP, or stream work. It lets Play free the request thread while the operation finishes.";
        }
        if (text.contains("websocket")) {
            return "For WebSocket, keep one actor per connection and one room actor for routing. The connection actor translates browser JSON into room messages.";
        }
        return "I can help with the Play chat project. Ask about routes, controllers, JDBC, MongoDB, WebSocket actors, settings, or deployment.";
    }
}
