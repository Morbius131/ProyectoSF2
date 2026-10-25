package Cliente_Servidor.V1.Controladores;

import Cliente_Servidor.V1.DTO.ResponseDTO;
import Cliente_Servidor.V1.DTO.ServidorAmigoDTO;
import Cliente_Servidor.V1.Enums.TipoLog;
import Cliente_Servidor.V1.Handles.HandleResult;
import Cliente_Servidor.V1.Modelos.ConexionUsuario;
import Cliente_Servidor.V1.Modelos.ServidorAmigo;
import Cliente_Servidor.V1.Pool.ServidorAmigoPool;
import Cliente_Servidor.V1.Pool.UsuarioConexionPool;
import Cliente_Servidor.V1.Servicios.DocumentoService;
import Cliente_Servidor.V1.Servicios.LogService;
import Cliente_Servidor.V1.Servicios.ServidorDistribuidoService;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Maneja las peticiones de tipo SERVIDOR:
 *   POST   — peer anunciándose (registra en pool, devuelve nuestra info)
 *   LIST   — lista todos los servidores conocidos (conectados + desconectados)
 *   GET    — info de este servidor o de un peer remoto; subtipo=LOG devuelve logs
 *   DELETE — peer notificando su desconexión
 */
@Component
public class ServidorAmigoControlador {

    private final ServidorAmigoPool servidorPool;
    private final ServidorDistribuidoService distribuidoService;
    private final UsuarioConexionPool usuarioPool;
    private final LogControlador logControlador;
    private final DocumentoService documentoService;
    private final LogService logService;
    private final ObjectMapper mapper;

    public ServidorAmigoControlador(ServidorAmigoPool servidorPool,
                                     ServidorDistribuidoService distribuidoService,
                                     UsuarioConexionPool usuarioPool,
                                     LogControlador logControlador,
                                     DocumentoService documentoService,
                                     LogService logService) {
        this.servidorPool = servidorPool;
        this.distribuidoService = distribuidoService;
        this.usuarioPool = usuarioPool;
        this.logControlador = logControlador;
        this.documentoService = documentoService;
        this.logService = logService;
        this.mapper = new ObjectMapper();
        this.mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build());
    }

    // ── SERVIDOR / POST ───────────────────────────────────────────────────────

    /** Peer anunciándose — lo registra en el pool y devuelve la info de este servidor. */
    public HandleResult recibirAnuncio(Map<String, Object> data) throws Exception {
        String nombre = (String) data.getOrDefault("nombre", "Desconocido");
        String host   = (String) data.getOrDefault("host", "?");
        int    puerto = toInt(data.get("puerto"));

        // No registrar si es este mismo servidor
        if (esMismoServidor(host, puerto)) {
            ConsolaUtil.info("Ignorando anuncio de sí mismo: " + host + ":" + puerto);
            Map<String, Object> respData = new LinkedHashMap<>();
            respData.put("nombre", distribuidoService.obtenerNombre());
            respData.put("host",   distribuidoService.obtenerHost());
            respData.put("puerto", distribuidoService.obtenerPuerto());
            return new HandleResult(mapper.writeValueAsString(ResponseDTO.ok(respData)), null);
        }

        @SuppressWarnings("unchecked")
        List<String> clientesPeer = (List<String>) data.getOrDefault("clientesConectados", new ArrayList<>());

        String peerId = host + ":" + puerto;
        boolean eraConectado = servidorPool.isConectado(peerId);

        ServidorAmigo s = new ServidorAmigo(host, puerto, nombre, true);
        s.setClientesConectados(clientesPeer);
        servidorPool.registrarOActualizar(s);

        if (!eraConectado) {
            registrar("[DISTRIBUIDO] Servidor '" + nombre + "' (" + peerId + ") se unió al cluster");
            ConsolaUtil.ok("Servidor amigo registrado: " + nombre + " (" + peerId + ")");
        } else {
            servidorPool.actualizarClientes(peerId, clientesPeer);
            ConsolaUtil.info("Heartbeat recibido de servidor: " + nombre + " (" + peerId + ")");
        }

        // Responder con la info de este servidor + lista de peers conocidos (gossip pull)
        List<String> misClientes = usuarioPool.obtenerUsuariosConectados().stream()
                .map(cu -> cu.getUsuario().getNombre())
                .collect(Collectors.toList());

        // Peers que el solicitante aún no conoce: todos los conectados excepto él mismo
        List<Map<String, Object>> peersConocidos = servidorPool.listarConectados().stream()
                .filter(p -> !p.getId().equals(peerId))
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("nombre", p.getNombre());
                    m.put("host",   p.getHost());
                    m.put("puerto", p.getPuerto());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> respData = new LinkedHashMap<>();
        respData.put("nombre", distribuidoService.obtenerNombre());
        respData.put("host",   distribuidoService.obtenerHost());
        respData.put("puerto", distribuidoService.obtenerPuerto());
        respData.put("clientesConectados", misClientes);
        respData.put("totalDocumentos", documentoService.contarDocumentos());
        respData.put("peersConocidos", peersConocidos);

        // Si es un servidor nuevo:
        // 1. Propagamos el gossip a todos nuestros peers
        // 2. Iniciamos sincronización de documentos bidireccional
        if (!eraConectado) {
            final ServidorAmigo finalS = s;
            CompletableFuture.runAsync(() -> {
                distribuidoService.propagarNuevoPeerATodos(finalS);
                // Sincronizar documentos: el nuevo servidor obtiene nuestros docs
                // y nosotros obtenemos los suyos
                distribuidoService.iniciarSincronizacionConPeer(finalS);
            });
        }

        return new HandleResult(mapper.writeValueAsString(ResponseDTO.ok(respData)), null);
    }

    // ── SERVIDOR / LIST ───────────────────────────────────────────────────────

    /** Lista todos los servidores conocidos (conectados y desconectados). */
    public String listarServidores() throws Exception {
        List<ServidorAmigoDTO> lista = servidorPool.listarTodos().stream()
                .map(ServidorAmigoDTO::desde)
                .collect(Collectors.toList());
        return mapper.writeValueAsString(ResponseDTO.ok(lista));
    }

    // ── SERVIDOR / GET ────────────────────────────────────────────────────────

    /**
     * Devuelve info o logs.
     *  data.servidorId = null → este servidor
     *  data.servidorId = "host:port" → reenvía al peer
     *  data.subtipo = "LOG" → logs en lugar de info
     */
    public HandleResult obtenerInfo(Map<String, Object> data) throws Exception {
        String servidorId = data != null ? (String) data.get("servidorId") : null;
        String subtipo    = data != null ? (String) data.getOrDefault("subtipo", "INFO") : "INFO";

        if (servidorId == null || servidorId.isBlank()) {
            return "LOG".equalsIgnoreCase(subtipo)
                    ? logControlador.listarLogs()
                    : obtenerInfoLocal();
        }

        Optional<ServidorAmigo> peerOpt = servidorPool.buscarPorId(servidorId);
        if (peerOpt.isEmpty() || !peerOpt.get().isConectado()) {
            return new HandleResult(
                    mapper.writeValueAsString(ResponseDTO.error("Servidor no disponible: " + servidorId)),
                    null);
        }
        ServidorAmigo peer = peerOpt.get();
        return "LOG".equalsIgnoreCase(subtipo)
                ? obtenerLogsRemotos(peer)
                : obtenerInfoRemota(peer);
    }

    // ── SERVIDOR / DELETE ─────────────────────────────────────────────────────

    /** Peer notificando su desconexión voluntaria. */
    public String recibirDesconexion(Map<String, Object> data) throws Exception {
        String nombre = (String) data.getOrDefault("nombre", "Desconocido");
        String host   = (String) data.getOrDefault("host", "?");
        int    puerto = toInt(data.get("puerto"));
        String peerId = host + ":" + puerto;

        if (servidorPool.isConectado(peerId)) {
            servidorPool.marcarDesconectado(peerId);
            registrar("[DISTRIBUIDO] Servidor '" + nombre + "' (" + peerId + ") se desconectó");
            ConsolaUtil.warn("Servidor amigo desconectado voluntariamente: " + nombre + " (" + peerId + ")");
        }
        return mapper.writeValueAsString(ResponseDTO.ok("Desconexion registrada"));
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private HandleResult obtenerInfoLocal() throws Exception {
        List<String> clientes = usuarioPool.obtenerUsuariosConectados().stream()
                .map(cu -> cu.getUsuario().getNombre())
                .collect(Collectors.toList());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("nombre",              distribuidoService.obtenerNombre());
        info.put("host",                distribuidoService.obtenerHost());
        info.put("puerto",              distribuidoService.obtenerPuerto());
        info.put("clientesConectados",  clientes);
        info.put("totalClientes",       clientes.size());
        info.put("totalDocumentos",     documentoService.contarDocumentos());
        info.put("servidoresConectados", servidorPool.listarConectados().size());
        info.put("servidoresDesconectados", servidorPool.listarDesconectados().size());

        return new HandleResult(mapper.writeValueAsString(ResponseDTO.ok(info)), null);
    }

    private HandleResult obtenerInfoRemota(ServidorAmigo peer) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type",   "SERVIDOR");
        req.put("method", "GET");
        req.put("data",   Map.of("subtipo", "INFO"));

        try (Socket socket = new Socket(peer.getHost(), peer.getPuerto());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {
            socket.setSoTimeout(10_000);
            out.println(mapper.writeValueAsString(req));
            String resp = in.readLine();
            return new HandleResult(
                    resp != null ? resp : mapper.writeValueAsString(ResponseDTO.error("Sin respuesta del peer")),
                    null);
        }
    }

    private HandleResult obtenerLogsRemotos(ServidorAmigo peer) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("type",   "SERVIDOR");
        req.put("method", "GET");
        req.put("data",   Map.of("subtipo", "LOG"));

        List<String> lineas = new ArrayList<>();
        try (Socket socket = new Socket(peer.getHost(), peer.getPuerto());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()))) {
            socket.setSoTimeout(15_000);
            out.println(mapper.writeValueAsString(req));

            String linea;
            while ((linea = in.readLine()) != null) {
                lineas.add(linea);
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = mapper.readValue(linea, Map.class);
                if ("FIN".equals(entry.get("status"))) break;
            }
        }
        return new HandleResult(lineas, null);
    }

    private void registrar(String mensaje) {
        try { logService.registrar(null, TipoLog.CONEXION, mensaje); }
        catch (Exception ignored) {}
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
    }

    /**
     * Verifica si el host:port dado corresponde a este mismo servidor.
     */
    private boolean esMismoServidor(String host, int port) {
        if (port != distribuidoService.obtenerPuerto()) return false;

        String miHost = distribuidoService.obtenerHost();
        String hostNorm = normalizarHost(host);
        String miHostNorm = normalizarHost(miHost);

        if (hostNorm.equals(miHostNorm)) return true;
        if (esHostLocal(host) && esHostLocal(miHost)) return true;

        try {
            java.net.InetAddress addrRemoto = java.net.InetAddress.getByName(host);
            java.net.InetAddress addrLocal = java.net.InetAddress.getByName(miHost);
            if (addrRemoto.equals(addrLocal)) return true;
            if (addrRemoto.isLoopbackAddress() || addrRemoto.equals(java.net.InetAddress.getLocalHost())) {
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
}
