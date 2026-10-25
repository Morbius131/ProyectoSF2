package Cliente_Servidor.V1.Controladores;

import Cliente_Servidor.V1.DTO.ConexionUsuarioDTO;
import Cliente_Servidor.V1.DTO.ResponseDTO;
import Cliente_Servidor.V1.Modelos.ConexionUsuario;
import Cliente_Servidor.V1.Pool.UsuarioConexionPool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConexionControlador {

    private final UsuarioConexionPool pool;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConexionControlador(UsuarioConexionPool pool) {
        this.pool = pool;
    }

    /** Retorna la lista de clientes conectados: IP, fecha, hora inicio */
    public String listarConectados() throws JsonProcessingException {
        ArrayList<ConexionUsuario> conectados = pool.obtenerUsuariosConectados();
        List<Map<String, String>> resultado = new ArrayList<>();

        for (ConexionUsuario cu : conectados) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("usuario", cu.getUsuario().getNombre());
            entry.put("ip", cu.getDireccionIp());
            entry.put("protocolo", cu.getProtocolo().name());
            entry.put("fechaHoraInicio", cu.getFechaHoraConexion() != null
                    ? cu.getFechaHoraConexion().toString() : "N/D");
            resultado.add(entry);
        }

        return mapper.writeValueAsString(ResponseDTO.ok(resultado));
    }

    public int totalConectados() {
        return pool.totalActivas();
    }
}
