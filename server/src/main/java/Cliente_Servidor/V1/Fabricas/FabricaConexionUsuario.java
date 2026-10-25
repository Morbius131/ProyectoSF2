/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.Fabricas;

import Cliente_Servidor.V1.DTO.ConexionUsuarioDTO;
import Cliente_Servidor.V1.Modelos.ConexionUsuario;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 *
 * @author crism
 */
@Service
public class FabricaConexionUsuario {
    
    public ConexionUsuario crearConexion(ConexionUsuarioDTO conexionUsuarioDTO){
        return new ConexionUsuario.Builder()
            .usuario(conexionUsuarioDTO.getUsuario())
            .direccionIp(conexionUsuarioDTO.getDireccionIp())
            .protocolo(conexionUsuarioDTO.getProtocolo())
            .fechaHoraConexion(LocalDateTime.now())
            .build();
    }
}
