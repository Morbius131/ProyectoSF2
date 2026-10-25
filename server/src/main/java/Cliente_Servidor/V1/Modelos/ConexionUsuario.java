/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.Modelos;

import Cliente_Servidor.V1.Enums.TipoProtocolo;
import java.time.LocalDateTime;

/**
 *
 * @author crism
 */
public class ConexionUsuario {
    
    private Usuario usuario;
    
    private String direccionIp;
    
    private TipoProtocolo protocolo;
    
    private LocalDateTime fechaHoraConexion;
    
    
    private ConexionUsuario(){
        
    }
    
    public static class Builder{
        private Usuario usuario;
        private String direccionIp;
        private TipoProtocolo protocolo;
        private LocalDateTime fechaHoraConexion;
        
        public Builder usuario(Usuario usuario) {
            this.usuario = usuario;
            return this;
        }
        
        public Builder direccionIp(String direccionIp) {
            this.direccionIp = direccionIp;
            return this;
        }
        
        public Builder protocolo(TipoProtocolo protocolo) {
            this.protocolo = protocolo;
            return this;
        }
        
        public Builder fechaHoraConexion(LocalDateTime fechaHoraConexion) {
            this.fechaHoraConexion = fechaHoraConexion;
            return this;
        }
        
        public ConexionUsuario build() {
            if (usuario == null) throw new IllegalStateException("Usuario es requerido");
            if (direccionIp == null || direccionIp.isBlank()) throw new IllegalStateException("IP es requerida");
            if (protocolo == null) throw new IllegalStateException("Protocolo es requerido");
            
            ConexionUsuario conexion = new ConexionUsuario();
            conexion.usuario = this.usuario;
            conexion.direccionIp = this.direccionIp;
            conexion.protocolo = this.protocolo;
            conexion.fechaHoraConexion = this.fechaHoraConexion != null ? this.fechaHoraConexion : LocalDateTime.now();
            return conexion;
        }
    }
    

    public Usuario getUsuario(){
        return this.usuario;
    }
    
    public void setUsuario(Usuario usuario){
        this.usuario = usuario;
    }
    
    public String getDireccionIp(){
        return this.direccionIp;
    }
    
    public void setDireccionIp(String direccionIp){
        this.direccionIp = direccionIp;
    }

    public TipoProtocolo getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(TipoProtocolo protocolo) {
        this.protocolo = protocolo;
    }
    
    public LocalDateTime getFechaHoraConexion(){
        return this.fechaHoraConexion;
    }
    
    public void setFechaHoraConexion(LocalDateTime fechaHoraConexion){
        this.fechaHoraConexion = fechaHoraConexion;
    }
    
}
