package com.miempresa.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@Component
public class ServerRoutingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ServerRoutingFilter.class);
    private static final String PREFIX = "/servidor/";

    private final DiscoveryClient discoveryClient;

    public ServerRoutingFilter(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();

        if (!path.startsWith(PREFIX)) {
            return chain.filter(exchange);
        }

        String remainder = path.substring(PREFIX.length());
        int slash = remainder.indexOf('/');

        String serverId;
        String actualPath;

        if (slash == -1) {
            serverId   = remainder;
            actualPath = "/";
        } else {
            serverId   = remainder.substring(0, slash);
            actualPath = remainder.substring(slash);
        }

        ServiceInstance instance = findInstance(serverId);

        if (instance == null) {
            log.warn("Servidor no encontrado en Eureka: '{}'", serverId);
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        String query = exchange.getRequest().getURI().getRawQuery();
        String targetUriStr = instance.getUri().toString() + actualPath
                + (query != null ? "?" + query : "");

        URI targetUri = URI.create(targetUriStr);

        log.info("Enrutando [{}] {} -> {}", exchange.getRequest().getMethod(), path, targetUri);

        // Cambiar el path de la request al path real (sin /servidor/...)
        ServerHttpRequest newRequest = exchange.getRequest()
                .mutate()
                .path(actualPath)
                .build();

        // Establecer la URI destino usando el atributo que Spring Cloud Gateway usa para el routing
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, targetUri);

        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    private ServiceInstance findInstance(String serverId) {
        List<String> services = discoveryClient.getServices();

        for (String service : services) {
            List<ServiceInstance> instances = discoveryClient.getInstances(service);
            for (ServiceInstance instance : instances) {
                String instanceId = instance.getInstanceId();
                if (instanceId.equalsIgnoreCase(serverId)
                        || instanceId.toLowerCase().contains(serverId.toLowerCase())) {
                    return instance;
                }
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        // Debe correr DESPUÉS de RouteToRequestUrlFilter (10000) pero ANTES de NettyRoutingFilter
        return 10001;
    }
}
