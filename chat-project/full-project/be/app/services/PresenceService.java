package services;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class PresenceService {

    private final Set<Long> onlineUsers = ConcurrentHashMap.newKeySet();
    private final Map<Long, Instant> lastSeen = new ConcurrentHashMap<>();

    public void markOnline(Long userId) {
        onlineUsers.add(userId);
        lastSeen.put(userId, Instant.now());
    }

    public void markOffline(Long userId) {
        onlineUsers.remove(userId);
        lastSeen.put(userId, Instant.now());
    }

    public boolean isOnline(Long userId) {
        return onlineUsers.contains(userId);
    }

    public Set<Long> getOnlineUsers() {
        return Collections.unmodifiableSet(onlineUsers);
    }

    public Instant getLastSeen(Long userId) {
        return lastSeen.getOrDefault(userId, Instant.EPOCH);
    }
}
