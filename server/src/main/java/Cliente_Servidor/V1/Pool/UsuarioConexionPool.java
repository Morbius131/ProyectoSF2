package Cliente_Servidor.V1.Pool;

import Cliente_Servidor.V1.DTO.ConexionUsuarioDTO;
import Cliente_Servidor.V1.Fabricas.FabricaConexionUsuario;
import Cliente_Servidor.V1.Modelos.ConexionUsuario;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Object Pool de conexiones de usuario.
 * Límite configurable (por defecto 5).
 */
@Component
public class UsuarioConexionPool {

    private final FabricaConexionUsuario fabricaConexionUsuario;
    private final Map<String, ConexionUsuario> conexionesActivas = new ConcurrentHashMap<>();
    private final int MAX_CONEXIONES = 10; // ajustable

    public UsuarioConexionPool(FabricaConexionUsuario fabricaConexionUsuario) {
        this.fabricaConexionUsuario = fabricaConexionUsuario;
    }

    public int getMaxConexiones() {
        return MAX_CONEXIONES;
    }

    public ConexionUsuario obtener(ConexionUsuarioDTO usuarioConexionDTO) {
        if (conexionesActivas.size() >= MAX_CONEXIONES) {
            throw new IllegalStateException("Servidor lleno: límite de " + MAX_CONEXIONES + " conexiones alcanzado.");
        }
        ConexionUsuario conexion = fabricaConexionUsuario.crearConexion(usuarioConexionDTO);
        conexionesActivas.put(usuarioConexionDTO.getUsuario().getNombre(), conexion);
        return conexion;
    }

    public ArrayList<ConexionUsuario> obtenerUsuariosConectados() {
        return new ArrayList<>(conexionesActivas.values());
    }

    public void liberar(String nombreUsuario) {
        conexionesActivas.remove(nombreUsuario);
    }

    public boolean estaConectado(String nombreUsuario) {
        return conexionesActivas.containsKey(nombreUsuario);
    }

    public int totalActivas() {
        return conexionesActivas.size();
    }
}
