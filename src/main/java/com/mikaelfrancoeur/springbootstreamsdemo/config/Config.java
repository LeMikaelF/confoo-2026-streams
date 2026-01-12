package com.mikaelfrancoeur.springbootstreamsdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class Config {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient restClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.build();
    }

}
