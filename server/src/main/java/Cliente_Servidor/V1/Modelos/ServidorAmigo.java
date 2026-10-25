package Cliente_Servidor.V1.Modelos;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ServidorAmigo {

    private String id;          // "host:puerto"
    private String nombre;
    private String host;
    private int puerto;
    private boolean conectado;
    private LocalDateTime ultimaConexion;
    private List<String> clientesConectados;
    private int fallosConsecutivos; // Contador de fallos de heartbeat

    public ServidorAmigo() {
        this.clientesConectados = new ArrayList<>();
        this.fallosConsecutivos = 0;
    }

    public ServidorAmigo(String host, int puerto, String nombre, boolean conectado) {
        this.id = host + ":" + puerto;
        this.nombre = (nombre != null && !nombre.isBlank()) ? nombre : this.id;
        this.host = host;
        this.puerto = puerto;
        this.conectado = conectado;
        this.ultimaConexion = LocalDateTime.now();
        this.clientesConectados = new ArrayList<>();
        this.fallosConsecutivos = 0;
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

    public LocalDateTime getUltimaConexion() { return ultimaConexion; }
    public void setUltimaConexion(LocalDateTime t) { this.ultimaConexion = t; }

    public List<String> getClientesConectados() { return clientesConectados; }
    public void setClientesConectados(List<String> l) {
        this.clientesConectados = (l != null) ? l : new ArrayList<>();
    }

    public int getFallosConsecutivos() { return fallosConsecutivos; }
    public void incrementarFallos() { this.fallosConsecutivos++; }
    public void resetearFallos() { this.fallosConsecutivos = 0; }
}
