/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.Modelos;

import Cliente_Servidor.V1.Enums.TipoDocumento;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 *
 * @author crism
 */
// Solo tiene lo propio de un mensaje
@Entity
@Table(name = "mensajes")
@DiscriminatorValue("MENSAJE")
public class Mensaje extends Documento {

    @Column(name = "asunto")
    private String asunto;

    @Column(name = "contenido", columnDefinition = "TEXT")
    private String contenido;

    @Override
    public TipoDocumento getTipo() {
        return TipoDocumento.MENSAJE;
    }
    
    @Override
    public  String getNombreVisible(){
        return getUsuario().getNombre() + " - " + asunto;
    }
    
    
    public Mensaje(){
        
    }
    
  
     public String getAsunto() {
        return asunto;
    }

    public void setAsunto(String asunto) {
        this.asunto = asunto;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }
}
