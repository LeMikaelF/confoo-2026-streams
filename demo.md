# Java Streams demo plan

I want to build a demo on how to use streams to accomplish this:

1. Query the "orders" API. It returns pages of orders, and accepts a "lastCursor" to decide which page to show.
2. Query the "refunds" API. It accepts batches of orders, and returns 0..N pending refund requests.
3. Process the first N refund requests.

The catch is that I only need 10 refund requests, so I want to consume APIs 1 and 2 only as much as I need to.

This is a perfect use case for Java streams, and that's what I demonstrate. I will consume the first API using a
`Stream::iterate` pattern. I will send batches to the second API using `Gatherers.fixedWindow`. I will collect them in a
single batch using `reduce()`, and I will limit the source with `.limit()`.

## Implementation

1. The "orders" client will be a class `Orders` that exposes a method `Stream<Order> all(String cursor)`, and
   internally uses `Stream::iterate`.
2. A `RefundRequests` class that exposes a method `Stream<RefundRequest> forOrders(Collection<String> orderIds)`.
3. A `RefundsRepository` class that exposes a method `processRefunds(Collection<RefundRequest> refunds)`.
4. A business logic class that implements the flow and uses the first 3 classes as dependencies.

The first 3 classes above will be package-private.

## Testing

The complete flow will be tested in a single test suite. It will be `@SpringBootTest(classes = ...)`, using a
`MockRestServiceServer`. The server will use test JSON files (dummy order and refund request responses) saved as resources.