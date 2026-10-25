/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.DTO;

import Cliente_Servidor.V1.Enums.TipoProtocolo;
import Cliente_Servidor.V1.Modelos.Usuario;

/**
 *
 * @author crism
 */
public class ConexionUsuarioDTO {
    private Usuario usuario;
    private String direccionIp;
    private TipoProtocolo protocolo;

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getDireccionIp() {
        return direccionIp;
    }

    public void setDireccionIp(String direccionIp) {
        this.direccionIp = direccionIp;
    }

    public TipoProtocolo getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(TipoProtocolo protocolo) {
        this.protocolo = protocolo;
    }
  
}
