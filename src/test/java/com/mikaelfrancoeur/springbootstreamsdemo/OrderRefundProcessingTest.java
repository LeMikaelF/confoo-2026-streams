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
import static org.springframework.test.web.client.ExpectedCount.twice;
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
        // Setup: 5 pages of orders available, but first 2 refund batches return 12 refunds
        // The stream should stop after getting 10, not fetching pages 3-5
        //
        // Stream flow with lazy evaluation:
        // 1. Fetch page 1 (5 orders)
        // 2. Batch 5 orders -> call refunds API (6 refunds)
        // 3. Need more, fetch page 2 (5 more orders)
        // 4. Batch 5 orders -> call refunds API (6 more refunds, 12 total)
        // 5. limit(10) stops the stream - pages 3-5 NOT fetched

        server.expect(once(), requestTo("http://localhost:8080/api/orders"))
                .andRespond(withSuccess(loadJson("orders-page-1.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor1"))
                .andRespond(withSuccess(loadJson("orders-page-2.json"), MediaType.APPLICATION_JSON));

        // Two refund API calls (one per batch of 5 orders)
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
        // Setup: 2 pages of orders (last page has no next cursor)
        // Refund batches return 3 each (6 total) - less than limit of 10
        // Stream should consume all pages since we need more but can't get them

        server.expect(once(), requestTo("http://localhost:8080/api/orders"))
                .andRespond(withSuccess(loadJson("orders-page-1.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor1"))
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
        // Setup: 3 pages of orders, but first batch of refunds is empty
        // Stream should continue processing next batches

        server.expect(once(), requestTo("http://localhost:8080/api/orders"))
                .andRespond(withSuccess(loadJson("orders-page-1.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor1"))
                .andRespond(withSuccess(loadJson("orders-page-2.json"), MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://localhost:8080/api/orders?lastCursor=cursor2"))
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
        // Setup: 1 page with 5 orders, first batch returns 15 refunds
        // Only 10 should be returned

        server.expect(once(), requestTo("http://localhost:8080/api/orders"))
                .andRespond(withSuccess(loadJson("orders-page-last.json"), MediaType.APPLICATION_JSON));

        // Single batch returns 15 refunds, but we only need 10
        server.expect(once(), requestTo("http://localhost:8080/api/refunds/pending"))
                .andExpect(method(HttpMethod.POST))
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
