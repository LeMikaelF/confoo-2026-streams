package com.mikaelfrancoeur.springbootstreamsdemo;

import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

class RefundRequests {

    private final RestClient restClient;
    private final String baseUrl;

    RefundRequests(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    Stream<RefundRequest> forOrders(Collection<String> orderIds) {
        if (orderIds.isEmpty()) {
            return Stream.empty();
        }

        var response = restClient.post()
            .uri(baseUrl + "/api/refunds/pending")
            .body(new RefundRequestQuery(orderIds.stream().toList()))
            .retrieve()
            .body(RefundRequestsResponse.class);

        return response != null ? response.refundRequests().stream() : Stream.empty();
    }

    record RefundRequestQuery(List<String> orderIds) {}
}
