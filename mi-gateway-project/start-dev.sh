#!/bin/bash
# start-dev.sh - Levanta registry y gateway en segundo plano

echo "======================================"
echo "  Mi Gateway Project - Modo Desarrollo"
echo "======================================"

# Compilar todo
echo ""
echo "► Compilando proyecto..."
mvn clean package -DskipTests -q
if [ $? -ne 0 ]; then
  echo "✗ Error en la compilación"
  exit 1
fi
echo "✓ Compilación exitosa"

# Levantar Registry
echo ""
echo "► Iniciando Eureka Registry en :8761..."
java -jar registry/target/registry-1.0.0.jar &
REGISTRY_PID=$!
echo "  PID: $REGISTRY_PID"

# Esperar a que el registry esté listo
echo "  Esperando que el registry esté listo..."
sleep 10

# Levantar Gateway
echo ""
echo "► Iniciando API Gateway en :8080..."
java -jar gateway/target/gateway-1.0.0.jar &
GATEWAY_PID=$!
echo "  PID: $GATEWAY_PID"

echo ""
echo "======================================"
echo "  Servicios levantados:"
echo "  Eureka Dashboard → http://localhost:8761"
echo "  API Gateway      → http://localhost:8080"
echo "  Rutas activas    → http://localhost:8080/actuator/gateway/routes"
echo "======================================"
echo ""
echo "  Para detener: kill $REGISTRY_PID $GATEWAY_PID"
echo "  O presiona Ctrl+C"
echo ""

# Esperar a que terminen
wait $REGISTRY_PID $GATEWAY_PID
