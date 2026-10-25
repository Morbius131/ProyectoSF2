package Cliente_Servidor.V1.Controladores;

import Cliente_Servidor.V1.Chunk.ChunkBuffer;
import Cliente_Servidor.V1.Chunk.ChunkRegistry;
import Cliente_Servidor.V1.DTO.DocumentoDTO;
import Cliente_Servidor.V1.DTO.ResponseDTO;
import Cliente_Servidor.V1.DTO.UsuarioDTO;
import Cliente_Servidor.V1.Enums.TipoLog;
import Cliente_Servidor.V1.Enums.TipoUsuario;
import Cliente_Servidor.V1.Handles.HandleResult;
import Cliente_Servidor.V1.Modelos.ArchivoChunk;
import Cliente_Servidor.V1.Modelos.ArchivoChunkEncriptado;
import Cliente_Servidor.V1.Modelos.Documento;
import Cliente_Servidor.V1.Modelos.Usuario;
import Cliente_Servidor.V1.Servicios.DocumentoService;
import Cliente_Servidor.V1.Servicios.LogService;
import Cliente_Servidor.V1.Servicios.ServidorDistribuidoService;
import Cliente_Servidor.V1.Servicios.UsuarioService;
import Cliente_Servidor.V1.Util.ConsolaUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

@Component
public class DocumentoControlador {

    private final DocumentoService           documentoService;
    private final UsuarioService             usuarioService;
    private final LogService                 logService;
    private final ChunkRegistry              chunkRegistry;
    private final ServidorDistribuidoService distribuidoService;
    private final ObjectMapper               mapper = new ObjectMapper();

    // Hilo dedicado al procesado pesado (hash + encriptar + BD) para no bloquear la respuesta
    private final ExecutorService procesadorAsync =
            Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

    public DocumentoControlador(DocumentoService documentoService,
                                UsuarioService usuarioService,
                                LogService logService,
                                ChunkRegistry chunkRegistry,
                                ServidorDistribuidoService distribuidoService) {
        this.documentoService  = documentoService;
        this.usuarioService    = usuarioService;
        this.logService        = logService;
        this.chunkRegistry     = chunkRegistry;
        this.distribuidoService = distribuidoService;
    }

    // ── POST directo ──────────────────────────────────────────────────────────

    public String recibirDocumento(Map<String, Object> data) throws JsonProcessingException {
        DocumentoDTO dto = mapper.convertValue(data, DocumentoDTO.class);
        Long usuarioId = dto.getUsuarioId();
        if (usuarioId == null)
            return mapper.writeValueAsString(ResponseDTO.error("usuarioId es requerido"));

        // Solo verificar idUnico si es replicación externa (evita duplicados en sincronización)
        boolean esExterno = "EXTERNO".equalsIgnoreCase(dto.getTipoPropietario());
        if (esExterno && dto.getIdUnico() != null) {
            if (documentoService.existePorIdUnico(dto.getIdUnico())) {
                ConsolaUtil.info("Documento ya existe (idUnico=" + dto.getIdUnico() + "), omitiendo replicación");
                return mapper.writeValueAsString(ResponseDTO.ok("ya_existe"));
            }
        }

        UsuarioDTO usuarioDTO = new UsuarioDTO();
        usuarioDTO.setNombre(dto.getUsuarioNombre() != null ? dto.getUsuarioNombre() : "desconocido");
        usuarioDTO.setTipoUsuario(TipoUsuario.NORMAL);
        Usuario usuario = usuarioService.crearObtenerUsuario(usuarioDTO);

        Documento guardado = documentoService.procesarYGuardar(dto, usuario);
        logService.registrar(usuario, TipoLog.valueOf(guardado.getTipo().name()),
                "Documento recibido: " + guardado.getNombreVisible()
                + (guardado.getDestinatario() != null
                        ? " → destinatario=" + guardado.getDestinatario() : ""));

        // Replicar a peers sólo si es documento LOCAL (evita bucle de replicación)
        boolean esLocal = dto.getTipoPropietario() == null
                || "LOCAL".equalsIgnoreCase(dto.getTipoPropietario());
        if (esLocal) {
            // Agregar idUnico generado para replicación
            Map<String, Object> dataConId = new LinkedHashMap<>(data);
            dataConId.put("idUnico", guardado.getIdUnico());
            String destinatario = dto.getDestinatario();
            distribuidoService.replicarDocumento(dataConId, destinatario);
        }

        return mapper.writeValueAsString(ResponseDTO.ok("recibido"));
    }

    // ── POST_CHUNK_INIT ───────────────────────────────────────────────────────

    public String iniciarChunk(Map<String, Object> data) throws Exception {
        String nombre          = (String) data.get("nombre");
        String extension       = (String) data.get("extension");
        long   tamanio         = toLong(data.get("tamanio"));
        int    totalChunks     = toInt(data.get("totalChunks"));
        long   usuarioId       = toLong(data.get("usuarioId"));
        String usuarioNombre   = (String) data.getOrDefault("usuarioNombre", "desconocido");
        String tipoPropietario = (String) data.getOrDefault("tipoPropietario", "LOCAL");

        if (nombre == null || extension == null || totalChunks <= 0)
            return mapper.writeValueAsString(
                    ResponseDTO.error("nombre, extension y totalChunks son requeridos"));

        int chunkSizeBytes = toInt(data.get("chunkSize"));
        if (chunkSizeBytes <= 0) {
            chunkSizeBytes = (int) Math.ceil((double) tamanio / totalChunks);
        }
        if (chunkSizeBytes <= 0) {
            chunkSizeBytes = 32768;
        }
        String destinatario = (String) data.get("destinatario");
        String idUnico = (String) data.get("idUnico");
        String uploadId = ChunkRegistry.nuevoUploadId();
        ChunkBuffer buffer = new ChunkBuffer(uploadId, nombre, extension,
                tamanio, totalChunks, usuarioId, usuarioNombre, tipoPropietario,
                destinatario, idUnico, chunkSizeBytes);
        chunkRegistry.registrar(buffer);

        ConsolaUtil.info("Inicio chunked uploadId=" + uploadId
                + " archivo=" + nombre + "." + extension
                + " chunks=" + totalChunks + " tamanio=" + tamanio
                + " destinatario=" + (destinatario != null ? destinatario : "(null)"));

        return mapper.writeValueAsString(ResponseDTO.ok(Map.of(
                "uploadId",    uploadId,
                "totalChunks", totalChunks,
                "mensaje",     "Listo para recibir chunks")));
    }

    // ── POST_CHUNK ────────────────────────────────────────────────────────────

    public String recibirChunk(Map<String, Object> data) throws Exception {
        String uploadId  = (String) data.get("uploadId");
        int    chunkIndex = toInt(data.get("chunkIndex"));
        String contenido = (String) data.get("contenido");

        if (uploadId == null || contenido == null)
            return mapper.writeValueAsString(
                    ResponseDTO.error("uploadId y contenido son requeridos"));

        ChunkBuffer buffer = chunkRegistry.obtener(uploadId);
        if (buffer == null)
            return mapper.writeValueAsString(
                    ResponseDTO.error("uploadId no encontrado: " + uploadId));

        byte[] chunkBytes = Base64.getDecoder().decode(contenido);
        // writeChunk escribe en la posición correcta (soporta UDP desordenado)
        buffer.writeChunk(chunkIndex, chunkBytes);

        // No logueamos cada chunk para no penalizar rendimiento en UDP puro
        return mapper.writeValueAsString(ResponseDTO.ok(Map.of(
                "received", buffer.getChunksRecibidos(),
                "total",    buffer.getTotalChunks(),
                "bytes",    buffer.getBytesEscritos())));
    }

    // ── POST_CHUNK_END ────────────────────────────────────────────────────────

    public String finalizarChunk(Map<String, Object> data) throws Exception {
        String uploadId = (String) data.get("uploadId");
        if (uploadId == null)
            return mapper.writeValueAsString(ResponseDTO.error("uploadId es requerido"));

        ChunkBuffer buffer = chunkRegistry.obtener(uploadId);
        if (buffer == null)
            return mapper.writeValueAsString(
                    ResponseDTO.error("uploadId no encontrado: " + uploadId));

        ConsolaUtil.info("CHUNK_END recibido uploadId=" + uploadId
                + " chunks=" + buffer.getChunksRecibidos() + "/" + buffer.getTotalChunks()
                + " — procesando SINCRONO (cliente espera)");

        // UDP fire-and-forget: POST_CHUNK_END puede llegar antes que algunos chunks
        if (!buffer.estaCompleto()) {
            ConsolaUtil.warn("UDP: faltan chunks al recibir END (" + buffer.getChunksRecibidos()
                    + "/" + buffer.getTotalChunks() + "), esperando 2s...");
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }

        // Procesar SINCRONO: el cliente espera hasta que se guarde en BD
        try {
            UsuarioDTO usuarioDTO = new UsuarioDTO();
            usuarioDTO.setNombre(buffer.getUsuarioNombre());
            usuarioDTO.setTipoUsuario(TipoUsuario.NORMAL);
            Usuario usuario = usuarioService.crearObtenerUsuario(usuarioDTO);

            Documento guardado = documentoService.procesarDesdeChunkBuffer(buffer, usuario);

            // Si retorna null, el documento ya existía (duplicado por hash)
            if (guardado == null) {
                chunkRegistry.eliminar(uploadId);
                return mapper.writeValueAsString(ResponseDTO.ok(Map.of(
                        "mensaje", "Documento ya existe",
                        "duplicado", true)));
            }

            logService.registrar(usuario, TipoLog.ARCHIVO,
                    "Archivo grande recibido: " + guardado.getNombreVisible()
                    + " (" + buffer.getBytesEscritos() + " bytes)");
            chunkRegistry.eliminar(uploadId);
            ConsolaUtil.ok("Procesado OK: " + guardado.getNombreVisible());

            // Replicar a peers en BACKGROUND (el cliente no espera la replicación)
            if ("LOCAL".equalsIgnoreCase(buffer.getTipoPropietario())) {
                final Documento docFinal = guardado;
                final ChunkBuffer bufFinal = buffer;
                CompletableFuture.runAsync(() -> replicarArchivoGrande(docFinal, bufFinal), procesadorAsync);
            }

            return mapper.writeValueAsString(ResponseDTO.ok(Map.of(
                    "mensaje", "Archivo guardado correctamente",
                    "id", guardado.getId(),
                    "nombre", guardado.getNombreVisible())));

        } catch (Exception e) {
            ConsolaUtil.error("Error procesando uploadId=" + uploadId + ": " + e.getMessage());
            buffer.delete();
            chunkRegistry.eliminar(uploadId);
            return mapper.writeValueAsString(ResponseDTO.error("Error al guardar: " + e.getMessage()));
        }
    }

    private void replicarArchivoGrande(Documento guardado, ChunkBuffer buffer) {
        try {
            // Buscar el archivo en disco (ya guardado) en lugar de cargar desde BD
            java.io.File dirOriginal = new java.io.File("./archivos_originales/");
            String patron = buffer.getNombre() + "." + buffer.getExtension();
            java.io.File archivoOriginal = null;

            // Buscar el archivo más reciente que coincida
            if (dirOriginal.exists()) {
                java.io.File[] archivos = dirOriginal.listFiles((dir, name) -> name.endsWith(patron));
                if (archivos != null && archivos.length > 0) {
                    // Tomar el más reciente
                    archivoOriginal = archivos[archivos.length - 1];
                }
            }

            if (archivoOriginal == null || !archivoOriginal.exists()) {
                ConsolaUtil.warn("Archivo original no encontrado para replicar: " + patron);
                return;
            }

            java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("nombre",        buffer.getNombre());
            meta.put("extension",     buffer.getExtension());
            meta.put("usuarioNombre", buffer.getUsuarioNombre());
            meta.put("usuarioId",     buffer.getUsuarioId());
            if (buffer.getDestinatario() != null && !buffer.getDestinatario().isBlank()) {
                meta.put("destinatario", buffer.getDestinatario());
            }

            // Leer del disco en streaming (no carga todo en memoria)
            distribuidoService.replicarArchivoGrandeDesdeArchivo(meta, archivoOriginal);
        } catch (Exception e) {
            ConsolaUtil.warn("No se pudo replicar archivo grande: " + e.getMessage());
        }
    }

    // ── GET / LIST ────────────────────────────────────────────────────────────

    public String listarDocumentos(Map<String, Object> data) throws JsonProcessingException {
        String usuarioNombre = data != null ? (String) data.get("usuarioNombre") : null;
        List<DocumentoDTO> lista = documentoService.listarArchivosComoDTO(usuarioNombre);
        return mapper.writeValueAsString(ResponseDTO.ok(lista));
    }

    /** Lista todos los documentos con metadatos (para sincronización entre servidores). */
    public String listarParaSincronizacion() throws JsonProcessingException {
        List<DocumentoDTO> lista = documentoService.listarParaSincronizacion();
        ConsolaUtil.info("LIST_SYNC → " + lista.size() + " documentos para sincronización");
        return mapper.writeValueAsString(ResponseDTO.ok(lista));
    }

    public String listarMensajes(Map<String, Object> data) throws JsonProcessingException {
        String usuarioNombre = data != null ? (String) data.get("usuarioNombre") : null;
        List<DocumentoDTO> lista = documentoService.listarMensajesComoDTO(usuarioNombre);
        return mapper.writeValueAsString(ResponseDTO.ok(lista));
    }

    /** Devuelve solo metadatos (sin contenido) del documento. */
    public String obtenerSoloHash(Map<String, Object> data) throws JsonProcessingException {
        Long id = Long.valueOf(data.get("id").toString());
        Documento doc = documentoService.buscarPorId(id);
        return mapper.writeValueAsString(ResponseDTO.ok(Map.of(
                "id",   doc.getId(),
                "nombreVisible", doc.getNombreVisible(),
                "hash", doc.getHash() != null ? doc.getHash() : "")));
    }

    public String obtenerMensaje(Map<String, Object> data) throws JsonProcessingException {
        Long id = Long.valueOf(data.get("id").toString());
        DocumentoDTO mensaje = documentoService.buscarMensajeComoDTO(id);
        Map<String, Object> respData = new LinkedHashMap<>();
        respData.put("id", mensaje.getId());
        respData.put("idUnico", mensaje.getIdUnico());
        respData.put("nombreVisible", mensaje.getNombreVisible());
        respData.put("asunto", mensaje.getAsunto());
        respData.put("contenido", mensaje.getContenido() != null ? mensaje.getContenido() : "");
        respData.put("hash", mensaje.getHash() != null ? mensaje.getHash() : "");
        return mapper.writeValueAsString(ResponseDTO.ok(respData));
    }

    /**
     * Opción 5 — Envía el documento normal en chunks desde archivo_chunks_original.
     * Protocolo multilinea (streaming progresivo para evitar OOM):
     *   Línea 1 : {"status":"OK","totalChunks":N,"nombreVisible":"...","hash":"..."}
     *   Líneas  : {"status":"CHUNK","numero":i,"datos":"<base64>"}  × N
     *   Última  : {"status":"FIN"}
     */
    public HandleResult obtenerDocumentoChunks(Map<String, Object> data) throws Exception {
        Long id = Long.valueOf(data.get("id").toString());
        Documento doc = documentoService.buscarPorId(id);
        int totalChunks = documentoService.contarChunksOriginal(id);

        if (totalChunks == 0) {
            String err = mapper.writeValueAsString(
                    ResponseDTO.error("No hay chunks originales para el documento " + id));
            return new HandleResult(err, null);
        }

        ConsolaUtil.info("GET NORMAL id=" + id + " → " + totalChunks + " chunks (streaming)");

        final String nombreVisible = doc.getNombreVisible();
        final String hash = doc.getHash() != null ? doc.getHash() : "";

        // Retornar callback de streaming que lee chunks uno a uno de la BD
        return new HandleResult(out -> {
            try {
                // Línea 1: cabecera
                Map<String, Object> cabecera = new LinkedHashMap<>();
                cabecera.put("status", "OK");
                cabecera.put("totalChunks", totalChunks);
                cabecera.put("nombreVisible", nombreVisible);
                cabecera.put("hash", hash);
                out.println(mapper.writeValueAsString(cabecera));

                // Leer y enviar chunks uno a uno (no carga todo en memoria)
                for (int i = 0; i < totalChunks; i++) {
                    ArchivoChunk chunk = documentoService.obtenerChunkOriginal(id, i);
                    if (chunk != null) {
                        Map<String, Object> chunkJson = new LinkedHashMap<>();
                        chunkJson.put("status", "CHUNK");
                        chunkJson.put("numero", chunk.getNumeroChunk());
                        chunkJson.put("datos", Base64.getEncoder().encodeToString(chunk.getDatos()));
                        out.println(mapper.writeValueAsString(chunkJson));
                    }
                }

                // Línea final
                out.println(mapper.writeValueAsString(Map.of("status", "FIN")));
            } catch (Exception e) {
                ConsolaUtil.error("Error en streaming chunks: " + e.getMessage());
            }
        });
    }

    /**
     * Opción 7 — Envía el documento encriptado en chunks desde archivo_chunks_encriptado.
     * Mismo protocolo multilinea que obtenerDocumentoChunks (streaming progresivo).
     */
    public HandleResult obtenerDocumentoEncriptadoChunks(Map<String, Object> data) throws Exception {
        Long id = Long.valueOf(data.get("id").toString());
        Documento doc = documentoService.buscarPorId(id);
        int totalChunks = documentoService.contarChunksEncriptado(id);

        if (totalChunks == 0) {
            String err = mapper.writeValueAsString(
                    ResponseDTO.error("No hay chunks encriptados para el documento " + id));
            return new HandleResult(err, null);
        }

        ConsolaUtil.info("GET ENCRIPTADO id=" + id + " → " + totalChunks + " chunks (streaming)");

        final String nombreVisible = doc.getNombreVisible();
        final String hash = doc.getHash() != null ? doc.getHash() : "";

        // Retornar callback de streaming que lee chunks uno a uno de la BD
        return new HandleResult(out -> {
            try {
                Map<String, Object> cabecera = new LinkedHashMap<>();
                cabecera.put("status", "OK");
                cabecera.put("totalChunks", totalChunks);
                cabecera.put("nombreVisible", nombreVisible);
                cabecera.put("hash", hash);
                out.println(mapper.writeValueAsString(cabecera));

                // Leer y enviar chunks uno a uno
                for (int i = 0; i < totalChunks; i++) {
                    ArchivoChunkEncriptado chunk = documentoService.obtenerChunkEncriptado(id, i);
                    if (chunk != null) {
                        Map<String, Object> chunkJson = new LinkedHashMap<>();
                        chunkJson.put("status", "CHUNK");
                        chunkJson.put("numero", chunk.getNumeroChunk());
                        chunkJson.put("datos", Base64.getEncoder().encodeToString(chunk.getDatos()));
                        out.println(mapper.writeValueAsString(chunkJson));
                    }
                }

                out.println(mapper.writeValueAsString(Map.of("status", "FIN")));
            } catch (Exception e) {
                ConsolaUtil.error("Error en streaming chunks encriptados: " + e.getMessage());
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }
}
