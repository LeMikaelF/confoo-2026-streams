package com.mikaelfrancoeur.springbootstreamsdemo.inbound;

import com.mikaelfrancoeur.springbootstreamsdemo.domain.RefundRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RefundRequests {

    private final RestClient restClient;
    @Value("${api.base-url:http://localhost:8080}")
    private final String baseUrl;

    public Stream<RefundRequest> forOrders(Collection<String> orderIds) {
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

    private record RefundRequestQuery(List<String> orderIds) {
    }

    private record RefundRequestsResponse(List<RefundRequest> refundRequests) {
    }
}
