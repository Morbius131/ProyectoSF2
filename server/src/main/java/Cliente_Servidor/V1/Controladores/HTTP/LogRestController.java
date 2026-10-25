package Cliente_Servidor.V1.Controladores.HTTP;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Cliente_Servidor.V1.DTO.LogDTO;
import Cliente_Servidor.V1.Servicios.LogService;

@RestController
@RequestMapping("/api/logs")
public class LogRestController {

    private final LogService logService;

    public LogRestController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listarLogs() {
        try {
            List<LogDTO> logs = logService.listarComoDTO();
            List<Map<String, Object>> items = logs.stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "OK");
            body.put("totalLogs", items.size());
            body.put("data", items);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "ERROR");
            err.put("message", "Error listando logs: " + e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    private Map<String, Object> toMap(LogDTO log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", log.getId());
        map.put("tipo", log.getTipo() != null ? log.getTipo().name() : "");
        map.put("mensaje", log.getMensaje());
        map.put("fechaHora", log.getFechaHora());
        map.put("usuario", log.getUsuario() != null ? log.getUsuario().getNombre() : "");
        return map;
    }
}
