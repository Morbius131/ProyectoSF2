package Cliente_Servidor.V1.Cliente;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/**
 * Cliente UDP puro — sin ACK, sin ventana, sin retransmisión.
 *
 * Envío de archivos:
 *   1. POST_CHUNK_INIT  → servidor crea ChunkBuffer, devuelve uploadId
 *   2. Disparo masivo   → todos los chunks se envían sin esperar respuesta
 *   3. POST_CHUNK_END   → servidor ensambla, hashea, encripta y persiste
 *
 * Velocidad máxima: limitada solo por el SO y la NIC, no por RTT.
 */
public class ClienteUDP {

    private static final String HOST_DEFAULT   = "localhost";
    private static final int    PORT_DEFAULT   = 6000;
    private static final int    TIMEOUT_MS     = 30000;
    private static final int    BUF_SIZE       = 524288;  // 512 KB recv
    private static final int    CHUNK_SIZE     = 32768;   // 32 KB por chunk
    private static final int    LOTE_SIZE      = 500;     // chunks por lote antes de pausar
    private static final long   PAUSA_LOTE_MS  = 20;      // pausa breve anti-saturación SO
    private static final int    END_TIMEOUT_MS = 30_000;  // margen mayor para procesamiento en servidor

    private static final String DOWNLOAD_DIR = "documentos_recibidos";

    private final String        host;
    private final int           port;
    private final ObjectMapper  mapper;
    private       DatagramSocket socket;
    private       InetAddress    address;
    private       String         nombreUsuario;

    public ClienteUDP(String host, int port) throws Exception {
        this.host    = host;
        this.port    = port;
        this.address = InetAddress.getByName(host);
        this.socket  = new DatagramSocket();
        this.socket.setSoTimeout(TIMEOUT_MS);
        // Buffer de envío grande para que el SO no dropee paquetes
        this.socket.setSendBufferSize(4 * 1024 * 1024);   // 4 MB
        Files.createDirectories(Paths.get(DOWNLOAD_DIR));
        this.mapper  = new ObjectMapper();
        this.mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build());
    }

    // ── Transporte básico (control: siempre request→response) ────────────────

    private String enviar(String payloadJson) throws Exception {
        byte[] b = payloadJson.getBytes();
        socket.send(new DatagramPacket(b, b.length, address, port));
        byte[] buf = new byte[BUF_SIZE];
        DatagramPacket rsp = new DatagramPacket(buf, buf.length);
        socket.receive(rsp);
        return new String(rsp.getData(), 0, rsp.getLength());
    }

    private String enviarRequest(Map<String, Object> req) throws Exception {
        return enviar(mapper.writeValueAsString(req));
    }

    private List<String> enviarYRecibirLineas(Map<String, Object> req) throws Exception {
        byte[] b = mapper.writeValueAsString(req).getBytes();
        socket.send(new DatagramPacket(b, b.length, address, port));
        List<String> lineas = new ArrayList<>();
        byte[] buf = new byte[BUF_SIZE];
        StringBuilder acum = new StringBuilder();
        boolean fin = false;

        // El servidor UDP envía TODAS las líneas en un único datagrama.
        // Recibimos ese paquete y procesamos todas las líneas del buffer
        // acumulado antes de volver a llamar receive(), evitando así el
        // timeout que ocurría al esperar un segundo paquete que nunca llega.
        while (!fin) {
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            socket.receive(pkt);
            acum.append(new String(pkt.getData(), 0, pkt.getLength()));

            // Procesar TODAS las líneas ya disponibles en el acumulador
            // antes de intentar recibir otro paquete.
            int idx;
            while ((idx = acum.indexOf("\n")) >= 0) {
                String linea = acum.substring(0, idx).trim();
                acum.delete(0, idx + 1);
                if (linea.isEmpty()) continue;
                if (linea.contains("\"FIN\"")) { fin = true; break; }
                lineas.add(linea);
            }

            // Si después de procesar todas las líneas el acumulador aún
            // contiene texto sin salto de línea, puede ser la última línea
            // sin \n al final (e.g. {"status":"FIN"} sin trailing newline).
            if (!fin && acum.length() > 0) {
                String resto = acum.toString().trim();
                if (!resto.isEmpty()) {
                    if (resto.contains("\"FIN\"")) {
                        fin = true;
                    } else {
                        lineas.add(resto);
                    }
                    acum.setLength(0);
                }
            }
        }
        return lineas;
    }

    // ── Operaciones básicas ──────────────────────────────────────────────────

    public void registrarUsuario(String nombre) throws Exception {
        this.nombreUsuario = nombre;
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "USUARIO"); req.put("method", "POST");
        req.put("data", Map.of("nombre", nombre, "tipoUsuario", "NORMAL"));
        imprimirRespuesta("REGISTRO DE USUARIO (UDP)", enviarRequest(req));
    }

    public void listarConectados() throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "USUARIO"); req.put("method", "LIST"); req.put("data", Map.of());
        imprimirRespuesta("CLIENTES CONECTADOS", enviarRequest(req));
    }

    public void listarDocumentos() throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "ARCHIVO"); req.put("method", "LIST"); req.put("data", Map.of());
        String resp = enviarRequest(req);
        System.out.println("\n  ── DOCUMENTOS DISPONIBLES ──────────────────────");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(resp, Map.class);
            Object data = map.get("data");
            if (data instanceof List<?> lista) {
                if (lista.isEmpty()) {
                    System.out.println("  (sin documentos)");
                } else {
                    System.out.printf("  %-6s  %s%n", "ID", "NOMBRE");
                    System.out.println("  ──────  ──────────────────────────────────");
                    for (Object item : lista) {
                        if (item instanceof Map<?, ?> raw) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> doc = (Map<String, Object>) raw;
                            System.out.printf("  %-6s  %s%n",
                                    doc.getOrDefault("id", "?"),
                                    doc.getOrDefault("nombreVisible", doc.getOrDefault("nombre", "?")));
                        }
                    }
                }
            } else { System.out.println(resp); }
        } catch (Exception e) { System.out.println(resp); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void enviarMensaje(String asunto, String contenido) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tipo", "MENSAJE"); data.put("asunto", asunto);
        data.put("contenido", contenido); data.put("tipoPropietario", "LOCAL");
        data.put("usuarioNombre", nombreUsuario); data.put("usuarioId", 0);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "MENSAJE"); req.put("method", "POST"); req.put("data", data);
        imprimirConfirmacion("ENVIO MENSAJE", enviarRequest(req));
    }

    // ── Envío de archivo UDP PURO ────────────────────────────────────────────

    /**
     * Envío sin ACK, sin ventana, sin retransmisión.
     *
     * Flujo:
     *   INIT  → obtiene uploadId
     *   CHUNKS → disparo masivo, un datagrama por chunk, sin esperar nada
     *   END   → servidor ensambla todo
     *
     * El servidor acumula los chunks en el ChunkBuffer (que ya tolera
     * llegadas desordenadas por chunkIndex) y ensambla al recibir END.
     */
    public void enviarArchivo(String rutaArchivo) throws Exception {
        File file = new File(rutaArchivo);
        if (!file.exists()) { System.out.println("  [ERROR] No encontrado: " + rutaArchivo); return; }

        String nombre       = file.getName();
        int    dot          = nombre.lastIndexOf('.');
        String sinExt       = dot >= 0 ? nombre.substring(0, dot) : nombre;
        String ext          = dot >= 0 ? nombre.substring(dot + 1) : "bin";
        long   tamanio      = file.length();
        int    totalChunks  = (int) ((tamanio + CHUNK_SIZE - 1) / CHUNK_SIZE);

        System.out.printf("%n  Archivo    : %s  (%,d bytes)%n", nombre, tamanio);
        System.out.printf("  Chunks     : %d × %d KB  — UDP PURO (sin ACK)%n",
                totalChunks, CHUNK_SIZE / 1024);

        // ── INIT ─────────────────────────────────────────────────────────────
        Map<String, Object> initData = new LinkedHashMap<>();
        initData.put("nombre", sinExt); initData.put("extension", ext);
        initData.put("tamanio", tamanio); initData.put("totalChunks", totalChunks);
        initData.put("tipoPropietario", "LOCAL");
        initData.put("usuarioNombre", nombreUsuario); initData.put("usuarioId", 0);
        Map<String, Object> initReq = new LinkedHashMap<>();
        initReq.put("type", "ARCHIVO"); initReq.put("method", "POST_CHUNK_INIT");
        initReq.put("data", initData);

        String initResp = enviarRequest(initReq);
        @SuppressWarnings("unchecked")
        Map<String, Object> initMap = mapper.readValue(initResp, Map.class);
        if (!"OK".equals(initMap.get("status"))) {
            System.out.println("  [ERROR] " + initMap.get("message")); return;
        }
        @SuppressWarnings("unchecked")
        String uploadId = (String) ((Map<String, Object>) initMap.get("data")).get("uploadId");
        System.out.println("  Upload ID  : " + uploadId);

        // ── DISPARO MASIVO DE CHUNKS (sin esperar nada) ──────────────────────
        long t0 = System.currentTimeMillis();
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file), 65536)) {
            byte[] buf = new byte[CHUNK_SIZE];
            int chunkIndex = 0;
            int leidos;
            while ((leidos = leerCompleto(bis, buf)) > 0) {
                byte[] datos = (leidos == CHUNK_SIZE) ? buf.clone() : Arrays.copyOf(buf, leidos);

                Map<String, Object> chunkData = new LinkedHashMap<>();
                chunkData.put("uploadId",    uploadId);
                chunkData.put("chunkIndex",  chunkIndex);
                chunkData.put("totalChunks", totalChunks);
                chunkData.put("contenido",   Base64.getEncoder().encodeToString(datos));
                Map<String, Object> chunkReq = new LinkedHashMap<>();
                chunkReq.put("type", "ARCHIVO");
                chunkReq.put("method", "POST_CHUNK");
                chunkReq.put("data", chunkData);

                byte[] pkt = mapper.writeValueAsString(chunkReq).getBytes();
                socket.send(new DatagramPacket(pkt, pkt.length, address, port));

                chunkIndex++;

                // Pausa periódica para no saturar el buffer UDP del SO
                if (chunkIndex % LOTE_SIZE == 0) {
                    Thread.sleep(PAUSA_LOTE_MS);
                }

                if (chunkIndex % 500 == 0 || chunkIndex == totalChunks) {
                    int pct = (int) (chunkIndex * 100L / totalChunks);
                    System.out.printf("\r  Enviando   : %3d%%  (%d/%d chunks)", pct, chunkIndex, totalChunks);
                }
            }
        }
        long elapsed = System.currentTimeMillis() - t0;
        double mb   = tamanio / (1024.0 * 1024.0);
        double mbps = elapsed > 0 ? mb / (elapsed / 1000.0) : 0;
        System.out.printf("%n  Disparado  : %d ms  |  %.1f MB/s%n", elapsed, mbps);

        // ── END ──────────────────────────────────────────────────────────────
        Map<String, Object> endData = new LinkedHashMap<>();
        endData.put("uploadId", uploadId);
        Map<String, Object> endReq = new LinkedHashMap<>();
        endReq.put("type", "ARCHIVO"); endReq.put("method", "POST_CHUNK_END");
        endReq.put("data", endData);
        System.out.println("  Finalizando (esperando confirmacion del servidor)...");
        // Para POST_CHUNK_END sí esperamos respuesta (confirma que el servidor ensambló)
        socket.setSoTimeout(END_TIMEOUT_MS); // el servidor puede tardar en encriptar/persistir
        imprimirConfirmacion("ENVIO ARCHIVO (UDP puro)", enviarRequest(endReq));
        socket.setSoTimeout(TIMEOUT_MS);
    }

    private static int leerCompleto(InputStream is, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = is.read(buf, total, buf.length - total);
            if (n == -1) break;
            total += n;
        }
        return total;
    }

    // ── Descarga ─────────────────────────────────────────────────────────────

    public void obtenerYDescargarDocumento(long id, Scanner scanner) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "ARCHIVO"); req.put("method", "GET");
        req.put("data", Map.of("id", id, "accion", "NORMAL"));
        System.out.println("\n  ── DOCUMENTO NORMAL ────────────────────────────");
        try {
            List<String> lineas = enviarYRecibirLineas(req);
            if (lineas.isEmpty()) { System.out.println("  [ERROR] Respuesta vacia."); return; }
            @SuppressWarnings("unchecked")
            Map<String, Object> cab = mapper.readValue(lineas.get(0), Map.class);
            if (!"OK".equals(cab.get("status"))) { System.out.println("  ERROR: " + cab.get("message")); return; }
            int    total  = ((Number) cab.get("totalChunks")).intValue();
            String nombre = (String) cab.getOrDefault("nombreVisible", "documento");
            String hash   = (String) cab.getOrDefault("hash", "");
            System.out.println("  Nombre: " + nombre);
            System.out.println("  Hash  : " + (hash.isEmpty() ? "(sin hash)" : hash));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 1; i <= total; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cm = mapper.readValue(lineas.get(i), Map.class);
                if (!"CHUNK".equals(cm.get("status"))) { System.out.println("  [ERROR] chunk " + i); break; }
                baos.write(Base64.getDecoder().decode((String) cm.get("datos")));
            }
            byte[] contenido = baos.toByteArray();
            String def = DOWNLOAD_DIR + File.separator + nombre;
            System.out.print("  Ruta destino [Enter = " + def + "]: ");
            String destino = scanner.nextLine().trim();
            if (destino.isEmpty()) destino = def;
            File dest = new File(destino);
            if (dest.isDirectory()) dest = new File(dest, nombre);
            dest.getParentFile().mkdirs();
            Files.write(dest.toPath(), contenido);
            System.out.println("  Guardado en: " + dest.getAbsolutePath());
        } catch (Exception e) { System.out.println("  [ERROR] " + e.getMessage()); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void obtenerConHash(long id) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "ARCHIVO"); req.put("method", "GET");
        req.put("data", Map.of("id", id, "accion", "SOLO_HASH"));
        String resp = enviarRequest(req);
        System.out.println("\n  ── DOCUMENTO CON HASH ──────────────────────────");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(resp, Map.class);
            if (!"OK".equals(map.get("status"))) { System.out.println("  ERROR: " + map.get("message")); return; }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) map.get("data");
            System.out.println("  ID     : " + data.getOrDefault("id", "?"));
            System.out.println("  Nombre : " + data.getOrDefault("nombreVisible", "?"));
            System.out.println("  Hash   : " + data.getOrDefault("hash", "(sin hash)"));
        } catch (Exception e) { System.out.println(resp); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void obtenerYDescargarEncriptado(long id, Scanner scanner) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "ARCHIVO"); req.put("method", "GET");
        req.put("data", Map.of("id", id, "accion", "ENCRIPTADO"));
        System.out.println("\n  ── DOCUMENTO ENCRIPTADO ────────────────────────");
        try {
            List<String> lineas = enviarYRecibirLineas(req);
            if (lineas.isEmpty()) { System.out.println("  [ERROR] Respuesta vacia."); return; }
            @SuppressWarnings("unchecked")
            Map<String, Object> cab = mapper.readValue(lineas.get(0), Map.class);
            if (!"OK".equals(cab.get("status"))) { System.out.println("  ERROR: " + cab.get("message")); return; }
            int    total  = ((Number) cab.get("totalChunks")).intValue();
            String nombre = (String) cab.getOrDefault("nombreVisible", "documento");
            String hash   = (String) cab.getOrDefault("hash", "");
            System.out.println("  Nombre: " + nombre + ".enc");
            System.out.println("  Hash  : " + (hash.isEmpty() ? "(sin hash)" : hash));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 1; i <= total; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cm = mapper.readValue(lineas.get(i), Map.class);
                if (!"CHUNK".equals(cm.get("status"))) { System.out.println("  [ERROR] chunk " + i); break; }
                baos.write(Base64.getDecoder().decode((String) cm.get("datos")));
            }
            byte[] contenido = baos.toByteArray();
            String def = DOWNLOAD_DIR + File.separator + nombre + ".enc";
            System.out.print("  Ruta destino [Enter = " + def + "]: ");
            String destino = scanner.nextLine().trim();
            if (destino.isEmpty()) destino = def;
            File dest = new File(destino);
            if (dest.isDirectory()) dest = new File(dest, nombre + ".enc");
            dest.getParentFile().mkdirs();
            Files.write(dest.toPath(), contenido);
            System.out.println("  Guardado en: " + dest.getAbsolutePath());
        } catch (Exception e) { System.out.println("  [ERROR] " + e.getMessage()); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void verLogs() throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "LOG"); req.put("method", "LIST"); req.put("data", Map.of());
        System.out.println("\n  ── INFORME DE LOGS ─────────────────────────────");
        try {
            List<String> lineas = enviarYRecibirLineas(req);
            if (lineas.isEmpty()) { System.out.println("  (sin logs)"); return; }
            // Línea 0: cabecera {"status":"OK","totalLogs":N}
            @SuppressWarnings("unchecked")
            Map<String, Object> cab = mapper.readValue(lineas.get(0), Map.class);
            int total = cab.containsKey("totalLogs") ? ((Number) cab.get("totalLogs")).intValue() : lineas.size() - 1;
            System.out.printf("  Total registros: %d%n", total);
            System.out.println("  ─────────────────────────────────────────────────");
            for (int i = 1; i < lineas.size(); i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = mapper.readValue(lineas.get(i), Map.class);
                String usuario  = entry.getOrDefault("usuario",   "").toString();
                String tipo     = entry.getOrDefault("tipo",      "").toString();
                String mensaje  = entry.getOrDefault("mensaje",   "").toString();
                String fechaHora = entry.getOrDefault("fechaHora", "").toString();
                System.out.printf("  [%s] %-10s %-12s %s%n",
                        fechaHora.length() > 19 ? fechaHora.substring(0, 19) : fechaHora,
                        tipo, usuario.isEmpty() ? "(sistema)" : usuario, mensaje);
            }
        } catch (Exception e) { System.out.println("  [ERROR] " + e.getMessage()); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    // ── Menú ─────────────────────────────────────────────────────────────────

    public void menuInteractivo() throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║   CLIENTE UDP PURO — MÁXIMA VELOCIDAD        ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.print("  Ingrese su nombre de usuario: ");
        registrarUsuario(scanner.nextLine().trim());
        boolean salir = false;
        while (!salir) {
            System.out.println("\n┌─── MENÚ ────────────────────────────────────┐");
            System.out.println("│  1. Listar clientes conectados              │");
            System.out.println("│  2. Listar documentos disponibles           │");
            System.out.println("│  3. Enviar mensaje                          │");
            System.out.println("│  4. Enviar archivo  [UDP puro, sin ACK]     │");
            System.out.println("│  5. Obtener documento normal                │");
            System.out.println("│  6. Obtener documento con hash              │");
            System.out.println("│  7. Obtener documento encriptado            │");
            System.out.println("│  8. Ver informe de logs                     │");
            System.out.println("│  ─── Emociones ────────────────────────────│");
            System.out.println("│  9.  Identificar emoción de imagen (nueva) │");
            System.out.println("│  10. Listar imágenes de rostro             │");
            System.out.println("│  11. Identificar emoción por ID de imagen  │");
            System.out.println("│  0. Salir                                   │");
            System.out.println("└─────────────────────────────────────────────┘");
            System.out.print("  Opcion: ");
            switch (scanner.nextLine().trim()) {
                case "1" -> listarConectados();
                case "2" -> listarDocumentos();
                case "3" -> {
                    System.out.print("  Asunto: ");    String asunto = scanner.nextLine().trim();
                    System.out.print("  Contenido: "); String cont   = scanner.nextLine().trim();
                    enviarMensaje(asunto, cont);
                }
                case "4" -> { System.out.print("  Ruta del archivo: "); enviarArchivo(scanner.nextLine().trim()); }
                case "5" -> { System.out.print("  ID del documento: "); obtenerYDescargarDocumento(Long.parseLong(scanner.nextLine().trim()), scanner); }
                case "6" -> { System.out.print("  ID del documento: "); obtenerConHash(Long.parseLong(scanner.nextLine().trim())); }
                case "7" -> { System.out.print("  ID del documento: "); obtenerYDescargarEncriptado(Long.parseLong(scanner.nextLine().trim()), scanner); }
                case "8" -> verLogs();
                case "9" -> {
                    System.out.print("  Ruta de la imagen de rostro: ");
                    identificarEmocion(scanner.nextLine().trim());
                }
                case "10" -> listarImagenesRostro();
                case "11" -> {
                    System.out.print("  ID de la imagen de rostro: ");
                    identificarEmocionPorId(Long.parseLong(scanner.nextLine().trim()));
                }
                case "0" -> salir = true;
                default  -> System.out.println("  Opcion invalida.");
            }
        }
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "USUARIO"); req.put("method", "DELETE");
            req.put("data", Map.of("nombre", nombreUsuario));
            enviarRequest(req);
        } catch (Exception ignored) {}
        socket.close();
        System.out.println("  [UDP] Sesion finalizada.");
    }

    // ── Emociones ─────────────────────────────────────────────────────────────

    public void identificarEmocion(String rutaImagen) throws Exception {
        File file = new File(rutaImagen);
        if (!file.exists()) {
            System.out.println("  [ERROR] Archivo no encontrado: " + rutaImagen);
            return;
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String nombre = file.getName();
        int dot = nombre.lastIndexOf('.');
        String nombreSinExt = dot >= 0 ? nombre.substring(0, dot) : nombre;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("imagenBase64", base64);
        data.put("nombre", nombreSinExt);
        data.put("usuarioNombre", nombreUsuario);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "EMOCION"); req.put("method", "POST"); req.put("data", data);
        imprimirRespuesta("IDENTIFICAR EMOCIÓN (UDP)", enviarRequest(req));
    }

    public void listarImagenesRostro() throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "EMOCION"); req.put("method", "LIST"); req.put("data", Map.of());
        String resp = enviarRequest(req);
        System.out.println("\n  ── IMÁGENES DE ROSTRO ─────────────────────────");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(resp, Map.class);
            Object dataObj = map.get("data");
            if (dataObj instanceof Map<?, ?> dataMap) {
                Object imagenesObj = dataMap.get("imagenes");
                if (imagenesObj instanceof List<?> lista) {
                    if (lista.isEmpty()) {
                        System.out.println("  (sin imágenes de rostro)");
                    } else {
                        System.out.printf("  %-6s  %-20s  %-8s  %-10s%n",
                                "ID", "NOMBRE", "EXT", "TAMAÑO");
                        System.out.println("  ──────  ────────────────────  ────────  ──────────");
                        for (Object item : lista) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> img = (Map<String, Object>) item;
                            System.out.printf("  %-6s  %-20s  %-8s  %s%n",
                                    img.getOrDefault("id", "?"),
                                    truncar(img.getOrDefault("nombre", "?").toString(), 20),
                                    img.getOrDefault("extension", "?"),
                                    img.getOrDefault("tamanio", "?"));
                        }
                    }
                } else { System.out.println(resp); }
            } else { System.out.println(resp); }
        } catch (Exception e) { System.out.println(resp); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void identificarEmocionPorId(long id) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "EMOCION"); req.put("method", "GET");
        req.put("data", Map.of("id", id));
        imprimirRespuesta("IDENTIFICAR EMOCIÓN POR ID (UDP)", enviarRequest(req));
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private void imprimirRespuesta(String titulo, String json) {
        System.out.println("\n  ── " + titulo + " ──────────────────────────────");
        try {
            Object obj = mapper.readValue(json, Object.class);
            System.out.println(truncarBase64(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)));
        } catch (Exception e) { System.out.println(json); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    private void imprimirConfirmacion(String titulo, String json) {
        System.out.println("\n  ── " + titulo + " ──────────────────────────────");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(json, Map.class);
            String status = (String) map.getOrDefault("status", "?");
            if ("OK".equalsIgnoreCase(status)) System.out.println("  OK — recibido");
            else System.out.println("  ERROR: " + map.getOrDefault("message", json));
        } catch (Exception e) { System.out.println("  " + json); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    private static String truncar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String truncarBase64(String pretty) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < pretty.length()) {
            if (pretty.charAt(i) == '"') {
                int start = i + 1, end = pretty.indexOf('"', start);
                while (end > 0 && pretty.charAt(end - 1) == '\\') end = pretty.indexOf('"', end + 1);
                if (end < 0) { sb.append(pretty.charAt(i)); i++; continue; }
                String valor = pretty.substring(start, end);
                if (valor.length() > 100 && valor.matches("[A-Za-z0-9+/=]+"))
                    sb.append('"').append(valor, 0, 80).append("... [base64, ").append(valor.length()).append(" chars]\"");
                else sb.append('"').append(valor).append('"');
                i = end + 1;
            } else { sb.append(pretty.charAt(i)); i++; }
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : HOST_DEFAULT;
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : PORT_DEFAULT;
        new ClienteUDP(host, port).menuInteractivo();
    }
}
