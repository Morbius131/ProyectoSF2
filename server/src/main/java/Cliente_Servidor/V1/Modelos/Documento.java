package Cliente_Servidor.V1.Modelos;

import Cliente_Servidor.V1.Enums.TipoDocumento;
import Cliente_Servidor.V1.Enums.TipoPropietario;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documentos")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo", discriminatorType = DiscriminatorType.STRING)
public abstract class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID único global para sincronización (timestamp + servidor + nombre). */
    @Column(name = "id_unico", unique = true)
    private String idUnico;

    @Column(name = "hash")
    private String hash;

    @Enumerated(EnumType.STRING)
    @Column(name = "propietario_tipo")
    private TipoPropietario tipoPropietario;

    @Column(name = "propietario_ip")
    private String propietarioIp;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "fecha_hora")
    private LocalDateTime fechaHora;

    /** null o "TODOS" = para todos; nombre de usuario = envío dirigido. */
    @Column(name = "destinatario")
    private String destinatario;

    public abstract TipoDocumento getTipo();
    public abstract String getNombreVisible();

    public Documento() {}

    public Long getId()                              { return id; }
    public String getIdUnico()                       { return idUnico; }
    public void setIdUnico(String idUnico)           { this.idUnico = idUnico; }
    public String getHash()                          { return hash; }
    public void setHash(String hash)                 { this.hash = hash; }
    public TipoPropietario getTipoPropietario()      { return tipoPropietario; }
    public void setTipoPropietario(TipoPropietario t){ this.tipoPropietario = t; }
    public String getPropietarioIp()                 { return propietarioIp; }
    public void setPropietarioIp(String ip)          { this.propietarioIp = ip; }
    public Usuario getUsuario()                      { return usuario; }
    public void setUsuario(Usuario u)                { this.usuario = u; }
    public LocalDateTime getFechaHora()              { return fechaHora; }
    public void setFechaHora(LocalDateTime f)        { this.fechaHora = f; }
    public String getDestinatario()                  { return destinatario; }
    public void setDestinatario(String d)            { this.destinatario = d; }
}
