package org.apache.skywalking.apm.testcase.sc.gateway.projectA;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author songxiaoyue
 */
@Component
public class ApiKeyResolver implements KeyResolver{

    public Mono<String> resolve(ServerWebExchange exchange) {
        return Mono.just(exchange.getRequest().getPath().value());
    }
}
