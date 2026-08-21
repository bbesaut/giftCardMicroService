package com.finovago.p2p.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.models.GroupedOpenApi;

@Configuration
public class OpenApiConfig {

    @Bean
    GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public-api")
                .pathsToMatch("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                .build();
    }

    @Bean
    GroupedOpenApi customerApi() {
        return GroupedOpenApi.builder()
                .group("customer-api")
                .pathsToMatch("/", "/api/v1/giftcards/create", "/api/v1/giftcards/redeem", "/api/v1/giftcards/lookup/**",
                        "/api/v1/giftcards/reserve", "/api/v1/giftcards/holds/**", "/api/v1/giftcards/*/ledger",
                        "/api/v1/giftcards/refund", "/api/v1/giftcards/credit", "/api/v1/auth/me/users",
                        "/api/v1/auth/me/users/**", "/api/v1/auth/me/api-key", "/api/v1/auth/me/api-key/**",
                        "/api/v1/auth/me/password")
                .build();
    }

    @Bean
    GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin-api")
                .pathsToMatch("/api/v1/auth/register", "/api/v1/giftcards/list", "/api/v1/auth/me/password")
                .build();
    }
}