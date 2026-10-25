/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Cliente_Servidor.V1.DTO;

import Cliente_Servidor.V1.Enums.TipoLog;
import Cliente_Servidor.V1.Modelos.Logs;
import Cliente_Servidor.V1.Modelos.Usuario;

/**
 *
 * @author crism
 */
public class LogDTO {
    private Long id;
    private Usuario usuario;
    private TipoLog tipo;
    private String mensaje;
    private String fechaHora;

    public LogDTO() {}

    public static LogDTO desde(Logs log) {
        LogDTO dto = new LogDTO();
        dto.id        = log.getId();
        dto.usuario   = log.getUsuario();
        dto.tipo      = log.getTipoLog();
        dto.mensaje   = log.getMensaje();
        dto.fechaHora = log.getFechaHora() != null ? log.getFechaHora().toString() : null;
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public TipoLog getTipo() { return tipo; }
    public void setTipo(TipoLog tipo) { this.tipo = tipo; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public String getFechaHora() { return fechaHora; }
    public void setFechaHora(String fechaHora) { this.fechaHora = fechaHora; }
}
