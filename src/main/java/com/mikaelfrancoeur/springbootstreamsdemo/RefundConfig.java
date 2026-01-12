package com.mikaelfrancoeur.springbootstreamsdemo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class RefundConfig {

    @Value("${api.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    @ConditionalOnMissingBean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient restClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.build();
    }

    @Bean
    Orders orders(RestClient restClient) {
        return new Orders(restClient, baseUrl);
    }

    @Bean
    RefundRequests refundRequests(RestClient restClient) {
        return new RefundRequests(restClient, baseUrl);
    }

    @Bean
    RefundsRepository refundsRepository() {
        return new RefundsRepository();
    }
}
