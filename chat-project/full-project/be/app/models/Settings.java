package models;

public class Settings {
    public Long userId;
    public boolean typingIndicators = true;
    public boolean showOnlineStatus = true;
    public boolean notifications = true;
    public boolean soundEnabled = true;

    public Settings() {}
    public Settings(Long userId) { this.userId = userId; }
}
