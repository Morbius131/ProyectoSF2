package Cliente_Servidor.V1.Controladores.HTTP;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import Cliente_Servidor.V1.Controladores.EmotionController;
import Cliente_Servidor.V1.Servicios.DocumentoService;
import Cliente_Servidor.V1.Servicios.EmotionRecognitionService;
import Cliente_Servidor.V1.Servicios.ImagenRostroService;
import Cliente_Servidor.V1.Servicios.UsuarioService;

@RestController
@RequestMapping("/api/emociones")
public class EmotionRestController {

    private final EmotionRecognitionService emotionService;
    private final ImagenRostroService rostroService;
    private final DocumentoService documentoService;
    private final UsuarioService usuarioService;
    private final EmotionController emotionController;
    private final ObjectMapper mapper;

    public EmotionRestController(EmotionRecognitionService emotionService,
                                  ImagenRostroService rostroService,
                                  DocumentoService documentoService,
                                  UsuarioService usuarioService,
                                  EmotionController emotionController) {
        this.emotionService = emotionService;
        this.rostroService = rostroService;
        this.documentoService = documentoService;
        this.usuarioService = usuarioService;
        this.emotionController = emotionController;
        this.mapper = new ObjectMapper();
    }


    // ── GET /api/emociones/resumen ───────────────────────────────────────────

    @GetMapping("/resumen")
    public ResponseEntity<Map<String, Object>> resumen() {
        try {
            var imagenes = rostroService.listar();
            long total = imagenes.size();

            // Conteo por emoción
            Map<String, Long> porEmocion = new LinkedHashMap<>();
            for (var img : imagenes) {
                String emocion = img.getEmocion() != null ? img.getEmocion() : "SIN_IDENTIFICAR";
                porEmocion.merge(emocion, 1L, Long::sum);
            }

            // Emoción más frecuente
            String emocionMasFrecuente = porEmocion.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("N/A");

            // Últimas 5 imágenes
            var ultimas = imagenes.stream()
                    .limit(5)
                    .map(img -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", img.getId());
                        m.put("nombre", img.getNombre());
                        m.put("emocion", img.getEmocion());
                        m.put("fechaHora", img.getFechaHora() != null ? img.getFechaHora().toString() : "");
                        return m;
                    })
                    .toList();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalImagenes", total);
            data.put("porEmocion", porEmocion);
            data.put("emocionMasFrecuente", emocionMasFrecuente);
            data.put("ultimasImagenes", ultimas);

            return ResponseEntity.ok(okMap(data));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(errorMap("Error generando resumen: " + e.getMessage()));
        }
    }

    // ── GET /api/emociones/imagenes ──────────────────────────────────────────

    @GetMapping("/imagenes")
    public ResponseEntity<Map<String, Object>> listar() {
        try {
            String json = emotionController.listarImagenesRostro();
            Map<String, Object> body = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(errorMap("Error listando: " + e.getMessage()));
        }
    }

    // ── GET /api/emociones/imagenes/{id} ─────────────────────────────────────

    @GetMapping("/imagenes/{id}")
    public ResponseEntity<Map<String, Object>> obtenerPorId(@PathVariable Long id) {
        return rostroService.buscarPorId(id)
                .map(img -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("id", img.getId());
                    data.put("nombre", img.getNombre());
                    data.put("extension", img.getExtension());
                    data.put("tamanio", img.getTamanio());
                    data.put("documentoId", img.getDocumentoId());
                    data.put("fechaHora", img.getFechaHora() != null ? img.getFechaHora().toString() : "");
                    data.put("emocion", img.getEmocion());

                    try {
                        var chunks = documentoService.obtenerChunksOriginal(img.getDocumentoId());
                        if (!chunks.isEmpty()) {
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            for (var chunk : chunks) {
                                baos.write(chunk.getDatos());
                            }
                            data.put("datosBase64", Base64.getEncoder().encodeToString(baos.toByteArray()));
                        } else {
                            data.put("datosBase64", "");
                        }
                    } catch (Exception e) {
                        data.put("datosBase64", "");
                    }

                    return ResponseEntity.<Map<String, Object>>ok(okMap(data));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST /api/emociones/identificar/{id} ─────────────────────────────────

    @PostMapping("/identificar/{id}")
    public ResponseEntity<Map<String, Object>> identificarPorId(@PathVariable Long id) {
        try {
            String json = emotionController.identificarPorId(Map.of("id", id));
            Map<String, Object> body = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(errorMap("Error identificando: " + e.getMessage()));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> okMap(Object data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "OK");
        map.put("data", data);
        return map;
    }

    private Map<String, Object> errorMap(String msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "ERROR");
        map.put("message", msg);
        return map;
    }

    private Map<String, Double> toStringMap(java.util.Map<?, Double> source) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (java.util.Map.Entry<?, Double> entry : source.entrySet()) {
            map.put(entry.getKey().toString(), entry.getValue());
        }
        return map;
    }
}
