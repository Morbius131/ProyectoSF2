package Cliente_Servidor.V1.Controladores;

import Cliente_Servidor.V1.DTO.LogDTO;
import Cliente_Servidor.V1.Handles.HandleResult;
import Cliente_Servidor.V1.Servicios.LogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Devuelve los logs en protocolo multilinea (igual que los chunks de archivo)
 * para evitar el límite de 64 KB por datagrama UDP cuando hay muchos registros.
 *
 * Protocolo:
 *   Línea 1 : {"status":"OK","totalLogs":N}
 *   Líneas  : {"status":"LOG", "id":..., "tipo":..., "mensaje":..., "fechaHora":..., "usuario":...}  × N
 *   Última  : {"status":"FIN"}
 */
@Component
public class LogControlador {

    private final LogService   logService;
    private final ObjectMapper mapper = new ObjectMapper();

    public LogControlador(LogService logService) {
        this.logService = logService;
    }

    public HandleResult listarLogs() throws Exception {
        List<LogDTO> logs = logService.listarComoDTO();
        List<String> lineas = new ArrayList<>();

        // Cabecera
        Map<String, Object> cab = new LinkedHashMap<>();
        cab.put("status",    "OK");
        cab.put("totalLogs", logs.size());
        lineas.add(mapper.writeValueAsString(cab));

        // Un JSON por log
        for (LogDTO log : logs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("status",    "LOG");
            entry.put("id",        log.getId());
            entry.put("tipo",      log.getTipo() != null ? log.getTipo().name() : "");
            entry.put("mensaje",   log.getMensaje());
            entry.put("fechaHora", log.getFechaHora());
            entry.put("usuario",   log.getUsuario() != null ? log.getUsuario().getNombre() : "");
            lineas.add(mapper.writeValueAsString(entry));
        }

        // Marcador de fin
        lineas.add(mapper.writeValueAsString(Map.of("status", "FIN")));
        return new HandleResult(lineas, null);
    }
}
