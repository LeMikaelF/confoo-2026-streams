package com.mikaelfrancoeur.springbootstreamsdemo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.resttestclient.autoconfigure.RestTestClientBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(classes = StreamsTest.Dummy.class)
class StreamsTest {

    private RestClient restClient;
    private MockRestServiceServer server;

    @BeforeEach
    void beforeEach() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @Test
    void test() {
        server.expect(anything()).andRespond(withSuccess());

        // scenario: I query a pageable list of objects. I then need to send them to another API that accepts batches.
        // The second API returns lists of variable length, and I need only 30 elements.

        // For this, I'll need a stream generator (with Stream::iterate) to consume the returned elements from the first API.
        // Then, I'll need to post then with a gatherer (fixedWindow) to the second API.
    }

    static class Dummy {
    }
}
