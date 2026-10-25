package Cliente_Servidor.V1.Controladores;

import Cliente_Servidor.V1.DTO.DocumentoDTO;
import Cliente_Servidor.V1.DTO.ResponseDTO;
import Cliente_Servidor.V1.DTO.UsuarioDTO;
import Cliente_Servidor.V1.Enums.TipoUsuario;
import Cliente_Servidor.V1.Modelos.ArchivoChunk;
import Cliente_Servidor.V1.Modelos.Documento;
import Cliente_Servidor.V1.Modelos.EmotionResult;
import Cliente_Servidor.V1.Modelos.ImagenRostro;
import Cliente_Servidor.V1.Modelos.Usuario;
import Cliente_Servidor.V1.Servicios.DocumentoService;
import Cliente_Servidor.V1.Servicios.EmotionRecognitionService;
import Cliente_Servidor.V1.Servicios.ImagenRostroService;
import Cliente_Servidor.V1.Servicios.ServidorDistribuidoService;
import Cliente_Servidor.V1.Servicios.UsuarioService;
import Cliente_Servidor.V1.Util.CryptoUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EmotionController {

    private final EmotionRecognitionService emotionService;
    private final ImagenRostroService rostroService;
    private final DocumentoService documentoService;
    private final UsuarioService usuarioService;
    private final ServidorDistribuidoService distribuidoService;
    private final ObjectMapper mapper;

    public EmotionController(EmotionRecognitionService emotionService,
                             ImagenRostroService rostroService,
                             DocumentoService documentoService,
                             UsuarioService usuarioService,
                             ServidorDistribuidoService distribuidoService) {
        this.emotionService = emotionService;
        this.rostroService = rostroService;
        this.documentoService = documentoService;
        this.usuarioService = usuarioService;
        this.distribuidoService = distribuidoService;
        this.mapper = new ObjectMapper();
    }

    // ── Servicio 1: Identificar emoción de imagen nueva ──────────────────────

    public String identificarEmocion(Map<String, Object> data) throws JsonProcessingException {
        String imagenBase64 = (String) data.get("imagenBase64");
        String usuarioNombre = (String) data.getOrDefault("usuarioNombre", "desconocido");
        String nombreArchivo = (String) data.getOrDefault("nombre", "rostro");
        String tipoPropietario = (String) data.getOrDefault("tipoPropietario", "LOCAL");
        String emocionRecibida = (String) data.get("emocion"); // Si viene de replicación
        String idUnicoRecibido = (String) data.get("idUnico"); // Si viene de replicación

        if (imagenBase64 == null || imagenBase64.isBlank()) {
            return mapper.writeValueAsString(ResponseDTO.error("imagenBase64 es requerido"));
        }

        byte[] imageBytes = Base64.getDecoder().decode(imagenBase64);

        // Solo verificar idUnico si es replicación externa (evita duplicados en sincronización)
        boolean esExterno = "EXTERNO".equalsIgnoreCase(tipoPropietario);
        Documento guardado = null;

        if (esExterno && idUnicoRecibido != null && !idUnicoRecibido.isBlank()) {
            var docExistente = documentoService.buscarPorIdUnico(idUnicoRecibido);
            if (docExistente.isPresent()) {
                guardado = docExistente.get();
                // Verificar si ya tiene ImagenRostro asociada
                if (rostroService.existePorDocumentoId(guardado.getId())) {
                    return mapper.writeValueAsString(ResponseDTO.ok(Map.of(
                            "mensaje", "Imagen ya existe",
                            "duplicado", true)));
                }
                // El documento existe pero no tiene ImagenRostro, continuar para crearla
            }
        }

        UsuarioDTO usuarioDTO = new UsuarioDTO();
        usuarioDTO.setNombre(usuarioNombre);
        usuarioDTO.setTipoUsuario(TipoUsuario.NORMAL);
        Usuario usuario = usuarioService.crearObtenerUsuario(usuarioDTO);

        // Si no encontramos documento existente, crear uno nuevo
        if (guardado == null) {
            DocumentoDTO docDto = new DocumentoDTO();
            docDto.setTipo("ARCHIVO");
            docDto.setNombre(nombreArchivo);
            docDto.setExtension("png");
            docDto.setTamanio((long) imageBytes.length);
            docDto.setContenido(imagenBase64);
            docDto.setTipoPropietario(tipoPropietario);
            docDto.setUsuarioNombre(usuarioNombre);
            docDto.setUsuarioId(usuario.getId());
            docDto.setIdUnico(idUnicoRecibido); // Preservar idUnico si viene de replicación

            guardado = documentoService.procesarYGuardar(docDto, usuario);

            // Si aún es null, significa que otro hilo lo insertó (race condition)
            if (guardado == null && idUnicoRecibido != null) {
                var docExistente = documentoService.buscarPorIdUnico(idUnicoRecibido);
                if (docExistente.isPresent()) {
                    guardado = docExistente.get();
                    // Verificar si ya tiene ImagenRostro
                    if (rostroService.existePorDocumentoId(guardado.getId())) {
                        return mapper.writeValueAsString(ResponseDTO.ok(Map.of(
                                "mensaje", "Imagen ya existe",
                                "duplicado", true)));
                    }
                } else {
                    return mapper.writeValueAsString(ResponseDTO.error(
                            "Error de concurrencia al guardar imagen"));
                }
            } else if (guardado == null) {
                return mapper.writeValueAsString(ResponseDTO.error(
                        "Error al guardar documento de imagen"));
            }
        }

        // Si viene de replicación, usar la emoción recibida; si no, calcularla
        String emocionNombre;
        EmotionResult resultado = null;
        if (emocionRecibida != null && !emocionRecibida.isBlank()) {
            emocionNombre = emocionRecibida;
        } else {
            resultado = emotionService.predecir(imageBytes);
            emocionNombre = resultado.getEmocionGanadora().name();
        }

        ImagenRostro imagenRostro = rostroService.guardar(
                nombreArchivo, "png", (long) imageBytes.length, guardado.getId(), emocionNombre);

        // Replicar a otros servidores solo si es LOCAL
        if ("LOCAL".equalsIgnoreCase(tipoPropietario)) {
            distribuidoService.replicarImagenRostro(imagenBase64, nombreArchivo, emocionNombre,
                    usuarioNombre, usuario.getId(), guardado.getIdUnico());
        }

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("imagenId", imagenRostro.getId());
        responseData.put("documentoId", guardado.getId());
        responseData.put("emocionGanadora", emocionNombre);
        if (resultado != null) {
            responseData.put("probabilidades", toStringMap(resultado.getProbabilidades()));
            responseData.put("emocionGanadoraDisplay", resultado.getEmocionGanadora().toDisplayName());
            responseData.put("tiempoInferenciaMs", resultado.getTiempoInferenciaMs());
        }

        return mapper.writeValueAsString(ResponseDTO.ok(responseData));
    }

    // ── Servicio 2: Listar imágenes de rostro ────────────────────────────────

    public String listarImagenesRostro() throws JsonProcessingException {
        List<ImagenRostro> imagenes = rostroService.listar();
        List<Map<String, Object>> lista = new ArrayList<>();

        for (ImagenRostro img : imagenes) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", img.getId());
            entry.put("nombre", img.getNombre());
            entry.put("extension", img.getExtension());
            entry.put("tamanio", img.getTamanio());
            entry.put("documentoId", img.getDocumentoId());
            entry.put("fechaHora", img.getFechaHora() != null ? img.getFechaHora().toString() : "");
            entry.put("emocion", img.getEmocion());

            // Obtener idUnico del documento asociado
            try {
                var doc = documentoService.buscarPorId(img.getDocumentoId());
                entry.put("idUnico", doc.getIdUnico());
            } catch (Exception e) {
                entry.put("idUnico", null);
            }

            try {
                List<ArchivoChunk> chunks = documentoService.obtenerChunksOriginal(img.getDocumentoId());
                if (!chunks.isEmpty()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    for (ArchivoChunk chunk : chunks) {
                        baos.write(chunk.getDatos());
                    }
                    entry.put("datosBase64", Base64.getEncoder().encodeToString(baos.toByteArray()));
                } else {
                    entry.put("datosBase64", "");
                }
            } catch (Exception e) {
                entry.put("datosBase64", "");
            }

            lista.add(entry);
        }

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("total", lista.size());
        responseData.put("imagenes", lista);

        return mapper.writeValueAsString(ResponseDTO.ok(responseData));
    }

    /** Lista imágenes de rostro para sincronización (sin datos Base64, solo metadatos). */
    public String listarImagenesRostroParaSync() throws JsonProcessingException {
        List<ImagenRostro> imagenes = rostroService.listar();
        List<Map<String, Object>> lista = new ArrayList<>();

        for (ImagenRostro img : imagenes) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", img.getId());
            entry.put("nombre", img.getNombre());
            entry.put("extension", img.getExtension());
            entry.put("tamanio", img.getTamanio());
            entry.put("documentoId", img.getDocumentoId());
            entry.put("fechaHora", img.getFechaHora() != null ? img.getFechaHora().toString() : "");
            entry.put("emocion", img.getEmocion());

            try {
                var doc = documentoService.buscarPorId(img.getDocumentoId());
                entry.put("idUnico", doc.getIdUnico());
            } catch (Exception e) {
                entry.put("idUnico", null);
            }

            lista.add(entry);
        }

        return mapper.writeValueAsString(ResponseDTO.ok(lista));
    }

    /** Obtiene una imagen de rostro con sus datos Base64 para sincronización. */
    public String obtenerImagenRostroConDatos(Map<String, Object> data) throws JsonProcessingException {
        Object idObj = data.get("id");
        if (idObj == null) {
            return mapper.writeValueAsString(ResponseDTO.error("id es requerido"));
        }
        Long id = Long.valueOf(idObj.toString());

        ImagenRostro img = rostroService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Imagen de rostro no encontrada: " + id));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", img.getId());
        entry.put("nombre", img.getNombre());
        entry.put("extension", img.getExtension());
        entry.put("tamanio", img.getTamanio());
        entry.put("documentoId", img.getDocumentoId());
        entry.put("fechaHora", img.getFechaHora() != null ? img.getFechaHora().toString() : "");
        entry.put("emocion", img.getEmocion());

        try {
            var doc = documentoService.buscarPorId(img.getDocumentoId());
            entry.put("idUnico", doc.getIdUnico());
        } catch (Exception e) {
            entry.put("idUnico", null);
        }

        try {
            List<ArchivoChunk> chunks = documentoService.obtenerChunksOriginal(img.getDocumentoId());
            if (!chunks.isEmpty()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (ArchivoChunk chunk : chunks) {
                    baos.write(chunk.getDatos());
                }
                entry.put("datosBase64", Base64.getEncoder().encodeToString(baos.toByteArray()));
            } else {
                entry.put("datosBase64", "");
            }
        } catch (Exception e) {
            entry.put("datosBase64", "");
        }

        return mapper.writeValueAsString(ResponseDTO.ok(entry));
    }

    // ── Servicio 3: Identificar emoción por ID ───────────────────────────────

    public String identificarPorId(Map<String, Object> data) throws JsonProcessingException {
        Object idObj = data.get("id");
        if (idObj == null) {
            return mapper.writeValueAsString(ResponseDTO.error("id es requerido"));
        }
        Long id = Long.valueOf(idObj.toString());

        ImagenRostro imagenRostro = rostroService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Imagen de rostro no encontrada: " + id));

        try {
            List<ArchivoChunk> chunks = documentoService.obtenerChunksOriginal(imagenRostro.getDocumentoId());
            if (chunks.isEmpty()) {
                return mapper.writeValueAsString(
                        ResponseDTO.error("No hay datos de imagen para el ID " + id));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (ArchivoChunk chunk : chunks) {
                baos.write(chunk.getDatos());
            }
            byte[] imageBytes = baos.toByteArray();

            EmotionResult resultado = emotionService.predecir(imageBytes);
            String emocionNombre = resultado.getEmocionGanadora().name();

            rostroService.actualizarEmocion(imagenRostro.getId(), emocionNombre);

            Map<String, Object> responseData = new LinkedHashMap<>();
            responseData.put("imagenId", imagenRostro.getId());
            responseData.put("nombre", imagenRostro.getNombre());
            responseData.put("probabilidades", toStringMap(resultado.getProbabilidades()));
            responseData.put("emocionGanadora", emocionNombre);
            responseData.put("emocionGanadoraDisplay", resultado.getEmocionGanadora().toDisplayName());
            responseData.put("tiempoInferenciaMs", resultado.getTiempoInferenciaMs());

            return mapper.writeValueAsString(ResponseDTO.ok(responseData));

        } catch (Exception e) {
            return mapper.writeValueAsString(
                    ResponseDTO.error("Error reconstruyendo imagen: " + e.getMessage()));
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Map<String, Double> toStringMap(Map<?, Double> source) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (Map.Entry<?, Double> entry : source.entrySet()) {
            map.put(entry.getKey().toString(), entry.getValue());
        }
        return map;
    }
}
