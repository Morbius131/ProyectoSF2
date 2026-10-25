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
// Solo tiene lo propio de un archivo
@Entity
@Table(name = "archivos")
@DiscriminatorValue("ARCHIVO")
public class Archivo extends Documento {

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "extension")
    private String extension;

    @Column(name = "tamanio")
    private Long tamanio;

    @Override
    public String getNombreVisible() {
        return nombre + "." + extension;
    }

    @Override
    public TipoDocumento getTipo() {
        return TipoDocumento.ARCHIVO;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public Long getTamanio() {
        return tamanio;
    }

    public void setTamanio(Long tamanio) {
        this.tamanio = tamanio;
    }

}

