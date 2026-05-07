package filters;

import org.apache.pekko.stream.Materializer;
import play.libs.Json;
import play.mvc.EssentialAction;
import play.mvc.EssentialFilter;
import play.mvc.Results;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class RateLimitFilter extends EssentialFilter {

    private static final int MAX_PER_MINUTE = 60;
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final Materializer mat;

    @Inject
    public RateLimitFilter(Materializer mat) {
        this.mat = mat;
        Executors.newScheduledThreadPool(1)
            .scheduleAtFixedRate(counts::clear, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public EssentialAction apply(EssentialAction next) {
        return EssentialAction.of(request -> {
            String ip = request.remoteAddress();
            int count = counts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();

            if (count > MAX_PER_MINUTE) {
                return org.apache.pekko.stream.javadsl.Source.single(
                    Results.status(429,
                        Json.newObject().put("error", "Rate limit exceeded. Max 60 requests/minute")
                    ).withHeader("Retry-After", "60")
                );
            }

            return next.apply(request).map(
                result -> result.withHeader("X-RateLimit-Remaining",
                    String.valueOf(MAX_PER_MINUTE - count)),
                mat.executionContext()
            );
        });
    }
}
