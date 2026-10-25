package ser.cliente.model;

/** id=-1 and ip="-" for external clients (from peer servers). */
public record ClientInfo(int id, String nombre, String ip, String servidor) {
    public ClientInfo(int id, String nombre, String ip) {
        this(id, nombre, ip, "local");
    }
}