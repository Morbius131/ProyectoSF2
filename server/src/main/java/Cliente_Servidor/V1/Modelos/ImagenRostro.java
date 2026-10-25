package Cliente_Servidor.V1.Modelos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "imagenes_rostro")
public class ImagenRostro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "extension", nullable = false)
    private String extension;

    @Column(name = "tamanio", nullable = false)
    private Long tamanio;

    @Column(name = "documento_id", nullable = false)
    private Long documentoId;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "emocion")
    private String emocion;

    public ImagenRostro() {}

    public ImagenRostro(String nombre, String extension, Long tamanio,
                        Long documentoId, LocalDateTime fechaHora) {
        this.nombre = nombre;
        this.extension = extension;
        this.tamanio = tamanio;
        this.documentoId = documentoId;
        this.fechaHora = fechaHora;
    }

    public ImagenRostro(String nombre, String extension, Long tamanio,
                        Long documentoId, LocalDateTime fechaHora, String emocion) {
        this.nombre = nombre;
        this.extension = extension;
        this.tamanio = tamanio;
        this.documentoId = documentoId;
        this.fechaHora = fechaHora;
        this.emocion = emocion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public Long getTamanio() { return tamanio; }
    public void setTamanio(Long tamanio) { this.tamanio = tamanio; }

    public Long getDocumentoId() { return documentoId; }
    public void setDocumentoId(Long documentoId) { this.documentoId = documentoId; }

    public LocalDateTime getFechaHora() { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public String getEmocion() { return emocion; }
    public void setEmocion(String emocion) { this.emocion = emocion; }
}
