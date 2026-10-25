package ser.cliente.model;

import java.util.List;

public record ServerInfo(
        String id,
        String nombre,
        String host,
        int puerto,
        boolean conectado,
        List<String> clientes
) {}
