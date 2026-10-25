/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package Cliente_Servidor.V1.Repository;

import Cliente_Servidor.V1.Modelos.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author crism
 */
public interface UsuarioRepository extends JpaRepository<Usuario,Long> {
    Optional<Usuario> findByNombre(String nombre);
}
