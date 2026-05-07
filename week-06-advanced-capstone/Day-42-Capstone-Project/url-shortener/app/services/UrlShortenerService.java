package services;

import models.ShortUrl;
import repositories.UrlRepository;
import com.typesafe.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;

@Singleton
public class UrlShortenerService {

    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final UrlRepository urlRepository;
    private final String baseUrl;
    private final int codeLength;

    @Inject
    public UrlShortenerService(UrlRepository urlRepository, Config config) {
        this.urlRepository = urlRepository;
        this.baseUrl = config.getString("urlshortener.baseUrl");
        this.codeLength = config.getInt("urlshortener.codeLength");
    }

    public CompletionStage<ShortUrl> shorten(String originalUrl, Long userId) {
        if (!isValidUrl(originalUrl)) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Invalid URL: " + originalUrl)
            );
        }

        String code = generateCode();
        return urlRepository.create(code, originalUrl, userId)
            .thenApply(url -> {
                url.shortUrl = baseUrl + "/" + url.code;
                return url;
            });
    }

    public CompletionStage<Optional<String>> resolve(String code) {
        return urlRepository.findByCode(code)
            .thenCompose(opt -> {
                if (opt.isEmpty()) {
                    return CompletableFuture.completedFuture(Optional.<String>empty());
                }
                String originalUrl = opt.get().originalUrl;
                // Fire-and-forget: increment click count async
                urlRepository.incrementClickCount(code);
                return CompletableFuture.completedFuture(Optional.of(originalUrl));
            });
    }

    public CompletionStage<List<ShortUrl>> listForUser(Long userId) {
        return urlRepository.findByUserId(userId)
            .thenApply(urls -> {
                urls.forEach(url -> url.shortUrl = baseUrl + "/" + url.code);
                return urls;
            });
    }

    public CompletionStage<Optional<ShortUrl>> getStats(String code, Long userId) {
        return urlRepository.findByCode(code)
            .thenApply(opt -> opt.filter(url -> url.userId.equals(userId)));
    }

    public CompletionStage<Boolean> delete(String code, Long userId) {
        return urlRepository.deleteByCodeAndUserId(code, userId);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            sb.append(CHARS.charAt(ThreadLocalRandom.current().nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
