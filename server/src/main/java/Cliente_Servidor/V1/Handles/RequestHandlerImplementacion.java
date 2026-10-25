package Cliente_Servidor.V1.Handles;

import Cliente_Servidor.V1.Controladores.DocumentoControlador;
import Cliente_Servidor.V1.Controladores.EmotionController;
import Cliente_Servidor.V1.Controladores.LogControlador;
import Cliente_Servidor.V1.Controladores.ServidorAmigoControlador;
import Cliente_Servidor.V1.Controladores.UsuarioControlador;
import Cliente_Servidor.V1.DTO.RequestDTO;
import Cliente_Servidor.V1.DTO.ResponseDTO;
import Cliente_Servidor.V1.Enums.TipoLog;
import Cliente_Servidor.V1.Enums.TipoPeticion;
import Cliente_Servidor.V1.Servicios.LogService;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Despachador central de peticiones JSON.
 *
 * Protocolo de petición:
 * {
 *   "type":   "USUARIO|ARCHIVO|MENSAJE|LOG",
 *   "method": "POST|GET|LIST|DELETE|POST_CHUNK_INIT|POST_CHUNK|POST_CHUNK_END",
 *   "data":   { ... }
 * }
 */
@Component
public class RequestHandlerImplementacion implements RequestHandler {

    private final UsuarioControlador     usuarioControlador;
    private final DocumentoControlador   documentoControlador;
    private final LogControlador         logControlador;
    private final ServidorAmigoControlador servidorControlador;
    private final EmotionController      emotionControlador;
    private final LogService             logService;
    private final ObjectMapper           mapper = new ObjectMapper();

    public RequestHandlerImplementacion(UsuarioControlador usuarioControlador,
                                        DocumentoControlador documentoControlador,
                                        LogControlador logControlador,
                                        ServidorAmigoControlador servidorControlador,
                                        EmotionController emotionControlador,
                                        LogService logService) {
        this.usuarioControlador   = usuarioControlador;
        this.documentoControlador = documentoControlador;
        this.logControlador       = logControlador;
        this.servidorControlador  = servidorControlador;
        this.emotionControlador   = emotionControlador;
        this.logService           = logService;
    }

    @Override
    public HandleResult handle(String requestJson) {
        long inicio = System.currentTimeMillis();
        String typeStr   = "?";
        String methodStr = "?";

        try {
            RequestDTO request = mapper.readValue(requestJson, RequestDTO.class);
            typeStr   = request.getType()   != null ? request.getType().name()   : "null";
            methodStr = request.getMethod() != null ? request.getMethod().name() : "null";

            ConsolaUtil.info("Peticion recibida → type=" + typeStr + "  method=" + methodStr);

            registrarLog(null, TipoLog.CONEXION,
                    "ENTRADA type=" + typeStr + " method=" + methodStr
                    + " data=" + abreviarData(request.getData()));

            Map<String, Object> data = request.getData();
            String responseJson;
            HandleResult result;

            switch (request.getType()) {

                case USUARIO -> {
                    switch (request.getMethod()) {
                        case POST -> {
                            result = usuarioControlador.crearObtenerUsuario(data);
                            registrarLog(result.getUsuario(), TipoLog.CONEXION,
                                    "USUARIO POST → id=" + (result.getUsuario() != null
                                            ? result.getUsuario().getId() : "?")
                                    + " nombre=" + (result.getUsuario() != null
                                            ? result.getUsuario().getNombre() : "?"));
                            logSalida(typeStr, methodStr, result.getResponseJson(), inicio);
                            return result;
                        }
                        case LIST -> {
                            responseJson = usuarioControlador.listarConectados();
                            registrarLog(null, TipoLog.CONEXION, "USUARIO LIST → listado de conectados");
                        }
                        case DELETE -> {
                            responseJson = mapper.writeValueAsString(ResponseDTO.ok("Desconectado"));
                            registrarLog(null, TipoLog.CONEXION,
                                    "USUARIO DELETE → desconexion data=" + abreviarData(data));
                        }
                        default -> {
                            responseJson = err("Metodo no soportado para USUARIO: " + methodStr);
                            registrarLog(null, TipoLog.ERROR, "Metodo no soportado: " + typeStr + "/" + methodStr);
                        }
                    }
                    result = new HandleResult(responseJson, null);
                    logSalida(typeStr, methodStr, responseJson, inicio);
                    return result;
                }

                case ARCHIVO, MENSAJE -> {
                    switch (request.getMethod()) {
                        case POST -> {
                            responseJson = documentoControlador.recibirDocumento(data);
                            registrarLog(null, request.getType() == TipoPeticion.MENSAJE ? TipoLog.MENSAJE : TipoLog.ARCHIVO,
                                    typeStr + " POST → nombre=" + safeGet(data, "nombre")
                                    + " tamanio=" + safeGet(data, "tamanio"));
                        }
                        case POST_CHUNK_INIT -> {
                            responseJson = documentoControlador.iniciarChunk(data);
                            registrarLog(null, TipoLog.ARCHIVO,
                                    "CHUNK_INIT → nombre=" + safeGet(data, "nombre")
                                    + " totalChunks=" + safeGet(data, "totalChunks"));
                        }
                        case POST_CHUNK -> {
                            responseJson = documentoControlador.recibirChunk(data);
                            registrarLog(null, TipoLog.ARCHIVO,
                                    "CHUNK → uploadId=" + safeGet(data, "uploadId")
                                    + " idx=" + safeGet(data, "chunkIndex")
                                    + "/" + safeGet(data, "totalChunks"));
                        }
                        case POST_CHUNK_END -> {
                            responseJson = documentoControlador.finalizarChunk(data);
                            registrarLog(null, TipoLog.ARCHIVO,
                                    "CHUNK_END → uploadId=" + safeGet(data, "uploadId"));
                        }
                        case LIST -> {
                            if (request.getType() == TipoPeticion.ARCHIVO) {
                                responseJson = documentoControlador.listarDocumentos(data);
                                registrarLog(null, TipoLog.ARCHIVO, typeStr + " LIST → listado de archivos"
                                        + (data != null && data.get("usuarioNombre") != null
                                                ? " usuario=" + data.get("usuarioNombre") : ""));
                            } else {
                                responseJson = documentoControlador.listarMensajes(data);
                                registrarLog(null, TipoLog.MENSAJE, typeStr + " LIST → listado de mensajes"
                                        + (data != null && data.get("usuarioNombre") != null
                                                ? " usuario=" + data.get("usuarioNombre") : ""));
                            }
                        }
                        case LIST_SYNC -> {
                            responseJson = documentoControlador.listarParaSincronizacion();
                            registrarLog(null, TipoLog.CONEXION,
                                    "ARCHIVO LIST_SYNC → sincronización entre servidores");
                        }
                        case GET -> {
                            String accion = data != null ? (String) data.get("accion") : null;
                            if (request.getType() == TipoPeticion.MENSAJE) {
                                result = new HandleResult(documentoControlador.obtenerMensaje(data), null);
                                registrarLog(null, TipoLog.MENSAJE,
                                        "MENSAJE GET → id=" + safeGet(data, "id"));
                                logSalida(typeStr, methodStr, result.getResponseJson(), inicio);
                                return result;
                            }
                            if ("SOLO_HASH".equalsIgnoreCase(accion)) {
                                responseJson = documentoControlador.obtenerSoloHash(data);
                                registrarLog(null, TipoLog.ARCHIVO,
                                        "ARCHIVO GET SOLO_HASH → id=" + safeGet(data, "id"));
                            } else if ("ENCRIPTADO".equalsIgnoreCase(accion)) {
                                result = documentoControlador.obtenerDocumentoEncriptadoChunks(data);
                                registrarLog(null, TipoLog.ARCHIVO,
                                        "ARCHIVO GET ENCRIPTADO (chunks) → id=" + safeGet(data, "id"));
                                logSalida(typeStr, methodStr, "MULTILINEA-ENC", inicio);
                                return result;
                                } else {
                                result = documentoControlador.obtenerDocumentoChunks(data);
                                registrarLog(null, TipoLog.ARCHIVO,
                                        "ARCHIVO GET NORMAL (chunks) → id=" + safeGet(data, "id"));
                                logSalida(typeStr, methodStr, "MULTILINEA-ORIG", inicio);
                                return result;
                            }
                        }
                        default -> {
                            responseJson = err("Metodo no soportado para " + typeStr + ": " + methodStr);
                            registrarLog(null, TipoLog.ERROR,
                                    "Metodo no soportado: " + typeStr + "/" + methodStr);
                        }
                    }
                    result = new HandleResult(responseJson, null);
                    logSalida(typeStr, methodStr, responseJson, inicio);
                    return result;
                }

                case LOG -> {
                    switch (request.getMethod()) {
                        case LIST -> {
                            result = logControlador.listarLogs();
                            registrarLog(null, TipoLog.CONEXION, "LOG LIST");
                            logSalida(typeStr, methodStr, "MULTILINEA-LOGS", inicio);
                            return result;
                        }
                        default -> {
                            responseJson = err("Metodo no soportado para LOG: " + methodStr);
                            registrarLog(null, TipoLog.ERROR, "Metodo no soportado: LOG/" + methodStr);
                        }
                    }
                    result = new HandleResult(responseJson, null);
                    logSalida(typeStr, methodStr, responseJson, inicio);
                    return result;
                }

                case SERVIDOR -> {
                    switch (request.getMethod()) {
                        case POST -> {
                            result = servidorControlador.recibirAnuncio(data);
                            registrarLog(null, TipoLog.CONEXION,
                                    "SERVIDOR POST → anuncio de " + safeGet(data, "nombre"));
                            logSalida(typeStr, methodStr, result.getResponseJson(), inicio);
                            return result;
                        }
                        case LIST -> {
                            responseJson = servidorControlador.listarServidores();
                            registrarLog(null, TipoLog.CONEXION, "SERVIDOR LIST");
                        }
                        case GET -> {
                            result = servidorControlador.obtenerInfo(data);
                            registrarLog(null, TipoLog.CONEXION,
                                    "SERVIDOR GET subtipo=" + safeGet(data, "subtipo")
                                    + " servidorId=" + safeGet(data, "servidorId"));
                            logSalida(typeStr, methodStr, "SERVIDOR-GET", inicio);
                            return result;
                        }
                        case DELETE -> {
                            responseJson = servidorControlador.recibirDesconexion(data);
                            registrarLog(null, TipoLog.CONEXION,
                                    "SERVIDOR DELETE → desconexion de " + safeGet(data, "nombre"));
                        }
                        default -> {
                            responseJson = err("Metodo no soportado para SERVIDOR: " + methodStr);
                            registrarLog(null, TipoLog.ERROR,
                                    "Metodo no soportado: SERVIDOR/" + methodStr);
                        }
                    }
                    result = new HandleResult(responseJson, null);
                    logSalida(typeStr, methodStr, responseJson, inicio);
                    return result;
                }

                case EMOCION -> {
                    switch (request.getMethod()) {
                        case POST -> {
                            responseJson = emotionControlador.identificarEmocion(data);
                            registrarLog(null, TipoLog.CONEXION,
                                    "EMOCION POST → identificar imagen");
                        }
                        case LIST -> {
                            responseJson = emotionControlador.listarImagenesRostro();
                            registrarLog(null, TipoLog.CONEXION, "EMOCION LIST → listar imágenes de rostro");
                        }
                        case LIST_SYNC -> {
                            responseJson = emotionControlador.listarImagenesRostroParaSync();
                            registrarLog(null, TipoLog.CONEXION, "EMOCION LIST_SYNC → sincronización imágenes rostro");
                        }
                        case GET -> {
                            responseJson = emotionControlador.identificarPorId(data);
                            registrarLog(null, TipoLog.CONEXION,
                                    "EMOCION GET → identificar por id=" + safeGet(data, "id"));
                        }
                        case GET_IMAGE -> {
                            responseJson = emotionControlador.obtenerImagenRostroConDatos(data);
                            registrarLog(null, TipoLog.CONEXION,
                                    "EMOCION GET_IMAGE → obtener imagen id=" + safeGet(data, "id"));
                        }
                        default -> {
                            responseJson = err("Metodo no soportado para EMOCION: " + methodStr);
                            registrarLog(null, TipoLog.ERROR,
                                    "Metodo no soportado: EMOCION/" + methodStr);
                        }
                    }
                    result = new HandleResult(responseJson, null);
                    logSalida(typeStr, methodStr, responseJson, inicio);
                    return result;
                }

                default -> {
                    String msg = "Tipo de peticion desconocido: " + typeStr;
                    registrarLog(null, TipoLog.ERROR, msg);
                    result = new HandleResult(err(msg), null);
                    logSalida(typeStr, methodStr, result.getResponseJson(), inicio);
                    return result;
                }
            }

        } catch (Exception e) {
            ConsolaUtil.error("Error manejando peticion: " + e.getMessage());
            try {
                registrarLog(null, TipoLog.ERROR,
                        "EXCEPCION type=" + typeStr + " method=" + methodStr + " → " + e.getMessage());
                String json = mapper.writeValueAsString(ResponseDTO.error(e.getMessage()));
                return new HandleResult(json, null);
            } catch (Exception ex) {
                return new HandleResult("{\"status\":\"ERROR\",\"message\":\"Error critico\"}", null);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void registrarLog(Cliente_Servidor.V1.Modelos.Usuario usuario,
                               TipoLog tipo, String mensaje) {
        try { logService.registrar(usuario, tipo, mensaje); }
        catch (Exception e) { ConsolaUtil.warn("No se pudo guardar log: " + e.getMessage()); }
    }

    private void logSalida(String type, String method, String responseJson, long inicio) {
        long ms = System.currentTimeMillis() - inicio;
        ConsolaUtil.info("Respuesta → type=" + type + " method=" + method
                + " [" + ms + " ms] " + abreviar(responseJson, 80));
    }

    private String err(String msg) {
        try { return mapper.writeValueAsString(ResponseDTO.error(msg)); }
        catch (Exception e) { return "{\"status\":\"ERROR\",\"message\":\"" + msg + "\"}"; }
    }

    private static String safeGet(Map<String, Object> data, String key) {
        if (data == null) return "null";
        Object v = data.get(key);
        return v != null ? v.toString() : "null";
    }

    private static String abreviarData(Map<String, Object> data) {
        if (data == null) return "null";
        String s = data.toString();
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }

    private static String abreviar(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
