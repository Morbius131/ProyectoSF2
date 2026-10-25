package Cliente_Servidor.V1.Controladores.HTTP;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import Cliente_Servidor.V1.Controladores.UsuarioControlador;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioRestController {

    private final UsuarioControlador usuarioControlador;
    private final ObjectMapper mapper;

    public UsuarioRestController(UsuarioControlador usuarioControlador) {
        this.usuarioControlador = usuarioControlador;
        this.mapper = new ObjectMapper();
    }

    @GetMapping("/conectados")
    public ResponseEntity<Map<String, Object>> listarConectados() {
        try {
            String json = usuarioControlador.listarConectados();
            Map<String, Object> body = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(errorMap("Error listando conectados: " + e.getMessage()));
        }
    }

    private Map<String, Object> errorMap(String msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "ERROR");
        map.put("message", msg);
        return map;
    }
}
