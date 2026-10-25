package Cliente_Servidor.V1.Servicios;

import Cliente_Servidor.V1.Enums.TipoLog;
import Cliente_Servidor.V1.Modelos.ServidorAmigo;
import Cliente_Servidor.V1.Pool.ServidorAmigoPool;
import Cliente_Servidor.V1.Pool.UsuarioConexionPool;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ServidorDistribuidoService {

    @Value("${servidor.nombre:Servidor-Local}")
    private String nombreServidor;

    @Value("${servidor.host:localhost}")
    private String hostServidor;

    @Value("${servidor.puerto:5000}")
    private int puertoServidor;

    @Value("${servidor.amigos:}")
    private String servidoresAmigosConfig;

    private final ServidorAmigoPool servidorPool;
    private final UsuarioConexionPool usuarioPool;
    private final LogService logService;
    private final DocumentoService documentoService;
    private final ImagenRostroService imagenRostroService;
    private final ObjectMapper mapper = new ObjectMapper();

    private final List<String[]> peersConfigurados = new ArrayList<>();
    private final Set<String> syncEnProgreso = java.util.Collections.synchronizedSet(new HashSet<>());
    // idUnicos de documentos que están siendo descargados actualmente (evita duplicados entre peers)
    private final Set<String> descargasEnProgreso = java.util.Collections.synchronizedSet(new HashSet<>());

    public ServidorDistribuidoService(ServidorAmigoPool servidorPool,
                                       UsuarioConexionPool usuarioPool,
                                       LogService logService,
                                       DocumentoService documentoService,
                                       ImagenRostroService imagenRostroService) {
        this.servidorPool = servidorPool;
        this.usuarioPool = usuarioPool;
        this.logService = logService;
        this.documentoService = documentoService;
        this.imagenRostroService = imagenRostroService;
    }

    @PostConstruct
    public void iniciar() {
        parsearPeers();
        // Detectar IP real si el host configurado es localhost y hay peers remotos
        detectarYCorregirHost();
        // El heartbeat arranca siempre: aunque este servidor no tenga peers configurados
        // al inicio, pueden unirse dinámicamente y el heartbeat los mantendrá activos.
        iniciarHeartbeat();
        if (!peersConfigurados.isEmpty()) {
            ConsolaUtil.info("Modo distribuido activo. Peers configurados: " + servidoresAmigosConfig);
            CompletableFuture.runAsync(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                anunciarATodos();
            });
        } else {
            ConsolaUtil.info("Modo standalone (sin peers configurados al inicio).");
        }
    }

    /**
     * Si el servidor está configurado con localhost pero tiene peers remotos,
     * intenta detectar la IP de red real para que los peers remotos puedan conectarse.
     */
    private void detectarYCorregirHost() {
        // Si ya tiene una IP configurada (no localhost), no hacer nada
        if (!esHostLocal(hostServidor)) {
            ConsolaUtil.info("Host configurado: " + hostServidor + ":" + puertoServidor);
            return;
        }

        // Verificar si hay peers remotos (no localhost)
        boolean hayPeersRemotos = peersConfigurados.stream()
                .anyMatch(p -> !esHostLocal(p[0]));

        if (hayPeersRemotos) {
            // Intentar detectar la IP de red
            String ipReal = detectarIpDeRed();
            if (ipReal != null && !esHostLocal(ipReal)) {
                ConsolaUtil.warn("Host configurado como '" + hostServidor + "' pero hay peers remotos.");
                ConsolaUtil.info("Usando IP de red detectada: " + ipReal);
                ConsolaUtil.info("TIP: Configura servidor.host=" + ipReal + " en application.properties");
                hostServidor = ipReal;
            }
        }

        ConsolaUtil.info("Este servidor: " + nombreServidor + " @ " + hostServidor + ":" + puertoServidor);
    }

    /**
     * Detecta la IP de red del servidor (no loopback).
     */
    private String detectarIpDeRed() {
        try {
            // Método 1: Usar la IP que se usaría para conectar a internet
            try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 53);
                String ip = socket.getLocalAddress().getHostAddress();
                if (!esHostLocal(ip)) return ip;
            }
        } catch (Exception ignored) {}

        try {
            // Método 2: Buscar interfaces de red
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;

                java.util.Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private void parsearPeers() {
        if (servidoresAmigosConfig == null || servidoresAmigosConfig.isBlank()) return;
        for (String raw : servidoresAmigosConfig.split(",")) {
            String peer = raw.trim();
            if (peer.isEmpty()) continue;
            String[] parts = peer.split(":");
            if (parts.length == 2) {
                peersConfigurados.add(new String[]{parts[0].trim(), parts[1].trim()});
            }
        }
    }

    /** Anuncia este servidor a los peers de arranque configurados (en paralelo). */
    public void anunciarATodos() {
        for (String[] peer : peersConfigurados) {
            final String host = peer[0];
            final int port;
            try { port = Integer.parseInt(peer[1]); }
            catch (NumberFormatException e) { continue; }
            CompletableFuture.runAsync(() -> anunciarA(host, port));
        }
    }

    /**
     * Abre una conexión TCP corta al peer, envía SERVIDOR/POST con nuestra info
     * y procesa la respuesta.
     * — Gossip pull: si el peer devuelve una lista de peers que no conocemos,
     *   nos anunciamos a ellos también (descubrimiento en cadena).
     */
    void anunciarA(String host, int port) {
        // No conectarse a sí mismo
        if (esMismoServidor(host, port)) return;

        String peerId = host + ":" + port;
        boolean eraConectado = servidorPool.isConectado(peerId);

        try {
            List<String> misClientes = obtenerNombresClientes();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nombre", nombreServidor);
            data.put("host", hostServidor);
            data.put("puerto", puertoServidor);
            data.put("clientesConectados", misClientes);

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "SERVIDOR");
            req.put("method", "POST");
            req.put("data", data);

            try (Socket socket = new Socket(host, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(15_000); // 15s - más tolerante a servidores ocupados
                out.println(mapper.writeValueAsString(req));
                String respStr = in.readLine();

                if (respStr != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resp = mapper.readValue(respStr, Map.class);
                    if ("OK".equals(resp.get("status"))) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rd = (Map<String, Object>) resp.get("data");
                        String nombre = rd != null
                                ? (String) rd.getOrDefault("nombre", peerId) : peerId;
                        @SuppressWarnings("unchecked")
                        List<String> clientesPeer = rd != null
                                ? (List<String>) rd.getOrDefault("clientesConectados", new ArrayList<>())
                                : new ArrayList<>();

                        ServidorAmigo s = new ServidorAmigo(host, port, nombre, true);
                        s.setClientesConectados(clientesPeer);
                        servidorPool.registrarOActualizar(s);

                        // Siempre actualizar estado (mantiene conectado=true y ultimaConexion)
                        servidorPool.registrarOActualizar(s);
                        servidorPool.resetearFallos(peerId); // Heartbeat OK, resetear contador

                        if (!eraConectado) {
                            registrar("[DISTRIBUIDO] Servidor '" + nombre + "' (" + peerId + ") conectado");
                            ConsolaUtil.ok("Servidor amigo conectado: " + nombre + " (" + peerId + ")");
                            // Iniciar sincronización con el peer recién descubierto
                            final ServidorAmigo peerFinal = s;
                            CompletableFuture.runAsync(() -> sincronizarConPeer(peerFinal));
                        }

                        // ── Gossip pull ────────────────────────────────────────────
                        // El peer nos dice qué otros servidores conoce.
                        // Si hay alguno que nosotros no conocemos, nos anunciamos.
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> peersConocidos = rd != null
                                ? (List<Map<String, Object>>) rd.getOrDefault("peersConocidos", new ArrayList<>())
                                : new ArrayList<>();

                        for (Map<String, Object> pi : peersConocidos) {
                            String pHost = (String) pi.get("host");
                            int pPuerto = pi.get("puerto") instanceof Number n ? n.intValue() : 0;
                            if (pHost == null || pPuerto == 0) continue;
                            // Usar esMismoServidor para comparación robusta de hosts
                            if (esMismoServidor(pHost, pPuerto)) continue;
                            String pId = pHost + ":" + pPuerto;
                            // Verificar también con IP normalizada para evitar duplicados
                            if (!servidorPool.isKnown(pId) && !existePeerEquivalente(pHost, pPuerto)) {
                                ConsolaUtil.info("Gossip: descubierto nuevo peer " + pId + " via " + peerId);
                                CompletableFuture.runAsync(() -> anunciarA(pHost, pPuerto));
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            // Registrar fallo - solo desconectar después de varios fallos consecutivos
            boolean debeDesconectar = servidorPool.registrarFallo(peerId);

            Optional<ServidorAmigo> existing = servidorPool.buscarPorId(peerId);
            if (existing.isEmpty()) {
                // Peer nuevo que nunca se conectó
                ServidorAmigo s = new ServidorAmigo(host, port, peerId, false);
                servidorPool.registrarOActualizar(s);
                servidorPool.marcarDesconectado(peerId);
            } else if (eraConectado && debeDesconectar) {
                // Solo marcar desconectado después de 3 fallos consecutivos
                servidorPool.marcarDesconectado(peerId);
                registrar("[DISTRIBUIDO] Servidor '" + peerId + "' desconectado (3 fallos consecutivos)");
                ConsolaUtil.warn("Servidor amigo desconectado: " + peerId);
            } else if (eraConectado) {
                // Fallo temporal, aún no desconectar
                int fallos = existing.get().getFallosConsecutivos();
                ConsolaUtil.warn("Heartbeat fallido para " + peerId + " (" + fallos + "/3)");
            }
        }
    }

    /**
     * Gossip push: cuando un nuevo peer se une, lo propagamos a todos los servidores
     * que ya conocemos para que ellos también lo registren.
     */
    public void propagarNuevoPeerATodos(ServidorAmigo newPeer) {
        for (ServidorAmigo peer : servidorPool.listarConectados()) {
            if (peer.getId().equals(newPeer.getId())) continue;
            CompletableFuture.runAsync(() -> propagarPeerA(peer, newPeer));
        }
    }

    /** Envía la info de {@code newPeer} a {@code target} como si fuera un anuncio de newPeer. */
    private void propagarPeerA(ServidorAmigo target, ServidorAmigo newPeer) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nombre", newPeer.getNombre());
            data.put("host",   newPeer.getHost());
            data.put("puerto", newPeer.getPuerto());
            data.put("clientesConectados",
                     newPeer.getClientesConectados() != null
                             ? newPeer.getClientesConectados() : new ArrayList<>());

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type",   "SERVIDOR");
            req.put("method", "POST");
            req.put("data",   data);

            try (Socket socket = new Socket(target.getHost(), target.getPuerto());
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in  = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {
                socket.setSoTimeout(5000);
                out.println(mapper.writeValueAsString(req));
                in.readLine(); // ACK
                ConsolaUtil.info("Gossip: propagado " + newPeer.getId() + " → " + target.getId());
            }
        } catch (Exception e) {
            ConsolaUtil.warn("Gossip: no se pudo propagar " + newPeer.getId()
                    + " a " + target.getId() + ": " + e.getMessage());
        }
    }

    private void iniciarHeartbeat() {
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10_000); // 10s para sincronización más rápida
                    // Heartbeat a TODOS los servidores conocidos (no solo los configurados)
                    // así los peers descubiertos dinámicamente también se mantienen vivos.
                    for (ServidorAmigo peer : servidorPool.listarTodos()) {
                        CompletableFuture.runAsync(
                                () -> anunciarA(peer.getHost(), peer.getPuerto()));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "hilo-heartbeat-servidores").start();
    }

    /**
     * Replica un documento a TODOS los peers conocidos (no solo conectados).
     * Se intenta enviar a todos - si un peer está caído, fallará naturalmente.
     * Esto evita que el heartbeat interfiera con replicaciones en progreso.
     */
    public void replicarDocumento(Map<String, Object> docData, String destinatario) {
        List<ServidorAmigo> peers = servidorPool.listarTodos();
        if (peers.isEmpty()) return;
        for (ServidorAmigo peer : peers) {
            // No replicar a sí mismo
            if (esMismoServidor(peer.getHost(), peer.getPuerto())) {
                continue;
            }
            CompletableFuture.runAsync(() -> enviarDocumentoAPeer(peer, docData));
        }
    }

    private void enviarDocumentoAPeer(ServidorAmigo peer, Map<String, Object> docData) {
        String peerId = peer.getId();
        servidorPool.iniciarTransferencia(peerId);
        try {
            String tipo = (String) docData.getOrDefault("tipo", "ARCHIVO");

            Map<String, Object> dataCopia = new LinkedHashMap<>(docData);
            dataCopia.put("tipoPropietario", "EXTERNO");
            dataCopia.put("propietarioIp", hostServidor);

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "ARCHIVO".equalsIgnoreCase(tipo) ? "ARCHIVO" : "MENSAJE");
            req.put("method", "POST");
            req.put("data", dataCopia);

            try (Socket socket = new Socket(peer.getHost(), peer.getPuerto());
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {
                socket.setSoTimeout(60_000); // 60s para archivos
                out.println(mapper.writeValueAsString(req));
                in.readLine(); // ACK
                ConsolaUtil.ok("Replicado a " + peerId
                        + " [" + docData.getOrDefault("nombre", "?") + "]");
            }
        } catch (Exception e) {
            ConsolaUtil.warn("Error replicando a " + peerId + ": " + e.getMessage());
        } finally {
            servidorPool.finalizarTransferencia(peerId);
        }
    }

    /**
     * Replica una imagen de rostro (con su emoción) a todos los peers conocidos.
     */
    public void replicarImagenRostro(String imagenBase64, String nombre, String emocion,
                                      String usuarioNombre, Long usuarioId, String idUnico) {
        List<ServidorAmigo> peers = servidorPool.listarTodos();
        if (peers.isEmpty()) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("imagenBase64", imagenBase64);
        data.put("nombre", nombre);
        data.put("emocion", emocion);
        data.put("usuarioNombre", usuarioNombre);
        data.put("usuarioId", usuarioId);
        data.put("tipoPropietario", "EXTERNO");
        data.put("idUnico", idUnico);

        for (ServidorAmigo peer : peers) {
            if (esMismoServidor(peer.getHost(), peer.getPuerto())) continue;
            CompletableFuture.runAsync(() -> enviarImagenRostroAPeer(peer, data));
        }
    }

    private void enviarImagenRostroAPeer(ServidorAmigo peer, Map<String, Object> data) {
        String peerId = peer.getId();
        servidorPool.iniciarTransferencia(peerId);
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "EMOCION");
            req.put("method", "POST");
            req.put("data", data);

            try (Socket socket = new Socket(peer.getHost(), peer.getPuerto());
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {
                socket.setSoTimeout(30_000);
                out.println(mapper.writeValueAsString(req));
                in.readLine(); // ACK
                ConsolaUtil.ok("Imagen rostro replicada a " + peerId
                        + " [" + data.getOrDefault("nombre", "?") + " - " + data.get("emocion") + "]");
            }
        } catch (Exception e) {
            ConsolaUtil.warn("Error replicando imagen rostro a " + peerId + ": " + e.getMessage());
        } finally {
            servidorPool.finalizarTransferencia(peerId);
        }
    }

    // ── Chunked replication (large files) ────────────────────────────────────

    private static final int REPLICATION_CHUNK_SIZE = 4 * 1024 * 1024; // 4MB como el cliente

    /**
     * Replica un archivo grande a todos los peers usando el protocolo
     * POST_CHUNK_INIT / POST_CHUNK × N / POST_CHUNK_END, evitando codificar
     * todo el contenido como un único JSON de decenas de MB.
     *
     * @param meta  campos de cabecera (nombre, extension, usuarioNombre, usuarioId, destinatario)
     * @param datos lista de byte[] — un elemento por chunk original del archivo (24KB)
     */
    public void replicarArchivoGrandePorChunks(Map<String, Object> meta, List<byte[]> datos) {
        List<ServidorAmigo> peers = servidorPool.listarTodos();
        if (peers.isEmpty()) return;

        // Consolidar chunks pequeños (24KB) en chunks grandes (4MB) para menos round-trips
        List<byte[]> chunkesGrandes = consolidarChunks(datos, REPLICATION_CHUNK_SIZE);

        for (ServidorAmigo peer : peers) {
            // No replicar a sí mismo
            if (esMismoServidor(peer.getHost(), peer.getPuerto())) {
                continue;
            }
            CompletableFuture.runAsync(() -> enviarArchivoChunkeadoAPeer(peer, meta, chunkesGrandes));
        }
    }

    /**
     * Replica archivo grande leyendo desde disco en streaming (no carga todo en memoria).
     */
    public void replicarArchivoGrandeDesdeArchivo(Map<String, Object> meta, java.io.File archivo) {
        List<ServidorAmigo> peers = servidorPool.listarTodos();
        if (peers.isEmpty()) return;

        for (ServidorAmigo peer : peers) {
            // No replicar a sí mismo
            if (esMismoServidor(peer.getHost(), peer.getPuerto())) {
                continue;
            }
            CompletableFuture.runAsync(() -> enviarArchivoStreamingAPeer(peer, meta, archivo));
        }
    }

    private void enviarArchivoStreamingAPeer(ServidorAmigo peer, Map<String, Object> meta, java.io.File archivo) {
        String peerId = peer.getId();
        servidorPool.iniciarTransferencia(peerId);
        try (Socket socket = new Socket(peer.getHost(), peer.getPuerto());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             java.io.FileInputStream fis = new java.io.FileInputStream(archivo);
             java.io.BufferedInputStream bis = new java.io.BufferedInputStream(fis, 512 * 1024)) {

            socket.setSoTimeout(300_000); // 5 minutos para archivos grandes
            long tamanio = archivo.length();
            int totalChunks = (int) Math.ceil((double) tamanio / REPLICATION_CHUNK_SIZE);

            // 1. INIT
            Map<String, Object> initData = new LinkedHashMap<>(meta);
            initData.put("totalChunks", totalChunks);
            initData.put("tamanio", tamanio);
            initData.put("chunkSize", REPLICATION_CHUNK_SIZE);
            initData.put("tipoPropietario", "EXTERNO");

            Map<String, Object> initReq = new LinkedHashMap<>();
            initReq.put("type", "ARCHIVO");
            initReq.put("method", "POST_CHUNK_INIT");
            initReq.put("data", initData);
            out.println(mapper.writeValueAsString(initReq));

            String initResp = in.readLine();
            if (initResp == null || !initResp.contains("\"status\":\"OK\"")) {
                ConsolaUtil.warn("INIT rechazado por " + peer.getId());
                return;
            }

            // Extraer uploadId
            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = mapper.readValue(initResp, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) respMap.get("data");
            String uploadId = (String) dataMap.get("uploadId");

            // 2. Enviar chunks en streaming (sin cargar todo en memoria)
            byte[] buf = new byte[REPLICATION_CHUNK_SIZE];
            int chunkIndex = 0;
            int bytesLeidos;

            while ((bytesLeidos = leerCompleto(bis, buf)) > 0) {
                byte[] chunkData = (bytesLeidos == REPLICATION_CHUNK_SIZE)
                    ? buf
                    : java.util.Arrays.copyOf(buf, bytesLeidos);

                Map<String, Object> chunkReq = new LinkedHashMap<>();
                chunkReq.put("type", "ARCHIVO");
                chunkReq.put("method", "POST_CHUNK");
                Map<String, Object> chunkBody = new LinkedHashMap<>();
                chunkBody.put("uploadId", uploadId);
                chunkBody.put("chunkIndex", chunkIndex);
                chunkBody.put("totalChunks", totalChunks);
                chunkBody.put("contenido", Base64.getEncoder().encodeToString(chunkData));
                chunkReq.put("data", chunkBody);

                out.println(mapper.writeValueAsString(chunkReq));
                in.readLine(); // ACK
                chunkIndex++;
            }

            // 3. END
            Map<String, Object> endReq = new LinkedHashMap<>();
            endReq.put("type", "ARCHIVO");
            endReq.put("method", "POST_CHUNK_END");
            endReq.put("data", Map.of("uploadId", uploadId));
            out.println(mapper.writeValueAsString(endReq));
            in.readLine();

            ConsolaUtil.ok("Replicado a " + peerId + ": " + archivo.getName() + " (" + chunkIndex + " chunks)");

        } catch (Exception e) {
            ConsolaUtil.warn("Error replicando a " + peerId + ": " + e.getMessage());
        } finally {
            servidorPool.finalizarTransferencia(peerId);
        }
    }

    private int leerCompleto(java.io.InputStream is, byte[] buf) throws java.io.IOException {
        int total = 0;
        while (total < buf.length) {
            int n = is.read(buf, total, buf.length - total);
            if (n == -1) break;
            total += n;
        }
        return total;
    }

    private List<byte[]> consolidarChunks(List<byte[]> chunks, int targetSize) {
        List<byte[]> resultado = new ArrayList<>();
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

        for (byte[] chunk : chunks) {
            try {
                buffer.write(chunk);
                if (buffer.size() >= targetSize) {
                    resultado.add(buffer.toByteArray());
                    buffer.reset();
                }
            } catch (Exception ignored) {}
        }
        if (buffer.size() > 0) {
            resultado.add(buffer.toByteArray());
        }
        return resultado;
    }

    private void enviarArchivoChunkeadoAPeer(ServidorAmigo peer,
                                              Map<String, Object> meta,
                                              List<byte[]> datos) {
        String peerId = peer.getId();
        servidorPool.iniciarTransferencia(peerId);
        try {
            int  totalChunks = datos.size();
            long tamanio     = datos.stream().mapToLong(b -> b.length).sum();
            int  chunkSize   = datos.isEmpty() ? 0 : datos.get(0).length;

            Map<String, Object> initData = new LinkedHashMap<>(meta);
            initData.put("totalChunks",    totalChunks);
            initData.put("tamanio",        tamanio);
            initData.put("chunkSize",      chunkSize);
            initData.put("tipoPropietario", "EXTERNO");
            initData.put("propietarioIp",   hostServidor);

            Map<String, Object> initReq = new LinkedHashMap<>();
            initReq.put("type",   "ARCHIVO");
            initReq.put("method", "POST_CHUNK_INIT");
            initReq.put("data",   initData);

            try (Socket       socket = new Socket(peer.getHost(), peer.getPuerto());
                 PrintWriter  out    = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in   = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(300_000); // 5 minutos

                out.println(mapper.writeValueAsString(initReq));
                String initRespStr = in.readLine();
                if (initRespStr == null) {
                    ConsolaUtil.warn("Replicación chunked: sin respuesta de INIT desde " + peer.getId());
                    return;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> initResp = mapper.readValue(initRespStr, Map.class);
                if (!"OK".equals(initResp.get("status"))) {
                    ConsolaUtil.warn("Replicación chunked: INIT rechazado por " + peer.getId());
                    return;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> initRespData = (Map<String, Object>) initResp.get("data");
                String uploadId = initRespData != null ? (String) initRespData.get("uploadId") : null;
                if (uploadId == null) {
                    ConsolaUtil.warn("Replicación chunked: uploadId null desde " + peer.getId());
                    return;
                }

                for (int i = 0; i < datos.size(); i++) {
                    Map<String, Object> chunkData = new LinkedHashMap<>();
                    chunkData.put("uploadId",   uploadId);
                    chunkData.put("chunkIndex", i);
                    chunkData.put("contenido",  Base64.getEncoder().encodeToString(datos.get(i)));

                    Map<String, Object> chunkReq = new LinkedHashMap<>();
                    chunkReq.put("type",   "ARCHIVO");
                    chunkReq.put("method", "POST_CHUNK");
                    chunkReq.put("data",   chunkData);

                    out.println(mapper.writeValueAsString(chunkReq));
                    in.readLine(); // ACK
                }

                Map<String, Object> endData = new LinkedHashMap<>();
                endData.put("uploadId", uploadId);

                Map<String, Object> endReq = new LinkedHashMap<>();
                endReq.put("type",   "ARCHIVO");
                endReq.put("method", "POST_CHUNK_END");
                endReq.put("data",   endData);

                out.println(mapper.writeValueAsString(endReq));
                in.readLine(); // final ACK

                ConsolaUtil.ok("Replicación chunked completa → " + peerId
                        + " [" + meta.getOrDefault("nombre", "?") + "] "
                        + totalChunks + " chunks");
            }
        } catch (Exception e) {
            ConsolaUtil.warn("Error replicando archivo grande a " + peerId
                    + ": " + e.getMessage());
        } finally {
            servidorPool.finalizarTransferencia(peerId);
        }
    }

    // ── Notificación inmediata de cambios en clientes ──────────────────────────

    /**
     * Notifica a todos los peers conectados que la lista de clientes cambió.
     * Llamar cuando un cliente se conecta o desconecta para sincronización inmediata.
     */
    public void notificarCambioClientes() {
        List<ServidorAmigo> peers = servidorPool.listarConectados();
        if (peers.isEmpty()) return;

        ConsolaUtil.info("Notificando cambio de clientes a " + peers.size() + " peers...");
        for (ServidorAmigo peer : peers) {
            CompletableFuture.runAsync(() -> anunciarA(peer.getHost(), peer.getPuerto()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> obtenerNombresClientes() {
        return usuarioPool.obtenerUsuariosConectados().stream()
                .map(cu -> cu.getUsuario().getNombre())
                .collect(Collectors.toList());
    }

    private void registrar(String mensaje) {
        try { logService.registrar(null, TipoLog.CONEXION, mensaje); }
        catch (Exception ignored) {}
    }

    public String obtenerNombre() { return nombreServidor; }
    public String obtenerHost()   { return hostServidor; }
    public int obtenerPuerto()    { return puertoServidor; }

    /**
     * Verifica si el host:port dado corresponde a este mismo servidor.
     * Compara considerando localhost, 127.0.0.1, y la IP real del servidor.
     */
    private boolean esMismoServidor(String host, int port) {
        if (port != puertoServidor) return false;

        String hostNormalizado = normalizarHost(host);
        String miHostNormalizado = normalizarHost(hostServidor);

        if (hostNormalizado.equals(miHostNormalizado)) return true;

        // Verificar si ambos son direcciones locales
        if (esHostLocal(host) && esHostLocal(hostServidor)) return true;

        // Intentar resolver y comparar IPs
        try {
            InetAddress addrRemoto = InetAddress.getByName(host);
            InetAddress addrLocal = InetAddress.getByName(hostServidor);
            if (addrRemoto.equals(addrLocal)) return true;

            // Verificar contra todas las direcciones locales
            if (addrRemoto.isLoopbackAddress() || addrRemoto.equals(InetAddress.getLocalHost())) {
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    private String normalizarHost(String host) {
        if (host == null) return "";
        host = host.trim().toLowerCase();
        if (host.equals("127.0.0.1") || host.equals("::1")) {
            return "localhost";
        }
        return host;
    }

    private boolean esHostLocal(String host) {
        if (host == null) return false;
        host = host.trim().toLowerCase();
        return host.equals("localhost") ||
               host.equals("127.0.0.1") ||
               host.equals("::1") ||
               host.startsWith("127.");
    }

    /**
     * Verifica si ya existe un peer equivalente registrado en el pool.
     * Esto maneja el caso donde el mismo servidor puede estar anunciado con
     * diferentes representaciones de host (localhost vs IP vs hostname).
     */
    private boolean existePeerEquivalente(String host, int puerto) {
        for (ServidorAmigo peer : servidorPool.listarTodos()) {
            if (peer.getPuerto() != puerto) continue;

            // Comparar hosts normalizados
            String hostNorm = normalizarHost(host);
            String peerHostNorm = normalizarHost(peer.getHost());
            if (hostNorm.equals(peerHostNorm)) return true;

            // Verificar si ambos son locales
            if (esHostLocal(host) && esHostLocal(peer.getHost())) return true;

            // Intentar resolver IPs
            try {
                InetAddress addr1 = InetAddress.getByName(host);
                InetAddress addr2 = InetAddress.getByName(peer.getHost());
                if (addr1.equals(addr2)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Resuelve la IP real de un host para evitar ambiguedades con localhost/127.0.0.1.
     * Útil para entornos mixtos donde algunos servidores se anuncian con localhost
     * y otros con su IP real.
     */
    private String resolverIpReal(String host) {
        try {
            if (esHostLocal(host)) {
                // Para hosts locales, intentar obtener la IP de red real
                InetAddress localHost = InetAddress.getLocalHost();
                if (!localHost.isLoopbackAddress()) {
                    return localHost.getHostAddress();
                }
            }
            return InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            return host;
        }
    }

    // ── Sincronización de documentos entre servidores ─────────────────────────

    // Pool de hilos para descargas paralelas (límite para no saturar)
    private static final int MAX_DESCARGAS_PARALELAS = 3;
    private final java.util.concurrent.ExecutorService syncExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(MAX_DESCARGAS_PARALELAS);

    /**
     * Sincroniza documentos con un peer: obtiene la lista de sus documentos y descarga
     * los que no tenemos localmente (comparando por hash).
     * Las descargas se hacen en paralelo para mayor velocidad.
     */
    public void sincronizarConPeer(ServidorAmigo peer) {
        String peerId = peer.getId();
        if (syncEnProgreso.contains(peerId)) {
            ConsolaUtil.info("Sync con " + peerId + " ya en progreso, omitiendo...");
            return;
        }

        syncEnProgreso.add(peerId);
        long tiempoInicio = System.currentTimeMillis();

        try {
            List<Map<String, Object>> docsRemotos = solicitarListaSyncDePeer(peer);
            if (docsRemotos == null || docsRemotos.isEmpty()) {
                ConsolaUtil.syncSinDocumentos(peerId);
                return;
            }

            // Filtrar documentos que necesitamos descargar
            List<Map<String, Object>> porDescargar = new ArrayList<>();
            int yaExistian = 0;
            int omitidos = 0;

            for (Map<String, Object> docRemoto : docsRemotos) {
                String idUnicoRemoto = (String) docRemoto.get("idUnico");
                String hashRemoto = (String) docRemoto.get("hash");

                // Verificar si ya existe en BD
                if (idUnicoRemoto != null && !idUnicoRemoto.isBlank()) {
                    if (documentoService.existePorIdUnico(idUnicoRemoto)) {
                        yaExistian++;
                        continue;
                    }
                    // Verificar si ya hay una descarga en progreso para este idUnico
                    if (descargasEnProgreso.contains(idUnicoRemoto)) {
                        ConsolaUtil.info("Documento " + idUnicoRemoto + " ya está siendo descargado por otro peer");
                        yaExistian++;
                        continue;
                    }
                } else if (hashRemoto != null && !hashRemoto.isBlank()) {
                    // Doc viejo sin idUnico, usar hash como fallback
                    if (documentoService.existePorHash(hashRemoto)) {
                        yaExistian++;
                        continue;
                    }
                } else {
                    // Sin idUnico ni hash, omitir
                    omitidos++;
                    continue;
                }
                porDescargar.add(docRemoto);
            }

            if (porDescargar.isEmpty()) {
                ConsolaUtil.syncSinDocumentos(peerId);
                ConsolaUtil.info("  (ya teníamos " + yaExistian + " documentos)");
                return;
            }

            ConsolaUtil.syncInicio(peerId, porDescargar.size());

            // Contadores thread-safe
            java.util.concurrent.atomic.AtomicInteger nuevos = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger errores = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger completados = new java.util.concurrent.atomic.AtomicInteger(0);
            int total = porDescargar.size();

            // Crear tareas de descarga
            List<CompletableFuture<Void>> tareas = new ArrayList<>();

            for (Map<String, Object> docRemoto : porDescargar) {
                Long idRemoto = docRemoto.get("id") != null
                        ? ((Number) docRemoto.get("id")).longValue() : null;
                String nombreDoc = (String) docRemoto.get("nombreVisible");
                if (nombreDoc == null) nombreDoc = "documento_" + idRemoto;
                String idUnicoDoc = (String) docRemoto.get("idUnico");

                // Marcar como descarga en progreso
                if (idUnicoDoc != null && !idUnicoDoc.isBlank()) {
                    descargasEnProgreso.add(idUnicoDoc);
                }

                final String nombreFinal = nombreDoc;
                final Map<String, Object> docFinal = docRemoto;
                final String idUnicoFinal = idUnicoDoc;

                CompletableFuture<Void> tarea = CompletableFuture.runAsync(() -> {
                    try {
                        boolean descargado = descargarDocumentoDePeer(peer, idRemoto, docFinal);
                        int actual = completados.incrementAndGet();

                        if (descargado) {
                            nuevos.incrementAndGet();
                            ConsolaUtil.syncDocumentoOk(nombreFinal);
                        } else {
                            errores.incrementAndGet();
                            ConsolaUtil.syncDocumentoError(nombreFinal, "fallo descarga");
                        }

                        ConsolaUtil.syncProgreso(actual, total, nombreFinal,
                                descargado ? "OK" : "ERROR");

                    } catch (Exception e) {
                        errores.incrementAndGet();
                        completados.incrementAndGet();
                        ConsolaUtil.syncDocumentoError(nombreFinal, e.getMessage());
                    } finally {
                        // Quitar de descargas en progreso
                        if (idUnicoFinal != null) {
                            descargasEnProgreso.remove(idUnicoFinal);
                        }
                    }
                }, syncExecutor);

                tareas.add(tarea);
            }

            // Esperar que todas las descargas terminen
            CompletableFuture.allOf(tareas.toArray(new CompletableFuture[0])).join();

            long tiempoTotal = System.currentTimeMillis() - tiempoInicio;
            ConsolaUtil.syncFin(peerId, nuevos.get(), yaExistian, errores.get(), tiempoTotal);

            if (nuevos.get() > 0) {
                registrar("[SYNC] Sincronizados " + nuevos.get() + " documentos desde " + peerId);
            }

            // Sincronizar imágenes de rostro
            sincronizarImagenesRostroConPeer(peer);

        } catch (Exception e) {
            ConsolaUtil.error("Error sincronizando con " + peerId + ": " + e.getMessage());
        } finally {
            syncEnProgreso.remove(peerId);
        }
    }

    /**
     * Sincroniza imágenes de rostro (emociones) con un peer.
     */
    private void sincronizarImagenesRostroConPeer(ServidorAmigo peer) {
        String peerId = peer.getId();
        try {
            List<Map<String, Object>> imagenesRemotas = solicitarListaImagenesRostroDePeer(peer);
            if (imagenesRemotas == null || imagenesRemotas.isEmpty()) {
                ConsolaUtil.info("No hay imágenes de rostro para sincronizar desde " + peerId);
                return;
            }

            ConsolaUtil.info("Sincronizando " + imagenesRemotas.size() + " imágenes de rostro desde " + peerId);

            int nuevas = 0;
            int yaExistian = 0;
            int omitidas = 0;

            for (Map<String, Object> imgRemota : imagenesRemotas) {
                String idUnico = (String) imgRemota.get("idUnico");
                String nombreImg = (String) imgRemota.get("nombre");

                // Verificar si ya existe una ImagenRostro para este documento
                if (idUnico != null && !idUnico.isBlank()) {
                    // Verificar si ya hay una descarga en progreso para este idUnico
                    if (descargasEnProgreso.contains(idUnico)) {
                        ConsolaUtil.info("Imagen rostro " + idUnico + " ya está siendo descargada por otro peer");
                        yaExistian++;
                        continue;
                    }
                    // Buscar si existe el documento con este idUnico
                    var docExistente = documentoService.buscarPorIdUnico(idUnico);
                    if (docExistente.isPresent()) {
                        // Verificar si ya existe una ImagenRostro asociada a ese documento
                        if (imagenRostroService.existePorDocumentoId(docExistente.get().getId())) {
                            ConsolaUtil.info("Imagen rostro ya existe: " + nombreImg + " idUnico=" + idUnico);
                            yaExistian++;
                            continue;
                        }
                        // El documento existe pero no tiene ImagenRostro asociada, descargar
                        ConsolaUtil.info("Documento existe pero sin ImagenRostro, sincronizando: " + nombreImg);
                    }
                } else {
                    // Sin idUnico, verificar que al menos tenga documentoId para poder descargarse
                    Long docId = imgRemota.get("documentoId") != null
                            ? ((Number) imgRemota.get("documentoId")).longValue() : null;
                    if (docId == null) {
                        ConsolaUtil.warn("Imagen rostro sin docId ni idUnico, omitiendo: " + nombreImg);
                        omitidas++;
                        continue;
                    }
                    // Si tiene docId pero no idUnico, intentar descargar igual (imágenes viejas)
                    ConsolaUtil.info("Imagen rostro sin idUnico, intentando descargar: " + nombreImg);
                }

                // Marcar como descarga en progreso
                if (idUnico != null && !idUnico.isBlank()) {
                    descargasEnProgreso.add(idUnico);
                }

                // Descargar la imagen completa
                try {
                    boolean ok = descargarImagenRostroDePeer(peer, imgRemota);
                    if (ok) {
                        nuevas++;
                        ConsolaUtil.ok("Imagen rostro sincronizada: " + nombreImg);
                    } else {
                        ConsolaUtil.warn("Error descargando imagen rostro: " + nombreImg);
                    }
                } finally {
                    // Quitar de descargas en progreso
                    if (idUnico != null) {
                        descargasEnProgreso.remove(idUnico);
                    }
                }
            }

            if (nuevas > 0) {
                ConsolaUtil.ok("Sincronizadas " + nuevas + " imágenes de rostro desde " + peerId);
                registrar("[SYNC] Sincronizadas " + nuevas + " imágenes de rostro desde " + peerId);
            } else {
                ConsolaUtil.info("Imágenes rostro: " + yaExistian + " ya existían, " + omitidas + " omitidas");
            }

        } catch (Exception e) {
            ConsolaUtil.warn("Error sincronizando imágenes de rostro con " + peerId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> solicitarListaImagenesRostroDePeer(ServidorAmigo peer) {
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "EMOCION");
            req.put("method", "LIST_SYNC");
            req.put("data", new LinkedHashMap<>());

            try (Socket socket = new Socket(peer.getHost(), peer.getPuerto());
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(30_000);
                out.println(mapper.writeValueAsString(req));
                String respStr = in.readLine();

                if (respStr == null) return null;

                Map<String, Object> resp = mapper.readValue(respStr, Map.class);
                if (!"OK".equals(resp.get("status"))) return null;

                Object data = resp.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
                return null;
            }
        } catch (Exception e) {
            ConsolaUtil.warn("Error solicitando lista imágenes rostro de " + peer.getId() + ": " + e.getMessage());
            return null;
        }
    }

    private boolean descargarImagenRostroDePeer(ServidorAmigo peer, Map<String, Object> metadatos) {
        String peerId = peer.getId();
        servidorPool.iniciarTransferencia(peerId);
        try {
            Long idRemoto = metadatos.get("id") != null ? ((Number) metadatos.get("id")).longValue() : null;
            Long docIdRemoto = metadatos.get("documentoId") != null
                    ? ((Number) metadatos.get("documentoId")).longValue() : null;

            if (docIdRemoto == null) return false;

            // Obtener la imagen con datos Base64 del peer
            Map<String, Object> reqData = new LinkedHashMap<>();
            reqData.put("id", idRemoto);

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "EMOCION");
            req.put("method", "GET_IMAGE");
            req.put("data", reqData);

            try (Socket socket = new Socket(peer.getHost(), peer.getPuerto());
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(60_000);
                out.println(mapper.writeValueAsString(req));
                String respStr = in.readLine();

                if (respStr == null) return false;

                @SuppressWarnings("unchecked")
                Map<String, Object> resp = mapper.readValue(respStr, Map.class);
                if (!"OK".equals(resp.get("status"))) return false;

                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                if (data == null) return false;

                String datosBase64 = (String) data.get("datosBase64");
                if (datosBase64 == null || datosBase64.isBlank()) return false;

                // Usar idUnico del GET_IMAGE si existe, sino del LIST_SYNC
                String idUnico = data.get("idUnico") != null
                        ? (String) data.get("idUnico")
                        : (String) metadatos.get("idUnico");

                // Guardar como EMOCION POST
                Map<String, Object> emocionData = new LinkedHashMap<>();
                emocionData.put("imagenBase64", datosBase64);
                emocionData.put("nombre", data.get("nombre") != null ? data.get("nombre") : metadatos.get("nombre"));
                emocionData.put("emocion", data.get("emocion") != null ? data.get("emocion") : metadatos.get("emocion"));
                emocionData.put("usuarioNombre", "sync");
                emocionData.put("usuarioId", 1L);
                emocionData.put("tipoPropietario", "EXTERNO");
                emocionData.put("idUnico", idUnico);

                ConsolaUtil.info("Descargando imagen rostro: " + emocionData.get("nombre") + " idUnico=" + idUnico);
                guardarImagenRostroSincronizada(emocionData);
                return true;
            }

        } catch (Exception e) {
            ConsolaUtil.warn("Error descargando imagen rostro: " + e.getMessage());
            return false;
        } finally {
            servidorPool.finalizarTransferencia(peerId);
        }
    }

    private void guardarImagenRostroSincronizada(Map<String, Object> data) {
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "EMOCION");
            req.put("method", "POST");
            req.put("data", data);

            try (Socket socket = new Socket("localhost", puertoServidor);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(60_000);
                out.println(mapper.writeValueAsString(req));
                in.readLine(); // ACK
            }
        } catch (Exception e) {
            ConsolaUtil.warn("Error guardando imagen rostro sincronizada: " + e.getMessage());
        }
    }

    /**
     * Solicita LIST_SYNC al peer y devuelve la lista de documentos.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> solicitarListaSyncDePeer(ServidorAmigo peer) {
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "ARCHIVO");
            req.put("method", "LIST_SYNC");
            req.put("data", new LinkedHashMap<>());

            try (Socket socket = new Socket(peer.getHost(), peer.getPuerto());
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(30_000);
                out.println(mapper.writeValueAsString(req));
                String respStr = in.readLine();

                if (respStr == null) return null;

                Map<String, Object> resp = mapper.readValue(respStr, Map.class);
                if (!"OK".equals(resp.get("status"))) {
                    ConsolaUtil.warn("LIST_SYNC rechazado por " + peer.getId());
                    return null;
                }

                Object data = resp.get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
                return null;
            }
        } catch (Exception e) {
            ConsolaUtil.warn("Error obteniendo lista de " + peer.getId() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Descarga un documento específico del peer y lo guarda localmente.
     * Usa el protocolo GET chunks para archivos, escribiendo a disco progresivamente
     * para evitar OOM en archivos grandes.
     */
    @SuppressWarnings("unchecked")
    private boolean descargarDocumentoDePeer(ServidorAmigo peer, Long idRemoto,
                                              Map<String, Object> metadatos) {
        String peerId = peer.getId();
        servidorPool.iniciarTransferencia(peerId);
        java.io.File archivoTemp = null;
        try {
            String tipo = (String) metadatos.getOrDefault("tipo", "ARCHIVO");

            if ("MENSAJE".equalsIgnoreCase(tipo)) {
                return descargarMensajeDePeer(peer, idRemoto, metadatos);
            }

            // Extraer nombre y extensión
            String nombreVisible = (String) metadatos.get("nombreVisible");
            String nombre, extension;
            if (nombreVisible != null && nombreVisible.contains(".")) {
                int lastDot = nombreVisible.lastIndexOf('.');
                nombre = nombreVisible.substring(0, lastDot);
                extension = nombreVisible.substring(lastDot + 1);
            } else {
                nombre = nombreVisible != null ? nombreVisible : "archivo";
                extension = "bin";
            }

            // Para archivos, usar GET chunks
            Map<String, Object> reqData = new LinkedHashMap<>();
            reqData.put("id", idRemoto);

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "ARCHIVO");
            req.put("method", "GET");
            req.put("data", reqData);

            try (Socket socket = new Socket(peer.getHost(), peer.getPuerto());
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(300_000); // 5 minutos para archivos grandes

                out.println(mapper.writeValueAsString(req));

                // Primera línea: cabecera
                String cabeceraStr = in.readLine();
                if (cabeceraStr == null) return false;

                Map<String, Object> cabecera = mapper.readValue(cabeceraStr, Map.class);
                if (!"OK".equals(cabecera.get("status"))) {
                    ConsolaUtil.warn("GET rechazado: " + cabecera.get("message"));
                    return false;
                }

                int totalChunks = ((Number) cabecera.get("totalChunks")).intValue();
                String nombreCompleto = nombre + "." + extension;

                // Crear archivo temporal para escribir chunks progresivamente
                archivoTemp = java.io.File.createTempFile("sync_", ".tmp");
                long totalBytes = 0;
                int chunksRecibidos = 0;

                try (java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(
                        new java.io.FileOutputStream(archivoTemp), 512 * 1024)) {

                    // Leer chunks y escribir a disco inmediatamente
                    for (int i = 0; i < totalChunks; i++) {
                        String chunkStr = in.readLine();
                        if (chunkStr == null) break;

                        Map<String, Object> chunkData = mapper.readValue(chunkStr, Map.class);
                        if ("CHUNK".equals(chunkData.get("status"))) {
                            String datos64 = (String) chunkData.get("datos");
                            byte[] datos = Base64.getDecoder().decode(datos64);
                            bos.write(datos);
                            totalBytes += datos.length;
                            chunksRecibidos++;

                            // Mostrar progreso cada 5 chunks o en el último
                            if (chunksRecibidos % 5 == 0 || chunksRecibidos == totalChunks) {
                                ConsolaUtil.syncDescargando(nombreCompleto, chunksRecibidos, totalChunks, totalBytes);
                            }
                        }
                    }
                }

                // Leer FIN
                in.readLine();

                if (chunksRecibidos == 0) {
                    ConsolaUtil.warn("No se recibieron chunks del documento " + idRemoto);
                    return false;
                }

                ConsolaUtil.info("Recibidos " + chunksRecibidos + " chunks, " + totalBytes + " bytes");

                // Guardar usando el protocolo de chunks (funciona para cualquier tamaño)
                return guardarArchivoDesdeTemp(archivoTemp, nombre, extension,
                        totalBytes, metadatos, peer);
            }

        } catch (Exception e) {
            ConsolaUtil.warn("Error descargando documento " + idRemoto + " de " + peerId
                    + ": " + e.getMessage());
            return false;
        } finally {
            servidorPool.finalizarTransferencia(peerId);
            if (archivoTemp != null && archivoTemp.exists()) {
                archivoTemp.delete();
            }
        }
    }

    /**
     * Guarda un archivo desde un archivo temporal usando POST_CHUNK_INIT/POST_CHUNK/POST_CHUNK_END.
     */
    private boolean guardarArchivoDesdeTemp(java.io.File archivoTemp, String nombre,
                                             String extension, long totalBytes,
                                             Map<String, Object> metadatos,
                                             ServidorAmigo peer) {
        try {
            int chunkSize = 4 * 1024 * 1024; // 4MB
            int totalChunks = (int) Math.ceil((double) totalBytes / chunkSize);

            // 1. POST_CHUNK_INIT
            Map<String, Object> initData = new LinkedHashMap<>();
            initData.put("nombre", nombre);
            initData.put("extension", extension);
            initData.put("tamanio", totalBytes);
            initData.put("totalChunks", totalChunks);
            initData.put("chunkSize", chunkSize);
            initData.put("usuarioNombre", metadatos.getOrDefault("usuarioNombre", "sync"));
            initData.put("usuarioId", 1L);
            initData.put("tipoPropietario", "EXTERNO");
            initData.put("propietarioIp", peer.getHost());
            initData.put("destinatario", metadatos.get("destinatario"));
            initData.put("idUnico", metadatos.get("idUnico"));

            Map<String, Object> initReq = new LinkedHashMap<>();
            initReq.put("type", "ARCHIVO");
            initReq.put("method", "POST_CHUNK_INIT");
            initReq.put("data", initData);

            try (Socket socket = new Socket("localhost", puertoServidor);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()));
                 java.io.BufferedInputStream bis = new java.io.BufferedInputStream(
                         new java.io.FileInputStream(archivoTemp), 512 * 1024)) {

                socket.setSoTimeout(300_000);

                // Enviar INIT
                out.println(mapper.writeValueAsString(initReq));
                String initRespStr = in.readLine();
                if (initRespStr == null) return false;

                @SuppressWarnings("unchecked")
                Map<String, Object> initResp = mapper.readValue(initRespStr, Map.class);
                if (!"OK".equals(initResp.get("status"))) {
                    ConsolaUtil.warn("CHUNK_INIT rechazado: " + initResp.get("message"));
                    return false;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> initRespData = (Map<String, Object>) initResp.get("data");
                String uploadId = initRespData != null ? (String) initRespData.get("uploadId") : null;
                if (uploadId == null) {
                    ConsolaUtil.warn("uploadId nulo en respuesta INIT");
                    return false;
                }

                // 2. Enviar chunks leyendo del archivo temporal
                byte[] buf = new byte[chunkSize];
                int chunkIndex = 0;
                int bytesLeidos;

                while ((bytesLeidos = leerCompleto(bis, buf)) > 0) {
                    byte[] chunkData = (bytesLeidos == chunkSize)
                            ? buf : java.util.Arrays.copyOf(buf, bytesLeidos);

                    Map<String, Object> chunkReq = new LinkedHashMap<>();
                    chunkReq.put("type", "ARCHIVO");
                    chunkReq.put("method", "POST_CHUNK");
                    Map<String, Object> chunkBody = new LinkedHashMap<>();
                    chunkBody.put("uploadId", uploadId);
                    chunkBody.put("chunkIndex", chunkIndex);
                    chunkBody.put("contenido", Base64.getEncoder().encodeToString(chunkData));
                    chunkReq.put("data", chunkBody);

                    out.println(mapper.writeValueAsString(chunkReq));
                    in.readLine(); // ACK
                    chunkIndex++;
                }

                // 3. POST_CHUNK_END
                Map<String, Object> endData = new LinkedHashMap<>();
                endData.put("uploadId", uploadId);

                Map<String, Object> endReq = new LinkedHashMap<>();
                endReq.put("type", "ARCHIVO");
                endReq.put("method", "POST_CHUNK_END");
                endReq.put("data", endData);

                out.println(mapper.writeValueAsString(endReq));
                String endRespStr = in.readLine();

                @SuppressWarnings("unchecked")
                Map<String, Object> endResp = mapper.readValue(endRespStr, Map.class);
                if ("OK".equals(endResp.get("status"))) {
                    return true;
                } else {
                    ConsolaUtil.warn("Error en CHUNK_END: " + endResp.get("message"));
                    return false;
                }
            }

        } catch (Exception e) {
            ConsolaUtil.warn("Error guardando archivo sincronizado: " + e.getMessage());
            return false;
        }
    }

    /**
     * Descarga un mensaje del peer.
     */
    @SuppressWarnings("unchecked")
    private boolean descargarMensajeDePeer(ServidorAmigo peer, Long idRemoto,
                                            Map<String, Object> metadatos) {
        try {
            Map<String, Object> reqData = new LinkedHashMap<>();
            reqData.put("id", idRemoto);

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "MENSAJE");
            req.put("method", "GET");
            req.put("data", reqData);

            try (Socket socket = new Socket(peer.getHost(), peer.getPuerto());
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(30_000);
                out.println(mapper.writeValueAsString(req));
                String respStr = in.readLine();

                if (respStr == null) return false;

                Map<String, Object> resp = mapper.readValue(respStr, Map.class);
                if (!"OK".equals(resp.get("status"))) return false;

                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                if (data == null) return false;

                Map<String, Object> msgData = new LinkedHashMap<>();
                msgData.put("asunto", data.get("asunto"));
                msgData.put("contenido", data.get("contenido"));
                msgData.put("usuarioNombre", metadatos.getOrDefault("usuarioNombre", "sync"));
                msgData.put("usuarioId", 1L);
                msgData.put("tipoPropietario", "EXTERNO");
                msgData.put("propietarioIp", peer.getHost());
                msgData.put("destinatario", metadatos.get("destinatario"));
                msgData.put("tipo", "MENSAJE");

                // Usar idUnico del GET si existe, sino del metadato LIST_SYNC
                String idUnico = data.get("idUnico") != null
                        ? (String) data.get("idUnico")
                        : (String) metadatos.get("idUnico");
                msgData.put("idUnico", idUnico);

                guardarDocumentoSincronizado(msgData);
                return true;
            }

        } catch (Exception e) {
            ConsolaUtil.warn("Error descargando mensaje " + idRemoto + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Guarda un documento sincronizado enviándolo al handler local.
     * Se usa una conexión TCP a localhost para pasar por el flujo normal de procesamiento.
     */
    private void guardarDocumentoSincronizado(Map<String, Object> docData) {
        try {
            String tipo = (String) docData.getOrDefault("tipo", "ARCHIVO");

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", tipo);
            req.put("method", "POST");
            req.put("data", docData);

            try (Socket socket = new Socket("localhost", puertoServidor);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(60_000);
                out.println(mapper.writeValueAsString(req));
                in.readLine(); // ACK
            }

        } catch (Exception e) {
            ConsolaUtil.warn("Error guardando documento sincronizado: " + e.getMessage());
        }
    }

    /**
     * Inicia sincronización con un peer cuando se detecta como nuevo.
     * Se ejecuta en background para no bloquear el anuncio.
     */
    public void iniciarSincronizacionConPeer(ServidorAmigo peer) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000); // Esperar a que el peer termine su registro
                sincronizarConPeer(peer);
            } catch (InterruptedException ignored) {}
        });
    }
}
