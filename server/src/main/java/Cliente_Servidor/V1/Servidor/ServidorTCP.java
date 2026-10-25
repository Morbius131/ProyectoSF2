package Cliente_Servidor.V1.Servidor;

import Cliente_Servidor.V1.DTO.ConexionUsuarioDTO;
import Cliente_Servidor.V1.DTO.ResponseDTO;
import Cliente_Servidor.V1.Enums.TipoLog;
import Cliente_Servidor.V1.Enums.TipoProtocolo;
import Cliente_Servidor.V1.Handles.HandleResult;
import Cliente_Servidor.V1.Handles.RequestHandler;
import Cliente_Servidor.V1.Modelos.ConexionUsuario;
import Cliente_Servidor.V1.Pool.UsuarioConexionPool;
import Cliente_Servidor.V1.Servicios.LogService;
import Cliente_Servidor.V1.Servicios.ServidorDistribuidoService;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import org.springframework.stereotype.Component;

/**
 * Servidor TCP que mantiene una sesion completa por cliente.
 * Cada conexion vive en su propio hilo.
 * El pool limita el numero maximo de clientes simultaneos.
 * Se logea todo: conexion, cada peticion, errores y desconexion.
 */
@Component
public class ServidorTCP implements Servidor {

    private final RequestHandler            handler;
    private final UsuarioConexionPool       usuarioConexionPool;
    private final LogService                logService;
    private final ServidorDistribuidoService distribuidoService;
    private final ObjectMapper              mapper = new ObjectMapper();

    public ServidorTCP(RequestHandler handler,
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
        ServerSocket serverSocket = new ServerSocket(port);
        ConsolaUtil.encabezado("Servidor TCP iniciado en puerto " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            String ip = socket.getInetAddress().getHostAddress();
            ConsolaUtil.info("Nueva conexion TCP entrante desde " + ip);

            if (usuarioConexionPool.totalActivas() >= usuarioConexionPool.getMaxConexiones()) {
                ConsolaUtil.warn("Pool lleno. Rechazando conexion de " + ip);
                try {
                    logService.registrar(null, TipoLog.ERROR,
                            "[TCP] Pool lleno. Conexion rechazada desde " + ip);
                } catch (Exception ignored) {}
                try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    out.println(mapper.writeValueAsString(
                            ResponseDTO.error("Servidor lleno. Intente mas tarde.")));
                }
                socket.close();
                continue;
            }

            new Thread(() -> manejarCliente(socket)).start();
            ConsolaUtil.poolEstado(usuarioConexionPool.totalActivas(),
                    usuarioConexionPool.getMaxConexiones());
        }
    }

    private void manejarCliente(Socket socket) {
        ConexionUsuario conexion = null;
        String ip = socket.getInetAddress().getHostAddress();

        // Log de conexion entrante
        registrarLog(null, TipoLog.CONEXION, "[TCP] Conexion entrante desde " + ip);

        try (
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter    out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;

                // Log de peticion recibida (abreviada para no saturar en chunks)
                registrarLog(conexion != null ? conexion.getUsuario() : null, TipoLog.CONEXION,
                        "[TCP] Peticion recibida de " + ip + " → "
                        + (line.length() > 120 ? line.substring(0, 120) + "..." : line));

                HandleResult result = handler.handle(line);

                // Registrar usuario en pool en primera peticion que devuelva usuario
                if (result.getUsuario() != null && conexion == null) {
                    ConexionUsuarioDTO dto = new ConexionUsuarioDTO();
                    dto.setUsuario(result.getUsuario());
                    dto.setDireccionIp(ip);
                    dto.setProtocolo(TipoProtocolo.TCP);

                    try {
                        conexion = usuarioConexionPool.obtener(dto);
                        ConsolaUtil.conexion(ip, "TCP", result.getUsuario().getNombre());
                        ConsolaUtil.poolEstado(usuarioConexionPool.totalActivas(),
                                usuarioConexionPool.getMaxConexiones());
                        registrarLog(result.getUsuario(), TipoLog.CONEXION,
                                "[TCP] Usuario '" + result.getUsuario().getNombre()
                                + "' registrado en pool desde " + ip);
                        distribuidoService.notificarCambioClientes();
                    } catch (IllegalStateException ex) {
                        String json = mapper.writeValueAsString(ResponseDTO.error(ex.getMessage()));
                        out.println(json);
                        registrarLog(null, TipoLog.ERROR,
                                "[TCP] Pool lleno al registrar usuario desde " + ip + ": " + ex.getMessage());
                        ConsolaUtil.warn("Pool lleno al registrar usuario desde " + ip);
                        return;
                    }
                }

                // Enviar respuesta (una linea, multilinea, o streaming)
                if (result.isStreaming()) {
                    // Streaming progresivo: el callback escribe directamente al writer
                    registrarLog(conexion != null ? conexion.getUsuario() : null, TipoLog.CONEXION,
                            "[TCP] Iniciando respuesta streaming a " + ip);
                    result.getStreamingCallback().accept(out);
                    registrarLog(conexion != null ? conexion.getUsuario() : null, TipoLog.CONEXION,
                            "[TCP] Respuesta streaming completada a " + ip);
                } else if (result.isMultilinea()) {
                    for (String linea : result.getLineas()) {
                        out.println(linea);
                    }
                    registrarLog(conexion != null ? conexion.getUsuario() : null, TipoLog.CONEXION,
                            "[TCP] Respuesta multilinea a " + ip + " -> " + result.getLineas().size() + " lineas");
                } else {
                    String resp = result.getResponseJson();
                    registrarLog(conexion != null ? conexion.getUsuario() : null, TipoLog.CONEXION,
                            "[TCP] Respuesta a " + ip + " -> "
                            + (resp != null && resp.length() > 80 ? resp.substring(0, 80) + "..." : resp));
                    out.println(resp);
                }
            }

        } catch (Exception e) {
            ConsolaUtil.error("Error en sesion TCP [" + ip + "]: " + e.getMessage());
            registrarLog(conexion != null ? conexion.getUsuario() : null, TipoLog.ERROR,
                    "[TCP] Error en sesion de " + ip + ": " + e.getMessage());
        } finally {
            if (conexion != null) {
                String nombre = conexion.getUsuario().getNombre();
                usuarioConexionPool.liberar(nombre);
                ConsolaUtil.desconexion(ip, nombre);
                ConsolaUtil.poolEstado(usuarioConexionPool.totalActivas(),
                        usuarioConexionPool.getMaxConexiones());
                registrarLog(conexion.getUsuario(), TipoLog.CONEXION,
                        "[TCP] Usuario '" + nombre + "' desconectado desde " + ip);
                distribuidoService.notificarCambioClientes();
            } else {
                registrarLog(null, TipoLog.CONEXION,
                        "[TCP] Conexion cerrada (sin usuario registrado) desde " + ip);
            }
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void registrarLog(Cliente_Servidor.V1.Modelos.Usuario usuario,
                               TipoLog tipo, String mensaje) {
        try { logService.registrar(usuario, tipo, mensaje); }
        catch (Exception e) { ConsolaUtil.warn("No se pudo guardar log TCP: " + e.getMessage()); }
    }
}
