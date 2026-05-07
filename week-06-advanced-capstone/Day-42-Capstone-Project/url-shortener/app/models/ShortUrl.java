package models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShortUrl {
    public Long id;
    public String code;

    @JsonProperty("original_url")
    public String originalUrl;

    @JsonProperty("short_url")
    public String shortUrl;

    @JsonProperty("user_id")
    public Long userId;

    @JsonProperty("click_count")
    public long clickCount;

    @JsonProperty("created_at")
    public String createdAt;

    @JsonProperty("last_accessed_at")
    public String lastAccessedAt;

    public ShortUrl() {}

    public ShortUrl(Long id, String code, String originalUrl, Long userId,
                    long clickCount, String createdAt, String lastAccessedAt) {
        this.id = id;
        this.code = code;
        this.originalUrl = originalUrl;
        this.userId = userId;
        this.clickCount = clickCount;
        this.createdAt = createdAt;
        this.lastAccessedAt = lastAccessedAt;
    }
}
