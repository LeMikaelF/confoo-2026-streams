package com.mikaelfrancoeur.springbootstreamsdemo.inbound;

import com.mikaelfrancoeur.springbootstreamsdemo.domain.Order;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class Orders {

    private final RestClient restClient;
    @Value("${api.base-url:http://localhost:8080}")
    private final String baseUrl;

    public Stream<Order> all() {
        return Stream.iterate(
                        fetchPage(null),
                        Objects::nonNull,
                        page -> page.hasMore() ? fetchPage(page.nextCursor()) : null
                )
                .flatMap(page -> page.orders().stream());
    }

    private Page fetchPage(@Nullable String cursor) {
        var uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/orders");
        if (cursor != null && !cursor.isBlank()) {
            uri.queryParam("lastCursor", cursor);
        }

        return restClient.get()
                .uri(uri.build().toUri())
                .retrieve()
                .body(Page.class);
    }

    private record Page(List<Order> orders, String nextCursor) {
        boolean hasMore() {
            return !StringUtils.isBlank(nextCursor);
        }
    }
}
