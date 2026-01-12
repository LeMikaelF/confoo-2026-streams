package com.mikaelfrancoeur.springbootstreamsdemo;

import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.stream.Stream;

class Orders {

    private final RestClient restClient;
    private final String baseUrl;

    Orders(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    Stream<Order> all(String cursor) {
        // Using Stream.iterate without predicate, then takeWhile to include the last page
        // The trick: we need to emit the page BEFORE checking hasMore, not after
        return Stream.iterate(
                fetchPage(cursor),
                page -> page.hasMore() ? fetchPage(page.nextCursor()) : null
            )
            .takeWhile(page -> page != null)
            .flatMap(page -> page.orders().stream());
    }

    private OrdersPage fetchPage(String cursor) {
        var uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/orders");
        if (cursor != null && !cursor.isBlank()) {
            uri.queryParam("lastCursor", cursor);
        }

        return restClient.get()
            .uri(uri.build().toUri())
            .retrieve()
            .body(OrdersPage.class);
    }
}
