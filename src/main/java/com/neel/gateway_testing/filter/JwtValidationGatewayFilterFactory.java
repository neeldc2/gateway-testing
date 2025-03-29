package com.neel.gateway_testing.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.neel.gateway_testing.config.CacheConfig;
import com.neel.gateway_testing.dto.UserContext;
import com.neel.gateway_testing.dto.ValidateResponse;
import com.neel.gateway_testing.dto.ValidationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtValidationGatewayFilterFactory.Config> {

    private final WebClient.Builder webClientBuilder;
    private final Cache<String, ValidateResponse> jwtCache;
    private final CacheConfig cacheConfig;

    public JwtValidationGatewayFilterFactory(WebClient.Builder webClientBuilder,
                                             Cache<String, ValidateResponse> jwtCache,
                                             CacheConfig cacheConfig) {
        super(Config.class);
        this.webClientBuilder = webClientBuilder;
        this.jwtCache = jwtCache;
        this.cacheConfig = cacheConfig;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Extract Authorization header
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header"));
            }

            final String token = authHeader.substring(7);
            // Check if token exists in cache
            final ValidateResponse validateResponseFromCache = jwtCache.getIfPresent(token);
            if (validateResponseFromCache != null) {
                log.info("JWT Token found in cache: " + validateResponseFromCache.getUserContext());
                ServerWebExchange newExchange = getServerWebExchange(exchange, validateResponseFromCache.getUserContext());
                return chain.filter(newExchange);
            }

            final ValidationRequest validationRequest = new ValidationRequest(authHeader);
            // Call auth service to validate JWT
            return webClientBuilder.build()
                    .post()
                    .uri("http://localhost:8100/api/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validationRequest)
                    .retrieve()
                    .bodyToMono(ValidateResponse.class)
                    .onErrorResume(e -> Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT token")))
                    .flatMap(validateResponse -> {
                        // Add user context to request attributes to be passed downstream
                        //exchange.getAttributes().put("userContext", userContext);

                        final UserContext userContextFromValidateApi = validateResponse.getUserContext();
                        jwtCache.put(token, validateResponse);

                        // Add user information to headers for downstream service
                        ServerWebExchange newExchange = getServerWebExchange(exchange, userContextFromValidateApi);
                        return chain.filter(newExchange);
                    });
        };
    }

    private static ServerWebExchange getServerWebExchange(
            final ServerWebExchange exchange,
            final UserContext userContext) {
        return exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header("x-user-id", String.valueOf(userContext.userId()))
                        .header("x-tenant", String.join(",", userContext.tenant()))
                        .header("x-tenant-id", String.join(",", userContext.tenantId().toString()))
                        .header("x-tenant-guid", String.valueOf(userContext.tenantGuid()))
                        .header("x-permissions", userContext.permissions().toString())
                        .build())
                .build();
    }

    public static class Config {
        // Add configuration properties if needed
    }
}
