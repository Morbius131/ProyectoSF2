# Sistema Cliente/Servidor — Java + Spring Boot + MySQL

## Arquitectura

```
src/main/java/Cliente_Servidor/V1/
├── ARQ_UDP/          PaqueteUDP.java              — Modelo de paquete UDP con ARQ
├── Cliente/          ClienteTCP.java              — Cliente interactivo TCP (consola)
│                     ClienteUDP.java              — Cliente UDP con stop-and-wait
├── Controladores/    UsuarioControlador.java       — Registro y listado de usuarios
│                     DocumentoControlador.java     — CRUD de mensajes y archivos
│                     LogControlador.java           — Informe de logs
│                     ConexionControlador.java      — Clientes conectados
├── DTO/              RequestDTO, ResponseDTO, DocumentoDTO, LogDTO, ...
├── Enums/            TipoPeticion, TipoProtocolo, TipoLog, ...
├── Fabricas/         FabricaUsuarios, FabricaConexionUsuario
├── Handles/          RequestHandler (interfaz)
│                     RequestHandlerImplementacion  — Despachador central JSON
├── Modelos/          Usuario, UsuarioNormal, Documento, Archivo, Mensaje, Logs
├── Pool/             UsuarioConexionPool           — Object Pool de conexiones
├── Repository/       JPA Repositories (MySQL)
├── Servidor/         Servidor (interfaz), ServidorTCP, ServidorUDP
├── Servicios/        UsuarioService, DocumentoService (hash+encriptación), LogService
├── Util/             CryptoUtil (SHA-256 + AES-128)
│                     AlmacenamientoArchivos        — Guarda archivos originales en disco
│                     ConsolaUtil                   — Display de actividad en consola
├── Main.java         — Entry point Spring Boot
└── ServerRunner.java — Arranca TCP (5000), UDP (6000) y monitor de pool
```

## Funcionalidades implementadas

| Servicio                                  | Protocolo | Petición JSON                              |
|-------------------------------------------|-----------|--------------------------------------------|
| Conectar cliente / registrar usuario      | TCP/UDP   | `type=USUARIO method=POST`                 |
| Listar clientes conectados (IP·fecha·hora)| TCP/UDP   | `type=USUARIO method=LIST`                 |
| Listar documentos disponibles             | TCP/UDP   | `type=ARCHIVO method=LIST`                 |
| Enviar mensaje                            | TCP/UDP   | `type=MENSAJE method=POST`                 |
| Enviar archivo (soporta >1 GB, Base64)    | TCP/UDP   | `type=ARCHIVO method=POST`                 |
| Obtener documento plano                   | TCP/UDP   | `type=ARCHIVO method=GET`                  |
| Obtener documento con hash SHA-256        | TCP/UDP   | `type=ARCHIVO method=GET accion=CON_HASH`  |
| Obtener documento encriptado AES-128      | TCP/UDP   | `type=ARCHIVO method=GET accion=ENCRIPTADO`|
| Ver informe de logs                       | TCP/UDP   | `type=LOG method=LIST`                     |
| Informar pool lleno (rechazo de conexión) | TCP       | Automático cuando pool está lleno          |

## Flujo de procesamiento de documentos

```
Cliente envía documento
       ↓
1. Guardar archivo ORIGINAL en ./archivos_originales/
2. Calcular hash SHA-256 del contenido
3. Encriptar contenido con AES-128/ECB/PKCS5
4. Persistir en MySQL (hash + contenido_encriptado)
5. Registrar log en BD
6. Responder al cliente con DocumentoDTO
```

## Configuración

### application.properties
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/cliente_servidor_db
spring.datasource.username=root
spring.datasource.password=TU_PASSWORD
spring.main.web-application-type=none  # Aplicación de consola pura
```

### Crear la base de datos
```sql
-- Ejecutar schema.sql en MySQL
mysql -u root -p < schema.sql
```

## Compilar y ejecutar

### Servidor
```bash
mvn clean package -DskipTests
java -jar target/V1-0.0.1-SNAPSHOT.jar
```

### Cliente TCP (interactivo)
```bash
# Desde el mismo JAR, indicando la clase main:
java -cp target/V1-0.0.1-SNAPSHOT.jar Cliente_Servidor.V1.Cliente.ClienteTCP localhost 5000
```

### Cliente UDP
```bash
java -cp target/V1-0.0.1-SNAPSHOT.jar Cliente_Servidor.V1.Cliente.ClienteUDP localhost 6000
```

## Protocolo JSON

### Petición
```json
{
  "type": "USUARIO",
  "method": "POST",
  "data": {
    "nombre": "juan",
    "tipoUsuario": "NORMAL"
  }
}
```

### Respuesta
```json
{
  "status": "OK",
  "statusCode": 200,
  "data": { "id": 1, "nombre": "juan" }
}
```

## Object Pool

- Máximo de conexiones simultáneas: **10** (configurable en `UsuarioConexionPool.MAX_CONEXIONES`)
- Implementación: `ConcurrentHashMap` seguro para multihilo
- Cuando el pool está lleno, el servidor responde con error y cierra la conexión

## Seguridad

- **Hash**: SHA-256 sobre el contenido (texto o Base64 del archivo)
- **Cifrado**: AES-128/ECB/PKCS5Padding (clave fija en `CryptoUtil.AES_KEY`)
  - ⚠️ En producción: usar un KeyStore o variable de entorno para la clave

## Archivos originales

Los archivos se guardan en `./archivos_originales/` con timestamp prefijado:
```
./archivos_originales/
├── 20260422_103045_reporte.pdf
├── 20260422_103120_msg_Hola_mundo.txt
└── ...
```
