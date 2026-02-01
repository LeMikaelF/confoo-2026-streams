# Spring Boot Streams Demo

A Java project demonstrating lazy-evaluated data pipelines using the Java Streams API with Spring Boot. The core use case — processing refund requests in batches from a paginated orders API — showcases how streams can minimize unnecessary I/O by deferring work until results are actually needed.

## What It Demonstrates

- **Lazy pagination** with `Stream.iterate()` — pages of orders are fetched on demand, not eagerly
- **Fixed-size batching** with `Gatherers.windowFixed()` (Java 23+) — order IDs are grouped into batches before querying for refunds
- **Stream composition** — `map`, `flatMap`, `limit`, and `reduce` are chained into a single declarative pipeline
- **Early termination** — `.limit(n)` stops the entire pipeline (including upstream HTTP calls) once enough refunds have been collected

## Tech Stack

- Java 25
- Spring Boot 4.0.1
- Spring WebMVC + `RestClient`
- Lombok
- Maven (with Maven Wrapper)
- JUnit 5, Mockito, AssertJ, `MockRestServiceServer`

## Project Structure

```
src/main/java/com/mikaelfrancoeur/springbootstreamsdemo/
├── config/
│   └── Config.java                  # RestClient bean configuration
├── domain/
│   ├── Order.java                   # Order record
│   ├── RefundRequest.java           # RefundRequest record
│   └── ProcessRefundBatchUseCase.java  # Core stream pipeline
├── inbound/
│   ├── Orders.java                  # Paginated order fetcher (Stream.iterate)
│   └── RefundRequests.java          # Batch refund request fetcher
├── outbound/
│   └── RefundProcessor.java         # Refund processing side-effect
└── SpringBootStreamsDemoApplication.java

src/test/
├── java/.../OrderRefundProcessingTest.java
└── resources/test-data/             # JSON fixtures for orders and refunds
```

## Build and Run

```bash
# Build
./mvnw clean package

# Run tests
./mvnw test
```

## The Stream Pipeline

`ProcessRefundBatchUseCase.execute()` builds a single stream pipeline:

```
orders.all(startCursor)              // Stream<Order>  — lazy paginated fetch
  .filter(COMPLETED)                 // Stream<Order>  — keep only completed orders
  .map(Order::id)                    // Stream<String>  — extract IDs
  .gather(Gatherers.windowFixed(5))  // Stream<List<String>>  — batch into groups of 5
  .flatMap(refundRequests::forOrders)// Stream<RefundRequest>  — fetch refunds per batch
  .limit(refundsToProcess)           // Stream<RefundRequest>  — stop early
  .toList()                          // List<RefundRequest>  — collect results
```

Because every stage is lazy, the pipeline only fetches as many order pages and refund batches as needed to satisfy the `limit`.

## Test Cases

`OrderRefundProcessingTest` uses `MockRestServiceServer` to verify pipeline behavior with four scenarios:

| Test | Scenario | Key assertion |
|------|----------|---------------|
| `lazinessVerification_streamStopsEarlyWhenLimitReached` | 4 pages available, limit stops early | Page 5 is never fetched |
| `allPagesConsumed_whenNotEnoughRefunds` | 3 pages, fewer refunds than limit | All pages consumed; returns what's available |
| `emptyRefundBatches_streamContinuesUntilLimitOrExhausted` | First refund batch is empty | Pipeline continues through subsequent batches |
| `singlePageSufficient_firstBatchHasEnoughRefunds` | Single page, large refund batch | Only COMPLETED order IDs sent; result capped at limit |

Test data includes orders with mixed statuses (COMPLETED, PENDING, CANCELLED) to ensure the `.filter()` step is exercised — removing it causes all tests to fail.
