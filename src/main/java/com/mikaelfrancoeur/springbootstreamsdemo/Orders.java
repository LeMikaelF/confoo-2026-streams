package com.mikaelfrancoeur.springbootstreamsdemo;

import io.micrometer.common.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

class Orders {

    private final RestClient restClient;
    private final String baseUrl;

    Orders(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    Stream<Order> all(String cursor) {
        return Stream.iterate(
                        fetchPage(cursor),
                        Objects::nonNull,
                        page -> page.hasMore() ? fetchPage(page.nextCursor()) : null
                )
                .flatMap(page -> page.orders().stream());
    }

    private Page fetchPage(String cursor) {
        var uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/orders");
        if (cursor != null && !cursor.isBlank()) {
            uri.queryParam("lastCursor", cursor);
        }

        return restClient.get()
                .uri(uri.build().toUri())
                .retrieve()
                .body(Page.class);
    }

    record Page(List<Order> orders, String nextCursor) {
        boolean hasMore() {
            return !StringUtils.isBlank(nextCursor);
        }
    }
}
