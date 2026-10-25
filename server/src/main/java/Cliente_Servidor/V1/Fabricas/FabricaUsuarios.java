/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.Fabricas;

import Cliente_Servidor.V1.DTO.UsuarioDTO;
import Cliente_Servidor.V1.Modelos.Usuario;
import Cliente_Servidor.V1.Modelos.UsuarioNormal;
import org.springframework.stereotype.Service;

/**
 *
 * @author crism
 */
@Service
public class FabricaUsuarios {
    
    public Usuario crearUsuario(UsuarioDTO usuarioDTO){
        
        return switch (usuarioDTO.getTipoUsuario()) {
            case NORMAL -> new UsuarioNormal.Builder()
                    .nombre(usuarioDTO.getNombre())
                    .build();
        };
    }
    
}
