package controllers;

import org.apache.pekko.actor.ActorSystem;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Day 12 - Benchmark Controller
 *
 * Mỗi endpoint giả lập I/O mất SIMULATED_IO_MS milliseconds.
 * Dùng Thread.sleep() để simulate blocking I/O (JDBC, file read, v.v.)
 *
 * Benchmark: ab -n 500 -c 50 http://localhost:9000/bench/<endpoint>
 */
@Singleton
public class BenchmarkController extends Controller {

    // Giả lập I/O mất 500ms (ví dụ: DB query)
    private static final int SIMULATED_IO_MS = 500;

    private final ExecutionContext blockingEc;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Inject
    public BenchmarkController(ActorSystem actorSystem) {
        this.blockingEc = actorSystem.dispatchers().lookup("blocking-io-dispatcher");
    }

    /**
     * Endpoint 1: SYNC - Blocking trực tiếp trên default EC thread
     *
     * ❌ VẤN ĐỀ: Thread bị block SUỐT 500ms
     * Với default EC 8 threads → max 8 * (1000/500) = 16 req/s
     *
     * Chạy: ab -n 200 -c 50 http://localhost:9000/bench/sync
     */
    public Result sync() {
        String thread = Thread.currentThread().getName();
        simulateBlockingIO();  // Block thread 500ms!
        return ok(buildResult("sync", thread));
    }

    /**
     * Endpoint 2: ASYNC WRONG - Async nhưng không specify executor
     *
     * ❌ VẪN SAI: supplyAsync() không có executor → dùng ForkJoinPool.commonPool()
     * ForkJoinPool chia sẻ với cả JVM, dễ bị contention
     * Vẫn tốt hơn sync một chút vì không block default EC
     * Nhưng không tốt bằng custom EC
     *
     * Chạy: ab -n 200 -c 50 http://localhost:9000/bench/async-wrong
     */
    public CompletionStage<Result> asyncWrong() {
        String callerThread = Thread.currentThread().getName();
        return CompletableFuture.supplyAsync(() -> {
            // Chạy trên ForkJoinPool.commonPool() - KHÔNG phải custom EC
            String workerThread = Thread.currentThread().getName();
            simulateBlockingIO();
            return buildResult("async-wrong", callerThread + " → " + workerThread);
        }).thenApply(Results::ok);
    }

    /**
     * Endpoint 3: ASYNC RIGHT - Async với custom blocking dispatcher
     *
     * ✅ ĐÚNG: Blocking code chạy trên blockingEc (50 threads)
     * Default EC được giải phóng ngay → nhận request tiếp theo
     * 50 threads * (1000/500) = ~100 req/s
     *
     * Chạy: ab -n 200 -c 50 http://localhost:9000/bench/async-right
     */
    public CompletionStage<Result> asyncRight() {
        String callerThread = Thread.currentThread().getName();
        return CompletableFuture.supplyAsync(() -> {
            // Chạy trên blockingEc - được phép block!
            String workerThread = Thread.currentThread().getName();
            simulateBlockingIO();
            return buildResult("async-right", callerThread + " → " + workerThread);
        }, (ExecutionContextExecutor) blockingEc)  // ← Chỉ định executor!
        .thenApply(Results::ok);
    }

    /**
     * Endpoint 4: NON-BLOCKING - Không block thread nào cả
     *
     * ✅✅ TỐT NHẤT: Dùng async scheduler thay vì Thread.sleep
     * Không cần blocking EC, không tốn thread nào trong 500ms
     * Lý tưởng cho: HTTP calls, async DB drivers (R2DBC)
     *
     * Chạy: ab -n 200 -c 50 http://localhost:9000/bench/non-blocking
     */
    public CompletionStage<Result> nonBlocking() {
        String callerThread = Thread.currentThread().getName();
        CompletableFuture<Result> future = new CompletableFuture<>();

        // Thay Thread.sleep bằng async scheduler
        // → Không block thread nào trong 500ms!
        scheduler.schedule(() -> {
            String workerThread = Thread.currentThread().getName();
            future.complete(ok(buildResult("non-blocking", callerThread + " → " + workerThread)));
        }, SIMULATED_IO_MS, java.util.concurrent.TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * Info endpoint - xem cấu hình hiện tại
     */
    public Result info() {
        ObjectNode info = Json.newObject();
        info.put("simulatedIoMs", SIMULATED_IO_MS);
        info.put("currentThread", Thread.currentThread().getName());
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        info.put("maxMemoryMB", Runtime.getRuntime().maxMemory() / 1024 / 1024);

        ObjectNode endpoints = Json.newObject();
        endpoints.put("sync", "ab -n 200 -c 50 http://localhost:9000/bench/sync");
        endpoints.put("async-wrong", "ab -n 200 -c 50 http://localhost:9000/bench/async-wrong");
        endpoints.put("async-right", "ab -n 200 -c 50 http://localhost:9000/bench/async-right");
        endpoints.put("non-blocking", "ab -n 200 -c 50 http://localhost:9000/bench/non-blocking");
        info.set("benchmark-commands", endpoints);

        return ok(info);
    }

    // Giả lập blocking I/O (ví dụ: JDBC query chậm)
    private void simulateBlockingIO() {
        try {
            Thread.sleep(SIMULATED_IO_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ObjectNode buildResult(String endpoint, String threadInfo) {
        ObjectNode result = Json.newObject();
        result.put("endpoint", endpoint);
        result.put("thread", threadInfo);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
}
