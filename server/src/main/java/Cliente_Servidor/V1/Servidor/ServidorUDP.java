package Cliente_Servidor.V1.Servidor;

import Cliente_Servidor.V1.DTO.ConexionUsuarioDTO;
import Cliente_Servidor.V1.Enums.TipoLog;
import Cliente_Servidor.V1.Enums.TipoProtocolo;
import Cliente_Servidor.V1.Handles.HandleResult;
import Cliente_Servidor.V1.Handles.RequestHandler;
import Cliente_Servidor.V1.Pool.UsuarioConexionPool;
import Cliente_Servidor.V1.Servicios.LogService;
import Cliente_Servidor.V1.Servicios.ServidorDistribuidoService;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

/**
 * Servidor UDP puro — sin ACKs para chunks.
 *
 * POST_CHUNK: se procesa y NO se responde nada al cliente.
 *             El cliente dispara sin esperar, máxima velocidad.
 *
 * POST_CHUNK_INIT / POST_CHUNK_END / todo lo demás:
 *             comportamiento normal request→response.
 *
 * El ChunkBuffer ya soporta chunks desordenados (escribe por índice),
 * así que UDP sin orden no es problema.
 */
@Component
public class ServidorUDP implements Servidor {

    private static final int UDP_SEND_PAUSE_EVERY = 100;
    private static final int UDP_SEND_PAUSE_MS = 2;

    private final RequestHandler            handler;
    private final UsuarioConexionPool       usuarioConexionPool;
    private final LogService                logService;
    private final ServidorDistribuidoService distribuidoService;
    private final ObjectMapper              mapper = new ObjectMapper();
    private DatagramSocket                  socket;

    public ServidorUDP(RequestHandler handler,
                       UsuarioConexionPool usuarioConexionPool,
                       LogService logService,
                       ServidorDistribuidoService distribuidoService) {
        this.handler             = handler;
        this.usuarioConexionPool = usuarioConexionPool;
        this.logService          = logService;
        this.distribuidoService  = distribuidoService;
    }

    @Override
    public void start(int port) throws Exception {
        this.socket = new DatagramSocket(port);
        // Buffer recv grande para absorber ráfagas de chunks sin drops del SO
        this.socket.setReceiveBufferSize(32 * 1024 * 1024); // 32 MB
        this.socket.setSendBufferSize(32 * 1024 * 1024);    // 32 MB
        ConsolaUtil.encabezado("Servidor UDP escuchando en puerto " + port + " (puro, sin ACK)");

        // Pool amplio: cada datagrama se procesa en paralelo
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.max(16, Runtime.getRuntime().availableProcessors() * 4));

        while (true) {
            byte[]         buffer = new byte[524288]; // 512 KB
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            byte[]      data      = Arrays.copyOf(packet.getData(), packet.getLength());
            InetAddress address   = packet.getAddress();
            int         clientPort = packet.getPort();
            String      clientIp  = address.getHostAddress();

            pool.submit(() -> procesarPaquete(data, address, clientPort, clientIp));
        }
    }

    private void procesarPaquete(byte[] data, InetAddress address, int clientPort, String clientIp) {
        try {
            String payload = new String(data);

            // Detectar POST_CHUNK (sin INIT ni END) para no responder nada
            boolean esChunkPuro = payload.contains("\"POST_CHUNK\"")
                                && !payload.contains("\"POST_CHUNK_INIT\"")
                                && !payload.contains("\"POST_CHUNK_END\"");

            if (esChunkPuro) {
                // Procesar silenciosamente — sin respuesta al cliente
                handler.handle(payload);
                return;
            }

            // Resto de peticiones: log + handle + respuesta normal
            logService.registrar(null, TipoLog.CONEXION,
                    "[UDP] Peticion desde " + clientIp + ":" + clientPort
                    + " payload=" + abreviar(payload, 120));

            HandleResult result = handler.handle(payload);

            // Registro en pool de conexiones
            if (result.getUsuario() != null
                    && !usuarioConexionPool.estaConectado(result.getUsuario().getNombre())) {
                try {
                    ConexionUsuarioDTO dto = new ConexionUsuarioDTO();
                    dto.setUsuario(result.getUsuario());
                    dto.setDireccionIp(clientIp);
                    dto.setProtocolo(TipoProtocolo.UDP);
                    usuarioConexionPool.obtener(dto);
                    ConsolaUtil.conexion(clientIp, "UDP", result.getUsuario().getNombre());
                    ConsolaUtil.poolEstado(usuarioConexionPool.totalActivas(),
                            usuarioConexionPool.getMaxConexiones());
                    logService.registrar(result.getUsuario(), TipoLog.CONEXION,
                            "[UDP] Usuario '" + result.getUsuario().getNombre()
                            + "' conectado desde " + clientIp);
                    distribuidoService.notificarCambioClientes();
                } catch (IllegalStateException ex) {
                    ConsolaUtil.warn("Pool lleno, rechazando conexion UDP de " + clientIp);
                }
            }

            // Desconexión
            if (result.getUsuario() != null && payload.contains("\"DELETE\"")) {
                String nombre = result.getUsuario().getNombre();
                if (usuarioConexionPool.estaConectado(nombre)) {
                    usuarioConexionPool.liberar(nombre);
                    ConsolaUtil.desconexion(clientIp, nombre);
                    ConsolaUtil.poolEstado(usuarioConexionPool.totalActivas(),
                            usuarioConexionPool.getMaxConexiones());
                    distribuidoService.notificarCambioClientes();
                }
            }

            // Respuesta
            if (result.isMultilinea()) {
                // Enviamos cada línea en un datagrama separado para evitar el
                // límite de 64 KB por paquete UDP (archivos grandes superan ese tope
                // si se concatenan todos los chunks en un solo paquete).
                // El cliente lee datagramas en bucle hasta recibir {"status":"FIN"}.
                List<String> lineas = result.getLineas();
                logService.registrar(result.getUsuario(), TipoLog.CONEXION,
                        "[UDP] Respuesta multilinea a " + clientIp + " lineas=" + lineas.size());
                int sent = 0;
                for (String linea : lineas) {
                    byte[] lb = (linea + "\n").getBytes();
                    socket.send(new DatagramPacket(lb, lb.length, address, clientPort));
                    sent++;
                    if (sent % UDP_SEND_PAUSE_EVERY == 0) {
                        try {
                            Thread.sleep(UDP_SEND_PAUSE_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } else {
                byte[] respBytes = result.getResponseJson().getBytes();
                logService.registrar(result.getUsuario(), TipoLog.CONEXION,
                        "[UDP] Resp a " + clientIp + " " + abreviar(result.getResponseJson(), 80));
                socket.send(new DatagramPacket(respBytes, respBytes.length, address, clientPort));
            }

        } catch (Exception e) {
            ConsolaUtil.error("Error procesando paquete UDP: " + e.getMessage());
            try {
                byte[] err = ("{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}").getBytes();
                socket.send(new DatagramPacket(err, err.length, address, clientPort));
            } catch (Exception ignored) {}
        }
    }

    private static String abreviar(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
