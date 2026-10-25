package Cliente_Servidor.V1.Cliente;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;

public class ClienteTCP {

    private static final String HOST_DEFAULT  = "localhost";
    private static final int    PORT_DEFAULT  = 5000;
    private static final int    CHUNK_SIZE    = 4 * 1024 * 1024;
    private static final long   UMBRAL_CHUNKS = 4L * 1024 * 1024;

    /** Carpeta local donde se guardan los documentos recibidos por defecto. */
    private static final String DOWNLOAD_DIR = "documentos_recibidos";

    private final String       host;
    private final int          port;
    private final ObjectMapper mapper;   // inicializado en constructor con límite de string extendido

    private Socket         socket;
    private BufferedReader in;
    private PrintWriter    out;
    private String         nombreUsuario;

    public ClienteTCP(String host, int port) throws IOException {
        this.host   = host;
        this.port   = port;
        // Configurar Jackson sin límite de longitud de string (evita error con archivos > 20 MB)
        this.mapper = new ObjectMapper();
        this.mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build());
        // Crear carpeta de descarga si no existe
        Files.createDirectories(Paths.get(DOWNLOAD_DIR));
    }

    public void conectar() throws IOException {
        socket = new Socket(host, port);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        log("Conectado al servidor " + host + ":" + port);
    }

    public void cerrar() {
        try { if (socket != null) socket.close(); log("Conexión cerrada."); }
        catch (IOException e) { System.err.println("Error al cerrar: " + e.getMessage()); }
    }

    private String enviarRequest(Map<String, Object> request) throws IOException {
        out.println(mapper.writeValueAsString(request));
        return in.readLine();
    }

    // ── Operaciones ───────────────────────────────────────────────────────────

    public void registrarUsuario(String nombre) throws IOException {
        this.nombreUsuario = nombre;
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "USUARIO"); req.put("method", "POST");
        req.put("data", Map.of("nombre", nombre, "tipoUsuario", "NORMAL"));
        imprimirRespuesta("REGISTRO DE USUARIO", enviarRequest(req));
    }

    public void listarConectados() throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "USUARIO"); req.put("method", "LIST"); req.put("data", Map.of());
        imprimirRespuesta("CLIENTES CONECTADOS", enviarRequest(req));
    }

    public void listarDocumentos() throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "ARCHIVO"); req.put("method", "LIST"); req.put("data", Map.of());
        String resp = enviarRequest(req);

        System.out.println("\n  ── ARCHIVOS DISPONIBLES ────────────────────────");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(resp, Map.class);
            Object data = map.get("data");
            if (data instanceof List<?> lista) {
                if (lista.isEmpty()) {
                    System.out.println("  (sin archivos)");
                } else {
                    System.out.printf("  %-6s  %-25s  %-8s  %-12s%n",
                            "ID", "NOMBRE", "ORIGEN", "DESTINATARIO");
                    System.out.println("  ──────  ─────────────────────────  ────────  ────────────");
                    for (Object item : lista) {
                        if (item instanceof Map<?, ?> docRaw) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> doc = (Map<String, Object>) docRaw;
                            System.out.printf("  %-6s  %-25s  %-8s  %s%n",
                                    doc.getOrDefault("id", "?"),
                                    truncar(doc.getOrDefault("nombreVisible",
                                            doc.getOrDefault("nombre", "?")).toString(), 25),
                                    doc.getOrDefault("tipoPropietario", "?"),
                                    doc.getOrDefault("destinatario", "TODOS"));
                        }
                    }
                }
            } else {
                System.out.println(resp);
            }
        } catch (Exception e) {
            System.out.println(resp);
        }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void enviarMensaje(String asunto, String contenido, String destinatario) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tipo", "MENSAJE"); data.put("asunto", asunto);
        data.put("contenido", contenido); data.put("tipoPropietario", "LOCAL");
        data.put("usuarioNombre", nombreUsuario); data.put("usuarioId", 0);
        if (destinatario != null && !destinatario.isBlank()
                && !destinatario.equalsIgnoreCase("TODOS")) {
            data.put("destinatario", destinatario);
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "MENSAJE"); req.put("method", "POST"); req.put("data", data);
        imprimirRespuesta("ENVÍO MENSAJE", enviarRequest(req));
    }

    public void enviarArchivo(String rutaArchivo, String destinatario) throws IOException {
        File file = new File(rutaArchivo);
        if (!file.exists()) { System.out.println("  [ERROR] Archivo no encontrado: " + rutaArchivo); return; }
        if (file.length() > UMBRAL_CHUNKS) enviarArchivoChunked(file, destinatario);
        else enviarArchivoDirecto(file, destinatario);
    }

    private void enviarArchivoDirecto(File file, String destinatario) throws IOException {
        byte[] bytes  = Files.readAllBytes(file.toPath());
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String nombre = file.getName();
        int dot = nombre.lastIndexOf('.');
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tipo", "ARCHIVO");
        data.put("nombre",    dot >= 0 ? nombre.substring(0, dot) : nombre);
        data.put("extension", dot >= 0 ? nombre.substring(dot + 1) : "bin");
        data.put("tamanio", bytes.length); data.put("contenido", base64);
        data.put("tipoPropietario", "LOCAL");
        data.put("usuarioNombre", nombreUsuario); data.put("usuarioId", 0);
        if (destinatario != null && !destinatario.isBlank()
                && !destinatario.equalsIgnoreCase("TODOS")) {
            data.put("destinatario", destinatario);
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "ARCHIVO"); req.put("method", "POST"); req.put("data", data);
        System.out.println("  Enviando " + file.getName() + " (" + bytes.length + " bytes)...");
        imprimirRespuesta("ENVÍO ARCHIVO", enviarRequest(req));
    }

    private void enviarArchivoChunked(File file, String destinatario) throws IOException {
        String nombre = file.getName();
        int dot = nombre.lastIndexOf('.');
        String ext = dot >= 0 ? nombre.substring(dot + 1) : "bin";
        String nom = dot >= 0 ? nombre.substring(0, dot) : nombre;
        long tamanio = file.length();
        int totalChunks = (int) Math.ceil((double) tamanio / CHUNK_SIZE);
        System.out.printf("  Enviando archivo grande: %s (%,d bytes) en %d chunks%n",
                file.getName(), tamanio, totalChunks);

        Map<String, Object> initData = new LinkedHashMap<>();
        initData.put("nombre", nom); initData.put("extension", ext);
        initData.put("tamanio", tamanio); initData.put("totalChunks", totalChunks);
        initData.put("tipoPropietario", "LOCAL");
        initData.put("usuarioNombre", nombreUsuario); initData.put("usuarioId", 0);
        if (destinatario != null && !destinatario.isBlank()
                && !destinatario.equalsIgnoreCase("TODOS")) {
            initData.put("destinatario", destinatario);
        }
        Map<String, Object> initReq = new LinkedHashMap<>();
        initReq.put("type", "ARCHIVO"); initReq.put("method", "POST_CHUNK_INIT");
        initReq.put("data", initData);

        String initResp = enviarRequest(initReq);
        @SuppressWarnings("unchecked")
        Map<String, Object> initMap = mapper.readValue(initResp, Map.class);
        if (!"OK".equals(initMap.get("status"))) { System.out.println("  [ERROR] " + initResp); return; }
        @SuppressWarnings("unchecked")
        String uploadId = (String) ((Map<String, Object>) initMap.get("data")).get("uploadId");
        System.out.println("  Upload iniciado. uploadId=" + uploadId);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[CHUNK_SIZE]; int chunkIndex = 0, leido;
            while ((leido = fis.read(buf)) != -1) {
                String chunkB64 = Base64.getEncoder().encodeToString(Arrays.copyOf(buf, leido));
                Map<String, Object> chunkData = new LinkedHashMap<>();
                chunkData.put("uploadId", uploadId); chunkData.put("chunkIndex", chunkIndex);
                chunkData.put("totalChunks", totalChunks); chunkData.put("contenido", chunkB64);
                Map<String, Object> chunkReq = new LinkedHashMap<>();
                chunkReq.put("type", "ARCHIVO"); chunkReq.put("method", "POST_CHUNK");
                chunkReq.put("data", chunkData);
                String chunkResp = enviarRequest(chunkReq);
                @SuppressWarnings("unchecked")
                Map<String, Object> chunkMap = mapper.readValue(chunkResp, Map.class);
                if (!"OK".equals(chunkMap.get("status"))) {
                    System.out.println("  [ERROR] chunk " + chunkIndex + ": " + chunkResp); return;
                }
                chunkIndex++;
                int pct = (int) (chunkIndex * 100L / totalChunks);
                System.out.printf("\r  Progreso: %3d%% (%d/%d chunks)", pct, chunkIndex, totalChunks);
            }
        }
        System.out.println();

        Map<String, Object> endData = new LinkedHashMap<>();
        endData.put("uploadId", uploadId);
        Map<String, Object> endReq = new LinkedHashMap<>();
        endReq.put("type", "ARCHIVO"); endReq.put("method", "POST_CHUNK_END");
        endReq.put("data", endData);
        System.out.println("  Finalizando transferencia...");
        imprimirRespuesta("ENVÍO ARCHIVO (chunked)", enviarRequest(endReq));
    }

    /**
     * Opción 5 — Recibe el documento original en chunks desde archivo_chunks_original.
     * Protocolo:
     *   Línea 1 : {"status":"OK","totalChunks":N,"nombreVisible":"...","hash":"..."}
     *   Líneas  : {"status":"CHUNK","numero":i,"datos":"<base64>"}  × N
     *   Última  : {"status":"FIN"}
     *
     * TCP envía línea a línea con out.println(), cada readLine() trae exactamente
     * un JSON. El fix de StreamReadConstraints aplica porque Jackson parsea el campo
     * "datos" (string base64) que en chunks grandes puede superar 20 MB.
     */
    public void obtenerYDescargarDocumento(long id, Scanner scanner) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "ARCHIVO"); req.put("method", "GET");
        req.put("data", Map.of("id", id, "accion", "NORMAL"));
        out.println(mapper.writeValueAsString(req));

        System.out.println("\n  ── DOCUMENTO NORMAL ────────────────────────────");
        try {
            String cabLine = in.readLine();
            @SuppressWarnings("unchecked")
            Map<String, Object> cabecera = mapper.readValue(cabLine, Map.class);

            if (!"OK".equals(cabecera.get("status"))) {
                System.out.println("  ERROR: " + cabecera.get("message"));
                System.out.println("  ─────────────────────────────────────────────────");
                return;
            }

            int    totalChunks   = ((Number) cabecera.get("totalChunks")).intValue();
            String nombreVisible = (String) cabecera.getOrDefault("nombreVisible", "documento");
            String hash          = (String) cabecera.getOrDefault("hash", "");

            System.out.println("  Nombre      : " + nombreVisible);
            System.out.println("  Hash        : " + (hash.isEmpty() ? "(sin hash)" : hash));
            System.out.println("  Total chunks: " + totalChunks);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                String chunkLine = in.readLine();
                @SuppressWarnings("unchecked")
                Map<String, Object> chunkMap = mapper.readValue(chunkLine, Map.class);
                if (!"CHUNK".equals(chunkMap.get("status"))) {
                    System.out.println("  [ERROR] chunk inesperado: " + chunkLine); break;
                }
                byte[] datos = Base64.getDecoder().decode((String) chunkMap.get("datos"));
                baos.write(datos);
                int pct = (int) ((i + 1) * 100L / totalChunks);
                System.out.printf("\r  Recibiendo: %3d%% (%d/%d chunks, %,d bytes)",
                        pct, i + 1, totalChunks, baos.size());
            }
            System.out.println();

            in.readLine(); // FIN

            byte[] contenido = baos.toByteArray();
            System.out.printf("  Tamaño total: %,d bytes%n", contenido.length);

            String defaultPath = DOWNLOAD_DIR + File.separator + nombreVisible;
            System.out.print("  Ruta destino [Enter = " + defaultPath + "]: ");
            String destino = scanner.nextLine().trim();
            if (destino.isEmpty()) destino = defaultPath;

            File destFile = new File(destino);
            if (destFile.isDirectory()) destFile = new File(destFile, nombreVisible);
            destFile.getParentFile().mkdirs();
            Files.write(destFile.toPath(), contenido);
            System.out.println("  Guardado en: " + destFile.getAbsolutePath());

        } catch (Exception e) {
            System.out.println("  [ERROR] " + e.getMessage());
        }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    /**
     * Opción 6 — Obtener solo metadatos (id, nombre, hash).
     */
    public void obtenerConHash(long id) throws IOException {
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
        } catch (Exception e) {
            System.out.println(resp);
        }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    /**
     * Opción 7 — Recibe el documento encriptado en chunks.
     * Mismo protocolo multilinea que obtenerYDescargarDocumento.
     */
    public void obtenerYDescargarEncriptado(long id, Scanner scanner) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "ARCHIVO"); req.put("method", "GET");
        req.put("data", Map.of("id", id, "accion", "ENCRIPTADO"));
        out.println(mapper.writeValueAsString(req));

        System.out.println("\n  ── DOCUMENTO ENCRIPTADO ────────────────────────");
        try {
            String cabLine = in.readLine();
            @SuppressWarnings("unchecked")
            Map<String, Object> cabecera = mapper.readValue(cabLine, Map.class);

            if (!"OK".equals(cabecera.get("status"))) {
                System.out.println("  ERROR: " + cabecera.get("message"));
                System.out.println("  ─────────────────────────────────────────────────");
                return;
            }

            int    totalChunks   = ((Number) cabecera.get("totalChunks")).intValue();
            String nombreVisible = (String) cabecera.getOrDefault("nombreVisible", "documento");
            String hash          = (String) cabecera.getOrDefault("hash", "");

            System.out.println("  Nombre      : " + nombreVisible + ".enc");
            System.out.println("  Hash orig.  : " + (hash.isEmpty() ? "(sin hash)" : hash));
            System.out.println("  Total chunks: " + totalChunks);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                String chunkLine = in.readLine();
                @SuppressWarnings("unchecked")
                Map<String, Object> chunkMap = mapper.readValue(chunkLine, Map.class);
                if (!"CHUNK".equals(chunkMap.get("status"))) {
                    System.out.println("  [ERROR] chunk inesperado: " + chunkLine); break;
                }
                byte[] datos = Base64.getDecoder().decode((String) chunkMap.get("datos"));
                baos.write(datos);
                int pct = (int) ((i + 1) * 100L / totalChunks);
                System.out.printf("\r  Recibiendo: %3d%% (%d/%d chunks, %,d bytes)",
                        pct, i + 1, totalChunks, baos.size());
            }
            System.out.println();

            in.readLine(); // FIN

            byte[] contenido = baos.toByteArray();
            System.out.printf("  Tamaño total: %,d bytes%n", contenido.length);

            String defaultPath = DOWNLOAD_DIR + File.separator + nombreVisible + ".enc";
            System.out.print("  Ruta destino [Enter = " + defaultPath + "]: ");
            String destino = scanner.nextLine().trim();
            if (destino.isEmpty()) destino = defaultPath;

            File destFile = new File(destino);
            if (destFile.isDirectory()) destFile = new File(destFile, nombreVisible + ".enc");
            destFile.getParentFile().mkdirs();
            Files.write(destFile.toPath(), contenido);
            System.out.println("  Guardado en: " + destFile.getAbsolutePath());

        } catch (Exception e) {
            System.out.println("  [ERROR] " + e.getMessage());
        }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void verLogs() throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "LOG"); req.put("method", "LIST"); req.put("data", Map.of());
        out.println(mapper.writeValueAsString(req));

        System.out.println("\n  ── INFORME DE LOGS ─────────────────────────────");
        try {
            String cabLine = in.readLine();
            @SuppressWarnings("unchecked")
            Map<String, Object> cab = mapper.readValue(cabLine, Map.class);
            if (!"OK".equals(cab.get("status"))) {
                System.out.println("  ERROR: " + cab.get("message"));
                System.out.println("  ─────────────────────────────────────────────────");
                return;
            }
            int total = cab.containsKey("totalLogs") ? ((Number) cab.get("totalLogs")).intValue() : 0;
            System.out.printf("  Total registros: %d%n", total);
            System.out.println("  ─────────────────────────────────────────────────");
            String linea;
            while ((linea = in.readLine()) != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = mapper.readValue(linea, Map.class);
                String status = entry.getOrDefault("status", "").toString();
                if ("FIN".equals(status)) break;
                String usuario   = entry.getOrDefault("usuario",   "").toString();
                String tipo      = entry.getOrDefault("tipo",      "").toString();
                String mensaje   = entry.getOrDefault("mensaje",   "").toString();
                String fechaHora = entry.getOrDefault("fechaHora", "").toString();
                System.out.printf("  [%s] %-10s %-12s %s%n",
                        fechaHora.length() > 19 ? fechaHora.substring(0, 19) : fechaHora,
                        tipo, usuario.isEmpty() ? "(sistema)" : usuario, mensaje);
            }
        } catch (Exception e) {
            System.out.println("  [ERROR] " + e.getMessage());
        }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    // ── Mensajes ──────────────────────────────────────────────────────────────

    public void listarMensajes() throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "MENSAJE"); req.put("method", "LIST"); req.put("data", Map.of());
        String resp = enviarRequest(req);

        System.out.println("\n  ── MENSAJES DISPONIBLES ────────────────────────");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(resp, Map.class);
            Object data = map.get("data");
            if (data instanceof List<?> lista) {
                if (lista.isEmpty()) {
                    System.out.println("  (sin mensajes)");
                } else {
                    System.out.printf("  %-6s  %-20s  %-15s  %s%n",
                            "ID", "ASUNTO", "DESTINATARIO", "SERVIDOR");
                    System.out.println("  ──────  ────────────────────  ───────────────  ─────────");
                    for (Object item : lista) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> doc = (Map<String, Object>) item;
                        System.out.printf("  %-6s  %-20s  %-15s  %s%n",
                                doc.getOrDefault("id", "?"),
                                truncar(doc.getOrDefault("asunto", "?").toString(), 20),
                                doc.getOrDefault("destinatario", "TODOS"),
                                doc.getOrDefault("tipoPropietario", "?")
                                + (doc.containsKey("propietarioIp")
                                        ? " (" + doc.get("propietarioIp") + ")" : ""));
                    }
                }
            } else { System.out.println(resp); }
        } catch (Exception e) { System.out.println(resp); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void obtenerMensajePorId(long id) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "MENSAJE"); req.put("method", "GET");
        req.put("data", Map.of("id", id));
        String resp = enviarRequest(req);

        System.out.println("\n  ── MENSAJE ID=" + id + " ─────────────────────────");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(resp, Map.class);
            if (!"OK".equals(map.get("status"))) {
                System.out.println("  ERROR: " + map.get("message"));
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> d = (Map<String, Object>) map.get("data");
                System.out.println("  ID          : " + d.getOrDefault("id", "?"));
                System.out.println("  Asunto      : " + d.getOrDefault("asunto", "?"));
                System.out.println("  Destinatario: " + d.getOrDefault("destinatario", "TODOS"));
                System.out.println("  Hash        : " + d.getOrDefault("hash", "(sin hash)"));
                System.out.println("  Contenido   : " + d.getOrDefault("contenido", ""));
            }
        } catch (Exception e) { System.out.println(resp); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    // ── Emociones ─────────────────────────────────────────────────────────────

    public void identificarEmocion(String rutaImagen) throws IOException {
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
        imprimirRespuesta("IDENTIFICAR EMOCIÓN", enviarRequest(req));
    }

    public void listarImagenesRostro() throws IOException {
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
                } else {
                    System.out.println(resp);
                }
            } else {
                System.out.println(resp);
            }
        } catch (Exception e) {
            System.out.println(resp);
        }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void identificarEmocionPorId(long id) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "EMOCION"); req.put("method", "GET");
        req.put("data", Map.of("id", id));
        imprimirRespuesta("IDENTIFICAR EMOCIÓN POR ID", enviarRequest(req));
    }

    // ── Distribuido: servidores ────────────────────────────────────────────────

    public void listarServidores() throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "SERVIDOR"); req.put("method", "LIST"); req.put("data", Map.of());
        String resp = enviarRequest(req);

        System.out.println("\n  ── SERVIDORES AMIGOS ───────────────────────────");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(resp, Map.class);
            Object data = map.get("data");
            if (data instanceof List<?> lista) {
                if (lista.isEmpty()) {
                    System.out.println("  (ningún servidor conocido)");
                } else {
                    System.out.printf("  %-20s  %-12s  %-6s  %s%n",
                            "NOMBRE", "ID", "ESTADO", "CLIENTES");
                    System.out.println("  ────────────────────  ────────────  ──────  ─────────");
                    for (Object item : lista) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> s = (Map<String, Object>) item;
                        boolean conectado = Boolean.TRUE.equals(s.get("conectado"));
                        @SuppressWarnings("unchecked")
                        List<String> clientes = (List<String>) s.getOrDefault("clientesConectados", List.of());
                        System.out.printf("  %-20s  %-12s  %-6s  %s%n",
                                truncar(s.getOrDefault("nombre", "?").toString(), 20),
                                s.getOrDefault("id", "?"),
                                conectado ? "CONECTADO" : "DESCONECTADO",
                                clientes.isEmpty() ? "(ninguno)" : String.join(", ", clientes));
                    }
                }
            } else { System.out.println(resp); }
        } catch (Exception e) { System.out.println(resp); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void verInfoServidor(String servidorId) throws IOException {
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("subtipo", "INFO");
        if (servidorId != null && !servidorId.isBlank()) dataMap.put("servidorId", servidorId);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "SERVIDOR"); req.put("method", "GET"); req.put("data", dataMap);
        String resp = enviarRequest(req);

        String titulo = (servidorId == null || servidorId.isBlank())
                ? "INFO SERVIDOR LOCAL" : "INFO SERVIDOR " + servidorId;
        System.out.println("\n  ── " + titulo + " ──────────────────────────");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(resp, Map.class);
            if (!"OK".equals(map.get("status"))) {
                System.out.println("  ERROR: " + map.get("message"));
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> d = (Map<String, Object>) map.get("data");
                d.forEach((k, v) -> System.out.printf("  %-25s: %s%n", k, v));
            }
        } catch (Exception e) { System.out.println(resp); }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    public void verLogsServidor(String servidorId) throws IOException {
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("subtipo", "LOG");
        if (servidorId != null && !servidorId.isBlank()) dataMap.put("servidorId", servidorId);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type", "SERVIDOR"); req.put("method", "GET"); req.put("data", dataMap);
        out.println(mapper.writeValueAsString(req));

        String titulo = (servidorId == null || servidorId.isBlank())
                ? "LOGS SERVIDOR LOCAL" : "LOGS SERVIDOR " + servidorId;
        System.out.println("\n  ── " + titulo + " ─────────────────────────");

        // Protocolo idéntico al verLogs() existente
        try {
            String cabLine = in.readLine();
            @SuppressWarnings("unchecked")
            Map<String, Object> cab = mapper.readValue(cabLine, Map.class);
            if (!"OK".equals(cab.get("status"))) {
                System.out.println("  ERROR: " + cab.get("message"));
                System.out.println("  ─────────────────────────────────────────────────");
                return;
            }
            int total = cab.containsKey("totalLogs") ? ((Number) cab.get("totalLogs")).intValue() : 0;
            System.out.printf("  Total registros: %d%n", total);
            System.out.println("  ─────────────────────────────────────────────────");
            String linea;
            while ((linea = in.readLine()) != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = mapper.readValue(linea, Map.class);
                String status = entry.getOrDefault("status", "").toString();
                if ("FIN".equals(status)) break;
                String usuario   = entry.getOrDefault("usuario",   "").toString();
                String tipo      = entry.getOrDefault("tipo",      "").toString();
                String mensaje   = entry.getOrDefault("mensaje",   "").toString();
                String fechaHora = entry.getOrDefault("fechaHora", "").toString();
                System.out.printf("  [%s] %-10s %-12s %s%n",
                        fechaHora.length() > 19 ? fechaHora.substring(0, 19) : fechaHora,
                        tipo, usuario.isEmpty() ? "(sistema)" : usuario, mensaje);
            }
        } catch (Exception e) {
            System.out.println("  [ERROR] " + e.getMessage());
        }
        System.out.println("  ─────────────────────────────────────────────────");
    }

    // ── Menú interactivo ──────────────────────────────────────────────────────

    public void menuInteractivo() throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║     CLIENTE TCP — SISTEMA CLIENTE/SERVIDOR   ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.print("  Ingrese su nombre de usuario: ");
        registrarUsuario(scanner.nextLine().trim());

        boolean salir = false;
        while (!salir) {
            System.out.println("\n┌─── MENÚ ────────────────────────────────────────┐");
            System.out.println("│  1.  Listar clientes conectados (local+peers)   │");
            System.out.println("│  2.  Listar archivos disponibles                │");
            System.out.println("│  3.  Enviar mensaje                             │");
            System.out.println("│  4.  Enviar archivo                             │");
            System.out.println("│  5.  Obtener documento normal                   │");
            System.out.println("│  6.  Obtener documento con hash                 │");
            System.out.println("│  7.  Obtener documento encriptado               │");
            System.out.println("│  8.  Ver logs de este servidor                  │");
            System.out.println("│  9.  Listar mensajes disponibles                │");
            System.out.println("│  10. Obtener mensaje por ID                     │");
            System.out.println("│  11. Listar servidores amigos                   │");
            System.out.println("│  12. Ver información de servidor                │");
            System.out.println("│  13. Ver logs de servidor                       │");
            System.out.println("│  ─── Emociones ─────────────────────────────────│");
            System.out.println("│  14. Identificar emoción de imagen (nueva)      │");
            System.out.println("│  15. Listar imágenes de rostro                  │");
            System.out.println("│  16. Identificar emoción por ID de imagen       │");
            System.out.println("│  0.  Salir                                      │");
            System.out.println("└─────────────────────────────────────────────────┘");
            System.out.print("  Opción: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> listarConectados();
                case "2" -> listarDocumentos();
                case "3" -> {
                    System.out.print("  Asunto: ");
                    String asunto = scanner.nextLine().trim();
                    System.out.print("  Contenido: ");
                    String cont = scanner.nextLine().trim();
                    System.out.print("  Destinatario (TODOS o nombre de usuario): ");
                    String dest3 = scanner.nextLine().trim();
                    enviarMensaje(asunto, cont, dest3.isEmpty() ? "TODOS" : dest3);
                }
                case "4" -> {
                    System.out.print("  Ruta del archivo: ");
                    String ruta = scanner.nextLine().trim();
                    System.out.print("  Destinatario (TODOS o nombre de usuario): ");
                    String dest4 = scanner.nextLine().trim();
                    enviarArchivo(ruta, dest4.isEmpty() ? "TODOS" : dest4);
                }
                case "5" -> {
                    System.out.print("  ID del documento: ");
                    obtenerYDescargarDocumento(Long.parseLong(scanner.nextLine().trim()), scanner);
                }
                case "6" -> {
                    System.out.print("  ID del documento: ");
                    obtenerConHash(Long.parseLong(scanner.nextLine().trim()));
                }
                case "7" -> {
                    System.out.print("  ID del documento: ");
                    obtenerYDescargarEncriptado(Long.parseLong(scanner.nextLine().trim()), scanner);
                }
                case "8" -> verLogs();
                case "9" -> listarMensajes();
                case "10" -> {
                    System.out.print("  ID del mensaje: ");
                    obtenerMensajePorId(Long.parseLong(scanner.nextLine().trim()));
                }
                case "11" -> listarServidores();
                case "12" -> {
                    listarServidores();
                    System.out.print("  ID del servidor (host:puerto) o Enter para este servidor: ");
                    String sid = scanner.nextLine().trim();
                    verInfoServidor(sid.isEmpty() ? null : sid);
                }
                case "13" -> {
                    listarServidores();
                    System.out.print("  ID del servidor (host:puerto) o Enter para este servidor: ");
                    String sid13 = scanner.nextLine().trim();
                    verLogsServidor(sid13.isEmpty() ? null : sid13);
                }
                case "14" -> {
                    System.out.print("  Ruta de la imagen de rostro: ");
                    String ruta14 = scanner.nextLine().trim();
                    identificarEmocion(ruta14);
                }
                case "15" -> listarImagenesRostro();
                case "16" -> {
                    System.out.print("  ID de la imagen de rostro: ");
                    identificarEmocionPorId(Long.parseLong(scanner.nextLine().trim()));
                }
                case "0" -> salir = true;
                default  -> System.out.println("  Opción inválida.");
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void imprimirRespuesta(String titulo, String json) {
        System.out.println("\n  ── " + titulo + " ──────────────────────────────");
        try {
            Object obj = mapper.readValue(json, Object.class);
            System.out.println(truncarBase64(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)));
        } catch (Exception e) { System.out.println(json); }
        System.out.println("  ─────────────────────────────────────────────────");
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

    private void log(String msg) { System.out.println("  [" + new Date() + "] " + msg); }

    private static String truncar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : HOST_DEFAULT;
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : PORT_DEFAULT;
        ClienteTCP cliente = new ClienteTCP(host, port);
        try { cliente.conectar(); cliente.menuInteractivo(); }
        finally { cliente.cerrar(); }
    }
}
