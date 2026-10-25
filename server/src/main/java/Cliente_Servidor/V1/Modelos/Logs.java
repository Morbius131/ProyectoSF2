/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.Modelos;

import Cliente_Servidor.V1.Enums.TipoLog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 *
 * @author crism
 */
@Entity
@Table(name="logs")
public class Logs {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name="usuario_id")
    private Usuario usuario;
    
    @Enumerated(EnumType.STRING)
    @Column(name="Tipo")
    private TipoLog tipo;
    
    @Column(name="mensaje", columnDefinition="Text")
    private String mensaje;
    
    @Column(name="fecha_hora")
    private LocalDateTime fechaHora;
    
    public Logs(){
        
    }
    
    public Long getId(){
        return this.id;
    }
    
    public Usuario getUsuario(){
        return this.usuario;
    }
    
    public void setUsuario(Usuario usuario){
        this.usuario = usuario;
    }
    
    public TipoLog getTipoLog(){
        return this.tipo;
    }
    
    public void setTipoLog(TipoLog tipo){
        this.tipo = tipo;
    }
    
    public String getMensaje(){
        return this.mensaje;
    }
    
    public void setMensaje(String mensaje){
        this.mensaje = mensaje;
    }
    
    public LocalDateTime getFechaHora(){
        return this.fechaHora;
    }
    
    public void setFechaHora(LocalDateTime fechaHora){
        this.fechaHora = fechaHora;
    }
    
}
