# Day 23 - Pekko Streams: Xử Lý Dữ Liệu Reactive

## Mục tiêu
- Hiểu khái niệm Source, Flow, Sink
- Stream dữ liệu lớn không tốn RAM
- Chunked HTTP response

---

## 1. Pekko Streams Concepts

```
Source[T, Mat]     →     Flow[In, Out, Mat]     →     Sink[T, Mat]
(tạo data)               (transform data)              (consume data)

Ví dụ:
File lines         →     Filter blank lines     →     HTTP response
Database rows      →     Map to JSON            →     Write to file
Kafka topic        →     Aggregate              →     Database
```

**Backpressure**: Consumer quyết định tốc độ → producer không thể overwhelm consumer.

---

## 2. Source Cơ Bản

```java
import org.apache.pekko.stream.javadsl.*;
import org.apache.pekko.util.ByteString;

// Source từ collection
Source<Integer, ?> numbers = Source.range(1, 1000);

// Source từ iterator (lazy - không load vào RAM)
Source<String, ?> lines = Source.fromIterator(() -> Files.lines(path).iterator());

// Source từ single value
Source<String, ?> single = Source.single("hello");

// Source tick (periodic)
Source<String, ?> ticker = Source.tick(
    Duration.ZERO,
    Duration.ofSeconds(1),
    "tick"
);

// Source từ CompletionStage
Source<User, ?> fromFuture = Source.completionStage(userService.findById(1L));
```

---

## 3. Flow Transformation

```java
Source<Integer, ?> numbers = Source.range(1, 100);

// map: transform mỗi element
numbers.map(n -> n * 2);  // 2, 4, 6, ...

// filter: giữ element thỏa điều kiện
numbers.filter(n -> n % 2 == 0);  // 2, 4, 6, ...

// collect: combined filter + map
numbers.collect(n -> n % 3 == 0 ? Optional.of("divisible: " + n) : Optional.empty());

// flatMapConcat: 1 element → nhiều elements
numbers.flatMapConcat(n -> Source.from(Arrays.asList(n, n * 10)));

// take/drop
numbers.take(10);    // Lấy 10 đầu
numbers.drop(10);    // Bỏ 10 đầu

// buffer: buffer N elements
numbers.buffer(100, OverflowStrategy.backpressure());

// throttle: giới hạn tốc độ
numbers.throttle(10, Duration.ofSecond(1));  // Max 10 elements/second
```

---

## 4. Sink

```java
// Sink.ignore(): Tiêu thụ mà không làm gì
Sink<Integer, CompletionStage<Done>> ignore = Sink.ignore();

// Sink.foreach(): Side effect cho mỗi element
Sink<String, CompletionStage<Done>> print = Sink.foreach(System.out::println);

// Sink.seq(): Thu thập thành List
Sink<Integer, CompletionStage<List<Integer>>> collect = Sink.seq();

// Sink.fold(): Aggregate
Sink<Integer, CompletionStage<Integer>> sum =
    Sink.fold(0, (acc, n) -> acc + n);
```

---

## 5. Stream HTTP Response (Chunked)

```java
import play.libs.streams.Accumulator;

// Stream 1 triệu records từ DB về client
// Memory footprint: O(chunk size), không phải O(total records)
public Result streamLargeData() {
    // Source tạo data (lazy)
    Source<ByteString, ?> source = Source.range(1, 1_000_000)
        .map(i -> "Line " + i + ": " + UUID.randomUUID() + "\n")
        .map(ByteString::fromString);

    // Play gửi chunked response
    return ok().chunked(source).as("text/plain");
}

// Stream CSV export
public Result exportCsv() {
    Source<ByteString, ?> csvSource = Source.single("id,title,done\n")
        .concat(
            Source.fromIterator(() -> todoRepository.streamAll())
                .map(todo -> todo.id + "," + todo.title + "," + todo.done + "\n")
                .map(ByteString::fromString)
        );

    return ok().chunked(csvSource)
        .as("text/csv")
        .withHeader("Content-Disposition", "attachment; filename=todos.csv");
}
```

---

## 6. Materialize Stream

```java
// Materializer thực sự chạy stream
Materializer mat = /* inject từ Play */;

// Chạy stream, lấy kết quả
CompletionStage<List<Integer>> result =
    Source.range(1, 10)
          .filter(n -> n % 2 == 0)
          .runWith(Sink.seq(), mat);

result.thenAccept(list -> System.out.println(list));
// [2, 4, 6, 8, 10]
```

---

## 7. Bài Tập

1. Stream 10,000 numbers từ API endpoint (chunked)
2. CSV export cho Todo list
3. Server-Sent Events với Source.tick (mỗi giây push 1 event)
4. Filter và transform stream trước khi gửi về client

```bash
# Test streaming
curl http://localhost:9000/stream/numbers | wc -l

# Test CSV export
curl http://localhost:9000/export/csv -o todos.csv
wc -l todos.csv
```
