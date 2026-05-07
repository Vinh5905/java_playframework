package modules;

import actors.ChatRoomActor;
import actors.GlobalRoomActor;
import com.google.inject.AbstractModule;
import play.libs.pekko.PekkoGuiceSupport;
import repositories.MessageRepository;
import repositories.SettingsRepository;
import services.PresenceService;
import services.SettingsService;

public class AppModule extends AbstractModule implements PekkoGuiceSupport {

    @Override
    protected void configure() {
        // Singleton actors (1 instance toàn app)
        bindActor(ChatRoomActor.class, "chat-room");
        bindActor(GlobalRoomActor.class, "global-room");
    }
}
