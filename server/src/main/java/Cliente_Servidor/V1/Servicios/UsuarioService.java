/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.Servicios;

import Cliente_Servidor.V1.DTO.UsuarioDTO;
import Cliente_Servidor.V1.Fabricas.FabricaUsuarios;
import Cliente_Servidor.V1.Modelos.Usuario;
import Cliente_Servidor.V1.Repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 *
 * @author crism
 */
@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final FabricaUsuarios fabricaUsuarios;

    // Lock por nombre de usuario para evitar race conditions sin bloquear todo
    private final java.util.concurrent.ConcurrentHashMap<String, Object> locksPorNombre =
            new java.util.concurrent.ConcurrentHashMap<>();

    public UsuarioService(UsuarioRepository usuarioRepository, FabricaUsuarios fabricaUsuarios){
        this.usuarioRepository = usuarioRepository;
        this.fabricaUsuarios = fabricaUsuarios;
    }

    public Usuario crearObtenerUsuario(UsuarioDTO usuarioDTO){

        if (usuarioDTO == null) {
            throw new IllegalArgumentException("UsuarioDTO no puede ser null");
        }

        if (usuarioDTO.getNombre() == null || usuarioDTO.getNombre().isBlank()) {
            throw new IllegalArgumentException("Nombre inválido");
        }

        if (usuarioDTO.getTipoUsuario() == null) {
            throw new IllegalArgumentException("Tipo usuario inválido");
        }

        String nombre = usuarioDTO.getNombre();

        // Obtener o crear lock para este nombre específico
        Object lock = locksPorNombre.computeIfAbsent(nombre, k -> new Object());

        synchronized (lock) {
            try {
                // Buscar dentro del synchronized para evitar race condition
                Optional<Usuario> usuarioExistente = usuarioRepository.findByNombre(nombre);

                if (usuarioExistente.isPresent()) {
                    return usuarioExistente.get();
                }

                Usuario usuarioNuevo = fabricaUsuarios.crearUsuario(usuarioDTO);
                return this.usuarioRepository.save(usuarioNuevo);

            } finally {
                // Limpiar el lock después de un tiempo para evitar memory leak
                // (el lock permanece mientras otros hilos lo necesiten)
            }
        }
    }
}
