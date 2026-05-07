package models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Settings {
    @JsonProperty("user_id")       public Long userId;
    @JsonProperty("typing_indicators") public boolean typingIndicators = true;
    @JsonProperty("show_online_status") public boolean showOnlineStatus = true;
    @JsonProperty("notifications") public boolean notifications = true;
    @JsonProperty("sound_enabled") public boolean soundEnabled = true;

    public Settings() {}
    public Settings(Long userId) { this.userId = userId; }
}
