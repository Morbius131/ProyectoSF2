# Mi Gateway Project

Proyecto multi-módulo Maven con **Eureka Server** + **API Gateway** con enrutamiento
manual: el cliente elige a qué servidor específico enviar cada request.

## Módulos

| Módulo     | Puerto | Descripción                                    |
|------------|--------|------------------------------------------------|
| `registry` | 8761   | Eureka Server — registro dinámico de instancias|
| `gateway`  | 8080   | API Gateway — enrutamiento manual por servidor |

## Requisitos

- Java 17+
- Maven 3.8+

---

## Compilar

```bash
mvn clean package -DskipTests
```

## Ejecutar

```bash
# 1. Primero el registry
java -jar registry/target/registry-1.0.0.jar

# 2. Luego el gateway
java -jar gateway/target/gateway-1.0.0.jar

# 3. Tus servidores (N instancias)
PORT=8081 java -jar mi-servicio.jar
PORT=8082 java -jar mi-servicio.jar
PORT=8083 java -jar mi-servicio.jar
```

---

## Cómo usar el gateway

### Paso 1 — Ver qué servidores están vivos

```
GET http://localhost:8080/gateway/servers
```

Respuesta:
```json
[
  {
    "instanceId": "localhost:mi-servicio:8081",
    "serviceId":  "mi-servicio",
    "host":       "localhost",
    "port":       "8081",
    "uri":        "http://localhost:8081",
    "urlGateway": "/servidor/localhost:mi-servicio:8081"
  },
  {
    "instanceId": "localhost:mi-servicio:8082",
    ...
    "urlGateway": "/servidor/localhost:mi-servicio:8082"
  }
]
```

### Paso 2 — Llamar al servidor que elijas

Toma el `urlGateway` del servidor que quieres y agrega el path de tu endpoint:

```
GET  http://localhost:8080/servidor/localhost:mi-servicio:8081/productos
GET  http://localhost:8080/servidor/localhost:mi-servicio:8082/productos
POST http://localhost:8080/servidor/localhost:mi-servicio:8083/productos
GET  http://localhost:8080/servidor/localhost:mi-servicio:8081/productos/5?activo=true
```

Cada llamada va **directo** al servidor indicado, sin load balancing.

### Paso 3 — Ver instancias de un servicio específico (opcional)

```
GET http://localhost:8080/gateway/servers/mi-servicio
```

---

## Configurar un microservicio para registrarse

Agregar al `pom.xml` del microservicio:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

Agregar al `application.yml`:

```yaml
spring:
  application:
    name: mi-servicio          # nombre del servicio en Eureka

server:
  port: ${PORT:8081}           # puerto dinámico vía variable de entorno

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
    # instanceId único por instancia (host:nombre:puerto)
    instance-id: ${spring.cloud.client.hostname}:${spring.application.name}:${server.port}
```

Al arrancar se registra solo. Al apagarse se da de baja automáticamente.

---

## URLs de referencia

| URL                                          | Descripción                         |
|----------------------------------------------|-------------------------------------|
| http://localhost:8761                        | Dashboard Eureka                    |
| http://localhost:8080/gateway/servers        | Todos los servidores vivos          |
| http://localhost:8080/gateway/servers/{id}   | Instancias de un servicio           |
| http://localhost:8080/actuator/gateway/routes| Rutas activas en el gateway         |
| http://localhost:8080/actuator/health        | Health del gateway                  |
