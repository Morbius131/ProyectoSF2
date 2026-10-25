package Cliente_Servidor.V1.Controladores;

import Cliente_Servidor.V1.DTO.ResponseDTO;
import Cliente_Servidor.V1.DTO.UsuarioDTO;
import Cliente_Servidor.V1.Enums.TipoUsuario;
import Cliente_Servidor.V1.Handles.HandleResult;
import Cliente_Servidor.V1.Modelos.ConexionUsuario;
import Cliente_Servidor.V1.Modelos.Usuario;
import Cliente_Servidor.V1.Pool.ServidorAmigoPool;
import Cliente_Servidor.V1.Pool.UsuarioConexionPool;
import Cliente_Servidor.V1.Servicios.UsuarioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class UsuarioControlador {

    private final UsuarioService usuarioService;
    private final UsuarioConexionPool pool;
    private final ServidorAmigoPool servidorPool;
    private final ObjectMapper mapper = new ObjectMapper();

    public UsuarioControlador(UsuarioService usuarioService,
                               UsuarioConexionPool pool,
                               ServidorAmigoPool servidorPool) {
        this.usuarioService = usuarioService;
        this.pool = pool;
        this.servidorPool = servidorPool;
    }

    public HandleResult crearObtenerUsuario(Map<String, Object> data) throws JsonProcessingException {
        String tipoUsuarioStr = (String) data.get("tipoUsuario");
        UsuarioDTO dto = new UsuarioDTO();
        dto.setNombre((String) data.get("nombre"));
        dto.setTipoUsuario(TipoUsuario.valueOf(tipoUsuarioStr));

        Usuario usuario = usuarioService.crearObtenerUsuario(dto);
        String json = mapper.writeValueAsString(ResponseDTO.ok(Map.of("id", usuario.getId(), "nombre", usuario.getNombre())));
        return new HandleResult(json, usuario);
    }

    public String listarConectados() throws JsonProcessingException {
        ArrayList<ConexionUsuario> conectados = pool.obtenerUsuariosConectados();
        List<Map<String, String>> resultado = new ArrayList<>();

        // Clientes locales
        for (ConexionUsuario cu : conectados) {
            Map<String, String> e = new LinkedHashMap<>();
            e.put("usuario",    cu.getUsuario().getNombre());
            e.put("ip",         cu.getDireccionIp());
            e.put("protocolo",  cu.getProtocolo().name());
            e.put("horaInicio", cu.getFechaHoraConexion() != null
                    ? cu.getFechaHoraConexion().toString() : "N/D");
            e.put("servidor",   "local");
            resultado.add(e);
        }

        // Clientes de servidores amigos conectados
        resultado.addAll(servidorPool.obtenerClientesExternos());

        return mapper.writeValueAsString(ResponseDTO.ok(resultado));
    }
}
