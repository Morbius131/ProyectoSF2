package Cliente_Servidor.V1.DTO;

import Cliente_Servidor.V1.Modelos.ServidorAmigo;
import java.util.ArrayList;
import java.util.List;

public class ServidorAmigoDTO {

    private String id;
    private String nombre;
    private String host;
    private int puerto;
    private boolean conectado;
    private String ultimaConexion;
    private List<String> clientesConectados;

    public ServidorAmigoDTO() {
        this.clientesConectados = new ArrayList<>();
    }

    public static ServidorAmigoDTO desde(ServidorAmigo s) {
        ServidorAmigoDTO dto = new ServidorAmigoDTO();
        dto.id = s.getId();
        dto.nombre = s.getNombre();
        dto.host = s.getHost();
        dto.puerto = s.getPuerto();
        dto.conectado = s.isConectado();
        dto.ultimaConexion = s.getUltimaConexion() != null
                ? s.getUltimaConexion().toString() : null;
        dto.clientesConectados = s.getClientesConectados() != null
                ? s.getClientesConectados() : new ArrayList<>();
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPuerto() { return puerto; }
    public void setPuerto(int puerto) { this.puerto = puerto; }
    public boolean isConectado() { return conectado; }
    public void setConectado(boolean conectado) { this.conectado = conectado; }
    public String getUltimaConexion() { return ultimaConexion; }
    public void setUltimaConexion(String u) { this.ultimaConexion = u; }
    public List<String> getClientesConectados() { return clientesConectados; }
    public void setClientesConectados(List<String> l) { this.clientesConectados = l; }
}
