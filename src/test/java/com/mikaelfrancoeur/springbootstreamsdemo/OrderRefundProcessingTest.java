package com.mikaelfrancoeur.springbootstreamsdemo;

import com.mikaelfrancoeur.springbootstreamsdemo.domain.ProcessRefundBatchUseCase;
import com.mikaelfrancoeur.springbootstreamsdemo.domain.RefundRequest;
import com.mikaelfrancoeur.springbootstreamsdemo.inbound.Orders;
import com.mikaelfrancoeur.springbootstreamsdemo.inbound.RefundRequests;
import com.mikaelfrancoeur.springbootstreamsdemo.outbound.RefundProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
class OrderRefundProcessingTest {

    @Autowired
    private RestClient.Builder restClientBuilder;
    @MockitoBean
    private RefundProcessor refundProcessor;

    private MockRestServiceServer server;
    private ProcessRefundBatchUseCase processRefundBatchUseCase;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.bindTo(restClientBuilder).ignoreExpectOrder(true).build();
        RestClient restClient = restClientBuilder.build();
        Orders orders = new Orders(restClient, "http://localhost:8080");
        RefundRequests refundRequests = new RefundRequests(restClient, "http://localhost:8080");
        processRefundBatchUseCase = new ProcessRefundBatchUseCase(orders, refundRequests, refundProcessor);
    }

    @Test
    void lazinessVerification_streamStopsEarlyWhenLimitReached() throws Exception {
        // Pages 1-3 have 3 COMPLETED each, page 4 has 5 COMPLETED = 14 total COMPLETED
        // windowFixed(5) batching with filter:
        //   Page 1: 3 COMPLETED IDs (buffer: 3, no batch yet)
        //   Page 2: 3 more (buffer: 6 -> emit batch of 5, carry 1)
        //   Page 3: 3 more (buffer: 4, no batch yet)
        //   Page 4: 5 more (buffer: 9 -> emit batch of 5, carry 4)
        // 2 refund API calls x 6 refunds each = 12 total, limit(10) -> 10
        // Page 5 is NOT fetched (laziness)
        //
        // Without filter: 5 orders/page -> batch every page -> different fetch pattern

        server.expect(once(), requestTo("http://localhost:8080/api/orders"))
                .andRespond(withSuccess(loadJson("orders-page-1.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor1"))
                .andRespond(withSuccess(loadJson("orders-page-2.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor2"))
                .andRespond(withSuccess(loadJson("orders-page-3.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor3"))
                .andRespond(withSuccess(loadJson("orders-page-4.json"), MediaType.APPLICATION_JSON));

        // Two refund API calls (one per batch of 5 COMPLETED order IDs)
        server.expect(twice(), requestTo("http://localhost:8080/api/refunds/pending"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(loadJson("refunds-batch-1.json"), MediaType.APPLICATION_JSON));

        var result = processRefundBatchUseCase.execute(10, null);

        assertThat(result)
                .extracting(RefundRequest::id)
                .containsExactly("refund-1", "refund-2", "refund-3", "refund-4", "refund-5",
                        "refund-6", "refund-1", "refund-2", "refund-3", "refund-4");

        verify(refundProcessor).process(assertArg(refundRequest -> assertThat(refundRequest).hasSize(10)));
        server.verify();
    }

    @Test
    void allPagesConsumed_whenNotEnoughRefunds() throws Exception {
        // Pages 1, 2, page-last: 3 COMPLETED each = 9 total COMPLETED
        // windowFixed(5): batch of 5 + partial batch of 4 = 2 refund calls
        // Each returns 3 refunds -> 6 total (under limit of 10)
        //
        // Without filter: 15 orders -> 3 batches of 5 -> 3 refund calls -> server.verify() fails

        server.expect(once(), requestTo("http://localhost:8080/api/orders"))
                .andRespond(withSuccess(loadJson("orders-page-1.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor1"))
                .andRespond(withSuccess(loadJson("orders-page-2.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor2"))
                .andRespond(withSuccess(loadJson("orders-page-last.json"), MediaType.APPLICATION_JSON));

        // Two refund API calls, each returning 3 refunds
        server.expect(twice(), requestTo("http://localhost:8080/api/refunds/pending"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(loadJson("refunds-batch-small.json"), MediaType.APPLICATION_JSON));

        var result = processRefundBatchUseCase.execute(10, null);

        assertThat(result).hasSize(6);
        verify(refundProcessor).process(assertArg(refundRequest -> assertThat(refundRequest).hasSize(6)));
        server.verify();
    }

    @Test
    void emptyRefundBatches_streamContinuesUntilLimitOrExhausted() throws Exception {
        // Pages 1, 2, 3, page-last: 3 COMPLETED each = 12 total COMPLETED
        // windowFixed(5): batch of 5, batch of 5, partial batch of 2 = 3 refund calls
        // Returns: empty + 6 + 3 = 9 total (under limit of 10)
        //
        // Without filter: 20 orders -> 4 batches of 5 -> 4 refund calls -> server.verify() fails

        server.expect(once(), requestTo("http://localhost:8080/api/orders"))
                .andRespond(withSuccess(loadJson("orders-page-1.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor1"))
                .andRespond(withSuccess(loadJson("orders-page-2.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor2"))
                .andRespond(withSuccess(loadJson("orders-page-3.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor3"))
                .andRespond(withSuccess(loadJson("orders-page-last.json"), MediaType.APPLICATION_JSON));

        // First batch -> empty, second -> 6, third -> 3 (total 9)
        server.expect(once(), requestTo("http://localhost:8080/api/refunds/pending"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(loadJson("refunds-batch-empty.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/refunds/pending"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(loadJson("refunds-batch-1.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/refunds/pending"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(loadJson("refunds-batch-small.json"), MediaType.APPLICATION_JSON));

        var result = processRefundBatchUseCase.execute(10, null);

        assertThat(result).hasSize(9);
        verify(refundProcessor).process(assertArg(refundRequest -> assertThat(refundRequest).hasSize(9)));
        server.verify();
    }

    @Test
    void singlePageSufficient_firstBatchHasEnoughRefunds() throws Exception {
        // page-last only: 3 COMPLETED out of 5 orders
        // windowFixed(5): partial batch of 3 -> 1 refund call -> 15 refunds -> limit(10) = 10
        //
        // Without filter: batch of 5 order IDs sent -> content assertion fails (expects 3)

        server.expect(once(), requestTo("http://localhost:8080/api/orders"))
                .andRespond(withSuccess(loadJson("orders-page-last.json"), MediaType.APPLICATION_JSON));

        // Verify that only the 3 COMPLETED order IDs are sent in the request body
        server.expect(once(), requestTo("http://localhost:8080/api/refunds/pending"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"orderIds\":[\"order-1\",\"order-3\",\"order-5\"]}"))
                .andRespond(withSuccess(loadJson("refunds-batch-large.json"), MediaType.APPLICATION_JSON));

        var result = processRefundBatchUseCase.execute(10, null);

        assertThat(result).hasSize(10);
        assertThat(result).extracting(RefundRequest::id)
                .containsExactly("refund-1", "refund-2", "refund-3", "refund-4", "refund-5",
                        "refund-6", "refund-7", "refund-8", "refund-9", "refund-10");
        verify(refundProcessor).process(assertArg(refundRequest -> assertThat(refundRequest).hasSize(10)));
        server.verify();
    }

    private String loadJson(String filename) throws Exception {
        var resource = new ClassPathResource("test-data/" + filename);
        return Files.readString(resource.getFile().toPath(), StandardCharsets.UTF_8);
    }
}
