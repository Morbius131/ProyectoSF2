package com.miempresa.gateway.controller;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoints propios del gateway para descubrimiento de servidores.
 *
 * GET /gateway/servers              → lista todos los servidores vivos
 * GET /gateway/servers/{serviceId}  → instancias de un servicio específico
 */
@RestController
@RequestMapping("/gateway")
public class ServersController {

    private final DiscoveryClient discoveryClient;

    public ServersController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    /**
     * Lista TODOS los servidores registrados en Eureka, de cualquier servicio.
     *
     * Respuesta:
     * [
     *   {
     *     "instanceId": "mi-host:mi-servicio:8081",
     *     "serviceId":  "MI-SERVICIO",
     *     "host":       "localhost",
     *     "port":       "8081",
     *     "uri":        "http://localhost:8081",
     *     "urlGateway": "/servidor/mi-host:mi-servicio:8081"
     *   },
     *   ...
     * ]
     *
     * El campo "urlGateway" es el prefijo que debe usar el cliente para
     * hablar con ese servidor específico a través del gateway.
     */
    @GetMapping("/servers")
    public ResponseEntity<List<Map<String, String>>> getAllServers() {
        List<Map<String, String>> result = discoveryClient.getServices().stream()
                .filter(service -> !service.equalsIgnoreCase("api-gateway")) // ← excluye el gateway
                .flatMap(service -> discoveryClient.getInstances(service).stream())
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Lista las instancias de un servicio en particular.
     *
     * Ejemplo: GET /gateway/servers/mi-servicio
     */
    @GetMapping("/servers/{serviceId}")
    public ResponseEntity<?> getServersByService(@PathVariable String serviceId) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);

        if (instances.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(instances.stream().map(this::toMap).toList());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private Map<String, String> toMap(ServiceInstance instance) {
    return Map.of(
            "urlGateway", "/servidor/" + instance.getInstanceId(),
            "endpoints",  instance.getMetadata().getOrDefault("endpoints", "")
    );
}
}
