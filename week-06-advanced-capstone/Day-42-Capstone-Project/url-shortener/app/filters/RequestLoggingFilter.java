package filters;

import org.apache.pekko.stream.Materializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.EssentialAction;
import play.mvc.EssentialFilter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.UUID;

@Singleton
public class RequestLoggingFilter extends EssentialFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final Materializer mat;

    @Inject
    public RequestLoggingFilter(Materializer mat) {
        this.mat = mat;
    }

    @Override
    public EssentialAction apply(EssentialAction next) {
        return EssentialAction.of(request -> {
            long start = System.currentTimeMillis();
            String requestId = UUID.randomUUID().toString().substring(0, 8);

            log.info("→ [{}] {} {}", requestId, request.method(), request.uri());

            return next.apply(request).map(result -> {
                long ms = System.currentTimeMillis() - start;
                log.info("← [{}] {} {} {} {}ms",
                    requestId, request.method(), request.uri(), result.status(), ms);
                return result
                    .withHeader("X-Request-Id", requestId)
                    .withHeader("X-Response-Time", ms + "ms");
            }, mat.executionContext());
        });
    }
}
