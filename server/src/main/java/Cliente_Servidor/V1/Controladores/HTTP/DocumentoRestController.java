package Cliente_Servidor.V1.Controladores.HTTP;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import Cliente_Servidor.V1.Controladores.DocumentoControlador;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoRestController {

    private final DocumentoControlador documentoControlador;
    private final ObjectMapper mapper;

    public DocumentoRestController(DocumentoControlador documentoControlador) {
        this.documentoControlador = documentoControlador;
        this.mapper = new ObjectMapper();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listarDocumentos() {
        try {
            String json = documentoControlador.listarDocumentos(null);
            Map<String, Object> body = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(errorMap("Error listando documentos: " + e.getMessage()));
        }
    }

    @GetMapping("/mensajes")
    public ResponseEntity<Map<String, Object>> listarMensajes() {
        try {
            String json = documentoControlador.listarMensajes(null);
            Map<String, Object> body = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(errorMap("Error listando mensajes: " + e.getMessage()));
        }
    }

    private Map<String, Object> errorMap(String msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "ERROR");
        map.put("message", msg);
        return map;
    }
}
