package Cliente_Servidor.V1.DTO;

import Cliente_Servidor.V1.Enums.TipoPropietario;
import Cliente_Servidor.V1.Modelos.Archivo;
import Cliente_Servidor.V1.Modelos.Documento;
import Cliente_Servidor.V1.Modelos.Mensaje;
import Cliente_Servidor.V1.Modelos.Usuario;
import java.time.LocalDateTime;

/**
 * DTO de Documento. El contenido ya NO se incluye aquí;
 * se transfiere en chunks separados (GET_CHUNK / GET_CHUNK_ENC).
 */
public class DocumentoDTO {

    private Long   id;
    private String idUnico;
    private String tipo;
    private String hash;
    private String tipoPropietario;
    private String propietarioIp;
    private String fechaHora;
    private Long   usuarioId;
    private String usuarioNombre;
    private String nombre;
    private String extension;
    private Long   tamanio;
    private String asunto;
    private String nombreVisible;

    // Solo se usa en POST (subida): el cliente envía el contenido en base64
    private String contenido;

    /** null / "TODOS" = para todos; nombre de usuario = envío dirigido. */
    private String destinatario;

    public DocumentoDTO() {}

    public static DocumentoDTO desde(Documento doc) {
        DocumentoDTO dto = new DocumentoDTO();
        dto.id              = doc.getId();
        dto.idUnico         = doc.getIdUnico();
        dto.tipo            = doc.getTipo().name();
        dto.hash            = doc.getHash();
        dto.tipoPropietario = doc.getTipoPropietario() != null ? doc.getTipoPropietario().name() : null;
        dto.propietarioIp   = doc.getPropietarioIp();
        dto.fechaHora       = doc.getFechaHora() != null ? doc.getFechaHora().toString() : null;
        dto.nombreVisible   = doc.getNombreVisible();
        dto.destinatario    = doc.getDestinatario();
        if (doc.getUsuario() != null) {
            dto.usuarioId     = doc.getUsuario().getId();
            dto.usuarioNombre = doc.getUsuario().getNombre();
        }
        if (doc instanceof Archivo a) {
            dto.nombre    = a.getNombre();
            dto.extension = a.getExtension();
            dto.tamanio   = a.getTamanio();
        } else if (doc instanceof Mensaje m) {
            dto.asunto = m.getAsunto();
            dto.contenido = m.getContenido();
        }
        return dto;
    }

    public Documento toEntidad(Usuario usuario) {
        Documento doc;
        if ("ARCHIVO".equals(this.tipo)) {
            Archivo a = new Archivo();
            a.setNombre(this.nombre);
            a.setExtension(this.extension);
            a.setTamanio(this.tamanio);
            doc = a;
        } else {
            Mensaje m = new Mensaje();
            m.setAsunto(this.asunto);
            m.setContenido(this.contenido);
            doc = m;
        }
        if (this.tipoPropietario != null) {
            doc.setTipoPropietario(TipoPropietario.valueOf(this.tipoPropietario));
        } else {
            doc.setTipoPropietario(TipoPropietario.LOCAL);
        }
        doc.setPropietarioIp(this.propietarioIp);
        doc.setUsuario(usuario);
        doc.setFechaHora(LocalDateTime.now());
        doc.setDestinatario(this.destinatario);
        doc.setIdUnico(this.idUnico);
        return doc;
    }

    // Getters / Setters
    public Long getId()                         { return id; }
    public void setId(Long id)                  { this.id = id; }
    public String getIdUnico()                  { return idUnico; }
    public void setIdUnico(String idUnico)      { this.idUnico = idUnico; }
    public String getTipo()                     { return tipo; }
    public void setTipo(String tipo)            { this.tipo = tipo; }
    public String getHash()                     { return hash; }
    public void setHash(String hash)            { this.hash = hash; }
    public String getTipoPropietario()          { return tipoPropietario; }
    public void setTipoPropietario(String t)    { this.tipoPropietario = t; }
    public String getPropietarioIp()            { return propietarioIp; }
    public void setPropietarioIp(String ip)     { this.propietarioIp = ip; }
    public String getFechaHora()                { return fechaHora; }
    public void setFechaHora(String f)          { this.fechaHora = f; }
    public Long getUsuarioId()                  { return usuarioId; }
    public void setUsuarioId(Long id)           { this.usuarioId = id; }
    public String getUsuarioNombre()            { return usuarioNombre; }
    public void setUsuarioNombre(String n)      { this.usuarioNombre = n; }
    public String getNombre()                   { return nombre; }
    public void setNombre(String n)             { this.nombre = n; }
    public String getExtension()                { return extension; }
    public void setExtension(String e)          { this.extension = e; }
    public Long getTamanio()                    { return tamanio; }
    public void setTamanio(Long t)              { this.tamanio = t; }
    public String getAsunto()                   { return asunto; }
    public void setAsunto(String a)             { this.asunto = a; }
    public String getNombreVisible()            { return nombreVisible; }
    public void setNombreVisible(String n)      { this.nombreVisible = n; }
    public String getContenido()                { return contenido; }
    public void setContenido(String c)          { this.contenido = c; }
    public String getDestinatario()             { return destinatario; }
    public void setDestinatario(String d)       { this.destinatario = d; }
}
